"""Bionic inFAMOUS: opt-in Vulkan FSR 3.1, no FSR4/DLSS, native AHB output.

Host-side invariants; Vulkan dispatch correctness requires Android device logs.
"""
from pathlib import Path
import os
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]
GPU = ROOT / "port/gpu"
CM = (GPU / "CMakeLists.txt").read_text()
HDR = (GPU / "shadps4/video_core/renderer_vulkan/vk_temporal_upscaler.h").read_text()
PRESENTER = (GPU / "shadps4/video_core/renderer_vulkan/vk_presenter.cpp").read_text()
RUN = (ROOT / "tools/android/bionic/run-bionic.sh").read_text()
PROBE = (ROOT / "port/src/probe.c").read_text()


class BionicFsr3Tests(unittest.TestCase):
    def test_native_vulkan_fsr3_is_linked_without_fsr4(self):
        self.assertIn("add_subdirectory(third_party/fsr-vulkan EXCLUDE_FROM_ALL)", CM)
        self.assertIn("FFX_VK_PORTABLE_BUILD_FSR4_V07_VULKAN OFF", CM)
        self.assertIn("target_link_libraries(tlggpu PRIVATE ffx-vulkan::portable)", CM)
        self.assertIn("vk_temporal_upscaler.cpp", (GPU / "shadps4/video_core/renderer_vulkan/vk_temporal_upscaler.cpp").as_posix())
        self.assertNotIn("vk_temporal_upscaler_bionic.h", HDR)
        self.assertIn("FfxVkPortableUpscaleContext* context", HDR)
        self.assertIn("vk_fsr4", CM)
        self.assertIn("vk_fsr4_android.cpp", str(next(GPU.glob("shadps4/video_core/renderer_vulkan/vk_fsr4_android.cpp"))))
        self.assertIn("FFX_VK_PORTABLE_OK", (GPU / "shadps4/video_core/renderer_vulkan/vk_temporal_upscaler.cpp").read_text())

    def test_bionic_keeps_ahardwarebuffer_and_fsr1_disabled(self):
        self.assertIn("#ifndef ISS_ANDROID_BIONIC", PRESENTER)
        self.assertIn("pp_pass.Render", PRESENTER)
        self.assertIn("vk_swapchain_ahb", (GPU / "CMakeLists.txt").read_text() + (GPU / "shadps4/video_core/renderer_vulkan/vk_swapchain_ahb.cpp").read_text())
        self.assertIn("TLG_FSR1=0 TLG_RCAS=0", RUN)

    def test_launcher_preserves_fsr3_request_and_default_none(self):
        self.assertIn('case "${TLG_UPSCALER:-none}" in', RUN)
        section = RUN.split('case "${TLG_UPSCALER:-none}" in', 1)[1].split('export TLG_FSR1=0', 1)[0]
        script = 'case "${TLG_UPSCALER:-none}" in' + section
        script += """\nprintf "%s %s %s %s" "$TLG_UPSCALER" "$ISS_UPSCALER" "$TLG_JITTER" "$TLG_OBJECT_MOTION"\n"""
        for selection, expected in (
            ('fsr3', 'fsr3 fsr3 1 1'), ('none', 'none off 0 0'),
            ('fsr4', 'none off 0 0')
        ):
            with self.subTest(selection=selection):
                env = dict(os.environ, TLG_UPSCALER=selection)
                result = subprocess.run(["sh", "-c", script], env=env, capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout.strip(), expected)

    def test_android_ui_opt_in_and_internal_output_scaling(self):
        src = ROOT / "android/app/src/ro/cobrabm/fexdroid"
        game = (src / "BionicGameActivity.java").read_text()
        video = (src / "BionicVideoSettings.java").read_text()
        self.assertIn('prefs.getBoolean("fsr3",false)', video)
        self.assertIn('if(prefs.getBoolean("fsr3",false))return "1280x720";', video)
        self.assertIn('"performance".equals(fsrMode(prefs))?"360p":"540p"', video)
        self.assertIn('"performance".equals(fsrMode(prefs))?"640x360":"960x540"', video)
        self.assertIn('prefs.getBoolean("fsr3",false)?fsrInternalResolution(prefs)', video)
        self.assertIn('resolutionPatch.setEnabled(!active)', video)
        self.assertIn('if(!prefs.getBoolean("fsr3",false)&&pos>=0', video)
        self.assertIn('e.put("TLG_UPSCALER",fsr3?"fsr3":"none")', game)
        self.assertIn('e.put("TLG_RENDER_RES",BionicVideoSettings.fsrInputSize(prefs))', game)
        self.assertIn('e.put("TLG_UPSCALE_PRESET",Integer.toString(BionicVideoSettings.fsrEnginePreset(prefs)))', game)
        self.assertIn('BionicVideoSettings.effectiveResolutionPatch(prefs)', game)
        self.assertIn('BionicVideoSettings.fsrSharpnessValue(prefs)', game)
        self.assertIn('BionicVideoSettings.fsrSharpenEnabled(prefs)', game)
        self.assertIn('e.put("TLG_FSR_SHARPEN"', game)
        self.assertIn('fsr3_sharpness_pct', video)
        self.assertIn('fsr3_sharpen_enabled', video)
        self.assertIn('sharpenSlider.setMax(200)', video)
        self.assertIn('sharpenSlider.setEnabled(active&&sharpening.isChecked())', video)
        self.assertIn('if(resolutionPatch!=null)patches.add(resolutionPatch)', game)
        self.assertNotIn('prefs.getString("internal_res","native");\\n   String internal=', game)
        # Both internal game patches must exist so there is exactly one automatic
        # executable resolution patch when FSR3 is enabled.
        patches = (ROOT / "port/patches/InfamousSecondSon.xml").read_text()
        for size in ("640x360", "960x540"):
            self.assertIn('Name="Render resolution '+size+'"', patches)
        self.assertIn('e.put("ISS_SCREEN_W",dimensions[0])', game)
        self.assertIn('e.put("ISS_SCREEN_H",dimensions[1])', game)

    def test_fsr_visual_diagnostics_have_no_readback_or_per_draw_printing(self):
        renderer = (GPU / "shadps4/video_core/renderer_vulkan/vk_temporal_upscaler.cpp").read_text()
        upscaler_h = (GPU / "shadps4/video_core/renderer_vulkan/vk_temporal_upscaler.h").read_text()
        native = (GPU / "shim/tlg_settings.cpp").read_text()
        self.assertIn('{"TLG_FSR_SHARPEN", "sharpen"}', native)
        self.assertIn('FSR3 ISS visual: source=%ux%u', renderer)
        for key in ('rcas_dispatches', 'extra_sharpen_dispatches',
                    'ui_draws_after_fsr', 'redirected_color',
                    'redirected_samples', 'ui_only_copies'):
            self.assertIn(key, upscaler_h)
        self.assertIn('if (Active() && ++android_fsr_probe.frames >= 180)', renderer)

    def test_pc_zero_is_diagnosable(self):
        self.assertIn("arm->uc_mcontext.regs[30]", PROBE)
        self.assertIn("native caller LR:", PROBE)
        self.assertIn("if (!functions_loaded)", (GPU / "shim/tlg_overlay.cpp").read_text())


if __name__ == "__main__":
    unittest.main()
