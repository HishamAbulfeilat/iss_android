"""The Bionic FPS HUD must measure real presenter frames without ImGui.

ImGui_ImplVulkan_LoadFunctions fails on some direct Turnip Vulkan HAL builds,
but the gameplay bridge already exposes a real frame counter. Ensure the FPS
HUD lives in the Android view hierarchy and is not a fake refresh-rate label.
"""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[3]
APP = ROOT / "android/app/src/ro/cobrabm/fexdroid/BionicGameActivity.java"
PRESENTER = ROOT / "tools/android/bionic/present/isspresent.c"
class NativeBionicFpsHudTests(unittest.TestCase):
    def test_fps_independent_of_native_imgui(self):
        src=APP.read_text()
        self.assertIn("fpsHud=new TextView(this)",src)
        self.assertIn("frame.addView(fpsHud,fpsParams)",src)
        self.assertIn("Gravity.TOP|Gravity.RIGHT",src)
        self.assertIn("showFps=getSharedPreferences",src)
        self.assertIn('e.put("TLG_SHOW_FPS",showFps?"1":"0")',src)
    def test_fps_uses_actual_native_presenter_delta(self):
        src=APP.read_text()
        self.assertIn("long n=IssPresent.frames()",src)
        self.assertIn("SystemClock.elapsedRealtimeNanos()",src)
        self.assertIn("(n-fpsSampleFrames)*1000000000.0/(now-fpsSampleNs)",src)
        self.assertIn("fpsSampleFrames=-1",src)
        self.assertIn("fpsHud.setVisibility(View.GONE)",src)
        native=PRESENTER.read_text()
        self.assertIn("atomic_fetch_add(&S.frames, 1)",native)
        self.assertIn("atomic_load(&S.frames)",native)
if __name__=="__main__":
    unittest.main()
