"""Source-level guard for the experimental inFAMOUS Relaxed/Precise hybrid."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]


class HybridReadbackTest(unittest.TestCase):
    def test_hybrid_only_enables_precise_read_protection_on_relaxed(self):
        src = (ROOT / "port/gpu/shadps4/video_core/buffer_cache/region_manager.h").read_text()
        self.assertIn('TLG_ISS_HYBRID_READ_PROTECT', src)
        self.assertIn('enabled && EmulatorSettings.GetReadbacksMode() == GpuReadbacksMode::Relaxed', src)
        self.assertIn('GpuReadbacksMode::Precise ||\n                HybridReadProtectionEnabled()', src)
        self.assertIn('UpdateProtection<enable, true>();', src)

    def test_readback_modes_are_accessible_in_one_dialog(self):
        src = (ROOT / "android/app/src/ro/cobrabm/fexdroid/BionicReadbackSettings.java").read_text()
        self.assertIn("RadioGroup choices = new RadioGroup(activity);", src)
        self.assertIn("choices.check(ids[mode(preferences)]);", src)
        self.assertIn("putBoolean(KEY,choice==2)", src)
        self.assertIn("putBoolean(HYBRID_KEY,choice==1)", src)
        main = (ROOT / "android/app/src/ro/cobrabm/fexdroid/BionicMainActivity.java").read_text()
        player = (ROOT / "android/app/src/ro/cobrabm/fexdroid/BionicPlayerMenu.java").read_text()
        self.assertIn("BionicReadbackSettings.show(", main)
        self.assertIn("BionicReadbackSettings.show(", player)

    def test_hybrid_v3_staged_and_eager_prefetch_independent(self):
        settings = (ROOT / "android/app/src/ro/cobrabm/fexdroid/BionicRendererSettings.java").read_text()
        dialog = (ROOT / "android/app/src/ro/cobrabm/fexdroid/BionicReadbackSettings.java").read_text()
        cache = (ROOT / "port/gpu/shadps4/video_core/buffer_cache/buffer_cache.cpp").read_text()
        self.assertIn('env.put("TLG_MAPPED_READBACK",hybrid ? "0" : "1");',settings)
        self.assertIn('final boolean eager = hybrid && p.getBoolean("renderer_hybrid_eager_prefetch",false);',settings)
        self.assertIn('env.put("TLG_EAGER_READBACK",eager ? "1" : "0");',settings)
        self.assertIn('static final String HYBRID_EAGER_KEY = "renderer_hybrid_eager_prefetch";',dialog)
        self.assertIn('eager.setVisibility(selectedId == ids[1] ? View.VISIBLE : View.GONE)',dialog)
        self.assertIn('.putBoolean(HYBRID_EAGER_KEY,eager.isChecked()).apply();',dialog)
        self.assertIn('RegionManager::HybridReadProtectionEnabled());',cache)

    def test_explicit_toggle_and_precise_override(self):
        src = (ROOT / "android/app/src/ro/cobrabm/fexdroid/BionicRendererSettings.java").read_text()
        self.assertIn('renderer_experimental_hybrid_readback', src)
        self.assertIn('final boolean hybrid = !precise && p.getBoolean("renderer_experimental_hybrid_readback",false);', src)
        self.assertIn('env.put("TLG_ISS_HYBRID_READ_PROTECT",hybrid ? "1" : "0");', src)
        self.assertIn('env.put("TLG_READBACKS", precise ? "2" : "1")', src)


if __name__ == "__main__":
    unittest.main()
