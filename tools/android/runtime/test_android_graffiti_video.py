"""Prevent regressions in virtual-only spray control and Android screen sizing.

No emulator or Android device is needed for the source-level invariants.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
PAD = ROOT / "port/src/runtime_pad.c"
ACT = ROOT / "android/app/src/ro/cobrabm/fexdroid/GameActivity.java"
MAIN = ROOT / "android/app/src/ro/cobrabm/fexdroid/MainActivity.java"


class GraffitiAndDisplayTests(unittest.TestCase):
    def test_only_android_virtual_sensor_rate_is_modified(self):
        text = PAD.read_text()
        physical = text.split("    if (g) {", 1)[1].split("    /* Android controller/overlay events", 1)[0]
        virtual = text.split("    if (android_pad_enabled()) {", 1)[1].split("    if (real && !simulated) {", 1)[0]
        self.assertIn("const float speed=1.2f*tlggpu_sensor_aim_scale();", physical)
        self.assertIn("const float speed=0.48f*tlggpu_sensor_aim_scale();", virtual)
        self.assertIn("spray_axis_x+=", virtual)
        self.assertIn("spray_axis_y+=", virtual)
        self.assertIn("spray_dt=fminf(dt,0.050f)", virtual)
        self.assertIn("spray_axis_x=spray_axis_y=0.f", virtual)
        self.assertIn("if (injected.buttons&BTN_CROSS) shake=1;", virtual)
        # Stick-neutral behavior and gamepad button masks in the physical
        # branch are not replaced with the virtual smoothing.
        self.assertNotIn("spray_axis_x", physical)

    def test_16_9_and_fullscreen_never_claim_impossible_geometry(self):
        activity = ACT.read_text()
        launcher = MAIN.read_text()
        self.assertIn('screenMode="stretch"', activity)  # migrate old fill
        self.assertIn('setSystemUiVisibility(5894)', activity)
        self.assertIn('"crop".equals(screenMode)?Math.max', activity)
        self.assertIn('"stretch".equals(screenMode)?w:', activity)
        self.assertIn('"stretch".equals(screenMode)?h:', activity)
        self.assertIn('"fit"', launcher)
        self.assertIn('"crop"', launcher)
        self.assertIn('"stretch"', launcher)

    def test_explicit_texture_fixes_are_independently_switchable(self):
        activity = ACT.read_text()
        launcher = MAIN.read_text()
        for name in ('texture_1d_fix', 'bc_tiny'):
            self.assertIn(name, activity)
            self.assertIn(name, launcher)
        self.assertIn('e.put("TLG_BP09"', activity)
        self.assertIn('e.put("TLG_BC_TINY"', activity)
        self.assertIn('prefs.getBoolean("texture_1d_fix",true)', activity)
        self.assertIn('prefs.getBoolean("bc_tiny",false)', activity)


if __name__ == "__main__":
    unittest.main()
