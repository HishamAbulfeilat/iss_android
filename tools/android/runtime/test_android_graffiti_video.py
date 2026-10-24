"""Prevent regressions in virtual-only spray control and Android screen sizing.

No emulator or Android device is needed for the source-level invariants.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
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
        self.assertIn("const float speed=(injected.mobile_controls?1.5f:0.85f)*tlggpu_sensor_aim_scale();", virtual)
        self.assertIn("spray_axis_x+=", virtual)
        self.assertIn("spray_axis_y+=", virtual)
        self.assertIn("spray_dt=fminf(dt,0.050f)", virtual)
        self.assertIn("spray_axis_x=spray_axis_y=0.f", virtual)
        self.assertIn("if (injected.buttons&BTN_CROSS) shake=1;", virtual)
        # Stick-neutral behavior and gamepad button masks in the physical
        # branch are not replaced with the virtual smoothing.
        self.assertNotIn("spray_axis_x", physical)

    @unittest.skipUnless(shutil.which('cc'), 'C compiler unavailable')
    def test_virtual_spray_uses_right_drag_with_classic_fallback(self):
        text=PAD.read_text()
        start=text.index('static int android_spray_stick_pair(')
        helper=text[start:text.index('\n}',start)+2]
        program='#include <stdlib.h>\n#include <assert.h>\n'+helper+'''
int main(void){
 int a[4]={128,128,128,128};assert(android_spray_stick_pair(a,0)==0);
 a[0]=220;assert(android_spray_stick_pair(a,0)==0);assert(android_spray_stick_pair(a,1)==2);
 a[2]=160;assert(android_spray_stick_pair(a,0)==2);
 a[2]=128;a[3]=90;assert(android_spray_stick_pair(a,0)==2);
 a[2]=a[3]=-1;assert(android_spray_stick_pair(a,0)==0);
 a[2]=130;a[3]=125;assert(android_spray_stick_pair(a,0)==0);assert(android_spray_stick_pair(a,1)==2);
}
'''
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'spray.c';binary=Path(tmp)/'spray';source.write_text(program)
            subprocess.run(['cc','-Wall','-Wextra','-Werror',str(source),'-o',str(binary)],check=True,capture_output=True,text=True)
            subprocess.run([str(binary)],check=True,capture_output=True,text=True)

    @unittest.skipUnless(shutil.which('cc'), 'C compiler unavailable')
    def test_mobile_spray_curve_preserves_small_motion_without_changing_classic(self):
        text=PAD.read_text()
        helpers=[]
        for name in ('static float aim_curve(', 'static float android_spray_curve('):
            start=text.index(name);helpers.append(text[start:text.index('\n}',start)+2])
        source='#include <assert.h>\n#include <math.h>\n'+'\n'.join(helpers)+r'''
int main(void){
 assert(android_spray_curve(0.02f,1)==0.f);
 assert(android_spray_curve(0.10f,1)>0.07f); // Classic's 15% zone swallowed this entirely.
 assert(android_spray_curve(0.10f,0)==0.f);
 for(int i=-200;i<=200;i++){
  float v=i/100.f;
  assert(android_spray_curve(v,1)>=-1.f&&android_spray_curve(v,1)<=1.f);
  assert(fabsf(android_spray_curve(v,1)+android_spray_curve(-v,1))<1e-6f);
  assert(android_spray_curve(v,0)==aim_curve(v));
 }
}
'''
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp);(path/'curve.c').write_text(source)
            subprocess.run(['cc','-Wall','-Wextra','-Werror',str(path/'curve.c'),'-lm','-o',str(path/'curve')],check=True,capture_output=True,text=True)
            subprocess.run([str(path/'curve')],check=True,capture_output=True,text=True)

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

    def test_failed_texture_fixes_are_disabled_after_device_feedback(self):
        activity = ACT.read_text()
        launcher = MAIN.read_text()
        # BP09 made rendering worse and BC Tiny had no measurable benefit.
        # Keep old preferences from re-enabling either experiment.
        for key in ('texture_1d_fix', 'bc_tiny'):
            self.assertNotIn(key, activity)
            self.assertNotIn(key, launcher)
        self.assertIn('e.put("TLG_BP09","0")', activity)
        self.assertIn('e.put("TLG_BC_TINY","0")', activity)


if __name__ == "__main__":
    unittest.main()
