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

    def test_explicit_toggle_and_precise_override(self):
        src = (ROOT / "android/app/src/ro/cobrabm/fexdroid/BionicRendererSettings.java").read_text()
        self.assertIn('renderer_experimental_hybrid_readback', src)
        self.assertIn('(!precise && p.getBoolean("renderer_experimental_hybrid_readback",false))', src)
        self.assertIn('env.put("TLG_READBACKS", precise ? "2" : "1")', src)


if __name__ == "__main__":
    unittest.main()
