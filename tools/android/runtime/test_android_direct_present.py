"""Video tearing regression: don't re-enable unsynchronized Xvfb polling.

The fexdroid display bridge polls the Xvfb SHM framebuffer without a per-frame
lock. Our Linux KGSL Turnip must use Mesa's coherent 3-slot fxpresent path,
even if Xvfb also advertises MIT-SHM; the native bridge exposes directFrames().
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
PATCH = ROOT / "tools/android/mesa-patches/0008-wsi-x11-sw-present-direct-even-with-mit-shm.patch"
RUN = ROOT / "tools/android/device/run-iss.sh"
GAME = ROOT / "android/app/src/ro/cobrabm/fexdroid/GameActivity.java"
MAIN = ROOT / "android/app/src/ro/cobrabm/fexdroid/MainActivity.java"
OLD = ROOT / "tools/android/mesa-patches/0007-wsi-x11-sw-frames-straight-to-the-app.patch"


class DirectPresentTests(unittest.TestCase):
    def test_software_wsi_chooses_direct_path_even_if_shm_exists(self):
        text = PATCH.read_text()
        self.assertIn("+   if (chain->base.wsi->sw && (!chain->has_mit_shm || chain->fxpresent))", text)
        self.assertIn("+   if (chain->base.wsi->sw && os_get_option(\"FEXDROID_PRESENT\")) {", text)
        self.assertIn("fxpresent_frame(chain, image)", OLD.read_text())
        self.assertIn("export MESA_VK_WSI_DEBUG=sw", RUN.read_text())

    def test_direct_present_reported_to_avoid_silent_fallback(self):
        game = GAME.read_text()
        self.assertIn("DisplayBridge.directFrames()", game)
        self.assertIn("DIRECT_PRESENT_ACTIVE", game)
        self.assertIn("Xvfb polling fallback can cause horizontal tearing", game)

    def test_rejected_texture_experiments_are_not_exposed_or_enabled(self):
        self.assertNotIn("Alias 1D/2D (BP09", MAIN.read_text())
        self.assertNotIn("Decodificar BC1", MAIN.read_text())
        game = GAME.read_text()
        self.assertIn('e.put("TLG_BP09","0")', game)
        self.assertIn('e.put("TLG_BC_TINY","0")', game)

    def test_working_graffiti_and_screen_modes_unchanged(self):
        game = GAME.read_text()
        self.assertIn('"crop".equals(screenMode)', game)
        self.assertIn('"stretch".equals(screenMode)', game)
        self.assertIn("ISS_PATCHES", game)
        self.assertIn("e.put(\"TLG_SHOW_FPS\"", game)


if __name__ == "__main__":
    unittest.main()
