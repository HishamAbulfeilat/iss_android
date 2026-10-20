"""Exercise the real incremental D-pad helper and native frame-generation state parser."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[3]
class PlayerFeatures(unittest.TestCase):
    @unittest.skipUnless(shutil.which('cc'), 'C compiler unavailable')
    def test_dpad_increment_release_and_poll_rate(self):
        source=r'''
#include "dpad_swipe.h"
#include <assert.h>
#include <math.h>
int main(void){
 IssDpadSwipe a={0}, b={0};
 assert(!iss_dpad_swipe_step(&a,0,100));
 assert(iss_dpad_swipe_step(&a,8,100)); unsigned id=a.id;
 assert(a.x==959.5f && a.y==471.f);
 iss_dpad_swipe_step(&a,8,10100); assert(fabsf(a.x-965.5f)<.01f && a.id==id);
 iss_dpad_swipe_step(&a,1,20100); assert(fabsf(a.y-468.f)<.01f);
 assert(!iss_dpad_swipe_step(&a,0,30100) && !a.active);
 iss_dpad_swipe_step(&a,4,40100); assert(a.id!=id && a.x==965.5f);
 iss_dpad_swipe_step(&a,4,50100); assert(a.x<965.5f);
 assert(!iss_dpad_swipe_step(&a,15,60100));
 a=(IssDpadSwipe){0}; iss_dpad_swipe_step(&a,10,1000); iss_dpad_swipe_step(&b,10,1000);
 for(unsigned t=11000;t<=101000;t+=10000)iss_dpad_swipe_step(&a,10,t);
 for(unsigned t=21000;t<=101000;t+=20000)iss_dpad_swipe_step(&b,10,t);
 assert(fabsf(a.x-b.x)<.01f && fabsf(a.y-b.y)<.01f);
 iss_dpad_swipe_step(&a,10,1); assert(a.x==b.x); // backward clock, no teleport
 int releases=0;unsigned previous=a.id;
 for(unsigned t=201000;t<8000000;t+=10000){
  int down=iss_dpad_swipe_step(&a,8,t);if(!down)++releases;
 }
 assert(releases>0 && a.id!=previous); // held directions start new strokes at the edge
 assert(a.x>=0 && a.x<=1919 && a.y>=0 && a.y<=942);
 return 0;
}'''
        self.compile_run(source, ['cc','-std=c11','-I'+str(ROOT/'port/src')])
    def test_two_finger_quick_tap_has_a_click_and_held_fingers(self):
        runtime=(ROOT/'port/src/runtime_pad.c').read_text()
        start=runtime.index('static struct { int active; uint64_t pressed, released; } two=')
        end=runtime.index('/* Opens the first gamepad',start)
        actual=runtime[start:end]
        source=r'''#include <stdint.h>
#include <assert.h>
enum {CLICK_LEFT=1u<<28,CLICK_RIGHT=1u<<29,BTN_R1=2,BTN_TOUCHPAD=4};
enum {CLICK_LEAD_US=50000,CLICK_HOLD_US=50000,CLICK_TAIL_US=100000};
typedef struct {uint16_t x,y;uint8_t id;} PadTouch;
typedef struct {uint64_t timestamp;uint32_t buttons;unsigned touch_count;PadTouch touches[2];} PadData;
static struct {int side;} click;
''' + actual + r'''
int main(){
 PadData d={.timestamp=1000,.buttons=CLICK_LEFT|CLICK_RIGHT};
 assert(play_two_finger(&d)&&d.touch_count==2&&!(d.buttons&BTN_TOUCHPAD));
 d=(PadData){.timestamp=21000};assert(play_two_finger(&d)&&d.touch_count==2);
 d=(PadData){.timestamp=51000};assert(play_two_finger(&d)&&(d.buttons&BTN_TOUCHPAD));
 d=(PadData){.timestamp=100999};assert(play_two_finger(&d)&&(d.buttons&BTN_TOUCHPAD));
 d=(PadData){.timestamp=101000};assert(play_two_finger(&d)&&!(d.buttons&BTN_TOUCHPAD));
 d=(PadData){.timestamp=201000};assert(!play_two_finger(&d));
 two.active=0;
 d=(PadData){.timestamp=300000,.buttons=CLICK_LEFT|CLICK_RIGHT};assert(play_two_finger(&d));
 d=(PadData){.timestamp=3300000,.buttons=CLICK_LEFT|CLICK_RIGHT};
 assert(play_two_finger(&d)&&(d.buttons&BTN_TOUCHPAD)&&d.touch_count==2);
}
'''
        self.compile_run(source,['cc','-std=c11'])
    def test_requested_source_fps_survives_guest_flip_divider(self):
        source=r'''#include "iss_frame_pacing.h"
#include <cassert>
#include <algorithm>
int main(){
 for(int fps: {30,60,120,240})for(int divider: {0,1,2}){
  int hz=IssFramePacing::VblankHz(fps,divider);
  assert(hz>=60);
  // Requests gated by the original game's flip divider must still reach the UI ceiling.
  assert(std::min(fps,hz/(divider+1))==fps);
 }
 assert(IssFramePacing::VblankHz(120,0)==120);
 assert(IssFramePacing::VblankHz(120,1)==240);
 assert(IssFramePacing::VblankHz(240,1)==480);
 assert(IssFramePacing::VblankHz(999,0)==60);
}
'''
        self.compile_run(source,['c++','-std=c++17','-I'+str(ROOT/'port/gpu/shim')])
    def test_live_performance_state_rejects_partial_and_invalid_values(self):
        source=r'''
#include "iss_performance_state.h"
#include <cassert>
#include <fstream>
int main(){
 IssPerformance::State s;
 for(int fps: {30,60,120,240})for(int sync: {0,1}){
  std::ofstream("state")<<"fps_limit="<<fps<<"\nvsync="<<sync<<"\ncomplete=1\n";
  assert(IssPerformance::Read("state",s)&&s.fps==fps&&s.vsync==bool(sync));
 }
 std::ofstream("state")<<"fps_limit=120\nvsync=0\n";
 assert(!IssPerformance::Read("state",s)&&s.fps==240&&s.vsync);
 std::ofstream("state")<<"fps_limit=999\nvsync=0\ncomplete=1\n";
 assert(!IssPerformance::Read("state",s)&&s.fps==240);
}
'''
        self.compile_run(source,['c++','-std=c++17','-I'+str(ROOT/'port/gpu/shim')])
    @unittest.skipUnless(shutil.which('c++'), 'C++ compiler unavailable')
    def test_framegen_bounds_atomic_replacement_and_dll_discovery(self):
        source=r'''
#include "iss_lsfg_state.h"
#include <cassert>
#include <fstream>
#include <cstdio>
#include <cstdlib>
#include <thread>
using namespace IssLsfgState;
void write(const char* path,const char* value){ std::ofstream(path)<<value; }
int main(){
 setenv("ISS_LSFG_STATE_FILE","state",1);setenv("ISS_LSFG_MULTIPLIER","3",1);
 State s=Initial(); assert(s.multiplier==3);
 write("state","multiplier=4\nflow=10\nperformance=0\nfp16=0\n");
 assert(Read("state",s) && s.multiplier==4 && s.flow==1 && !s.fp16 && !s.performance);
 write("state","multiplier=2"); assert(!Read("state",s) && s.multiplier==4);
 write("state","multiplier=9\nflow=-1\n");assert(Read("state",s) && !s.On() && s.flow==.25f);
 Watcher watcher(s);State next;watcher.Poll(next);
 write("state.new","multiplier=2\nflow=0.5\n");std::rename("state.new","state");
 std::this_thread::sleep_for(std::chrono::milliseconds(260));
 assert(watcher.Poll(next) && next.multiplier==2 && next.flow==.5f);
 write("Lossless.dll","MZown");assert(FindDll()=="Lossless.dll");
 write("explicit.dll","MZexplicit");setenv("ISS_LSFG_DLL","explicit.dll",1);assert(FindDll()=="explicit.dll");
 std::remove("explicit.dll");assert(FindDll()=="Lossless.dll");
}'''
        self.compile_run(source,['c++','-std=c++20','-pthread','-D__ANDROID__',
                         '-I'+str(ROOT/'port/gpu/shim'),'-I'+str(ROOT/'port/gpu/shadps4'),
                         str(ROOT/'port/gpu/shim/iss_lsfg_state.cpp')])
    @unittest.skipUnless(shutil.which('java'), 'Java unavailable')
    def test_hud_rate_counters_and_persisted_configuration(self):
        sdk=os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','/nonexistent'))
        jar=Path(os.environ.get('ANDROID_API_JAR',str(Path(sdk)/'platforms/android-35/android.jar')))
        if not jar.is_file(): self.skipTest('Android API jar unavailable')
        with tempfile.TemporaryDirectory() as temp:
            src=ROOT/'android/app/src/ro/cobrabm/fexdroid'
            subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(jar),'-d',temp,
                            str(src/'HudConfig.java'),str(src/'PerfHudMetrics.java'),
                            str(ROOT/'tools/android/runtime/java/IssHudTest.java')],check=True,capture_output=True,text=True)
            subprocess.run(['java','-ea','-cp',str(jar)+os.pathsep+temp,'ro.cobrabm.fexdroid.IssHudTest'],
                           check=True,capture_output=True,text=True)
    def compile_run(self,source,command):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp); src=path/('test.cpp' if command[0]=='c++' else 'test.c'); src.write_text(source)
            subprocess.run(command+[str(src),'-o',str(path/'test')],check=True,capture_output=True,text=True)
            subprocess.run([str(path/'test')],cwd=temp,check=True,capture_output=True,text=True,timeout=10)
if __name__=='__main__': unittest.main()
