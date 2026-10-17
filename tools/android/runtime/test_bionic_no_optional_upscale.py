"""Bionic inFAMOUS: optional FSR/DLSS/TAA excluded, native rendering retained."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[3]
GPU = ROOT / "port/gpu"
CM = (GPU / "CMakeLists.txt").read_text()
HDR = (GPU / "shadps4/video_core/renderer_vulkan/vk_temporal_upscaler.h").read_text()
STUB = (GPU / "shadps4/video_core/renderer_vulkan/vk_temporal_upscaler_bionic.h").read_text()
PRESENTER = (GPU / "shadps4/video_core/renderer_vulkan/vk_presenter.cpp").read_text()
RUN = (ROOT / "tools/android/bionic/run-bionic.sh").read_text()
PROBE = (ROOT / "port/src/probe.c").read_text()
class NoExtraUpscalers(unittest.TestCase):
    def test_bionic_does_not_link_ffx(self):
        self.assertIn("if(NOT (ANDROID AND ISS_ANDROID_BIONIC))", CM)
        self.assertIn("vk_temporal_upscaler", CM)
        self.assertIn("vk_fsr4", CM)
        self.assertIn("fsr411/fsr411", CM)
        self.assertIn("vk_temporal_upscaler_bionic.h", HDR)
    def test_native_renderer_no_optional_upscale(self):
        self.assertIn("bool Enabled() const noexcept { return false; }", STUB)
        for name in ("RedirectColor", "RedirectDepth", "RedirectSampled",
                     "DisplayOverride", "SceneMipBias", "OnFrameStart", "Jitter"):
            self.assertIn(name, STUB)
        self.assertIn("#ifndef ISS_ANDROID_BIONIC", PRESENTER)
        self.assertIn("pp_pass.Render", PRESENTER)
        for name in ("TLG_UPSCALER=none", "TLG_FSR1=0", "TLG_RCAS=0"):
            self.assertIn(name, RUN)
    def test_pc_zero_is_diagnosable(self):
        self.assertIn("arm->uc_mcontext.regs[30]", PROBE)
        self.assertIn("native caller LR:", PROBE)
        self.assertIn("if (!functions_loaded)", (GPU / "shim/tlg_overlay.cpp").read_text())
if __name__ == "__main__":
    unittest.main()
