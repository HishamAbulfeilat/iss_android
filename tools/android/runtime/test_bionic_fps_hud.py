"""Bionic HUD uses Android views and measured native presentation rates."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[3]
APP = ROOT / "android/app/src/ro/cobrabm/fexdroid"
class NativeBionicFpsHudTests(unittest.TestCase):
    def test_fps_independent_of_native_imgui(self):
        activity=(APP / "BionicGameActivity.java").read_text()
        menu=(APP / "BionicPlayerMenu.java").read_text()
        self.assertIn('e.put("TLG_SHOW_FPS","0")',activity)
        self.assertIn('new BionicPlayerMenu(this,frame,controller,pad',activity)
        self.assertIn('new PerfHudOverlay(a)',menu)
        self.assertIn('hud.setActive(false)',menu)
        self.assertIn('hud.release()',menu)
    def test_fps_uses_actual_native_presenter_delta(self):
        overlay=(APP / "PerfHudOverlay.java").read_text()
        self.assertIn('IssPresent.framesIfLoaded()',overlay)
        self.assertIn('System.nanoTime()',overlay)
        self.assertIn('PerfHudMetrics.diffFrames(lastFrames, now)*1e9/(time-lastFrameNs)',overlay)
        self.assertIn('lastFrameNs = 0',overlay)
        self.assertIn('presented_fps',overlay)
        native=(ROOT / "tools/android/bionic/present/isspresent.c").read_text()
        self.assertIn('atomic_fetch_add(&S.frames, 1)',native)
        self.assertIn('atomic_load(&S.frames)',native)
if __name__ == "__main__": unittest.main()
