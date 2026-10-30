"""Execute Android bridge transitions and the runtime's actual final pad sampler."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


class MenuSwipepad(unittest.TestCase):
    @unittest.skipUnless(shutil.which('java'), 'Java unavailable')
    def test_android_start_override_and_direct_finger(self):
        bridge = (ROOT/'android/app/src/ro/cobrabm/fexdroid/PadBridge.java').read_text()
        constants = sorted(set(re.findall(r'KeyEvent\.(KEYCODE_\w+|ACTION_\w+)', bridge)))
        sources = {
            'ro/cobrabm/fexdroid/PadBridge.java': bridge,
            'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
            'android/os/Handler.java': 'package android.os; public class Handler { public Handler(Looper l){} public boolean post(Runnable r){r.run();return true;} public boolean postDelayed(Runnable r,long ms){r.run();return true;} }',
            'android/os/HandlerThread.java': 'package android.os; public class HandlerThread extends Thread { public HandlerThread(String n,int p){} public Looper getLooper(){return null;} }',
            'android/os/Process.java': 'package android.os; public class Process { public static final int THREAD_PRIORITY_URGENT_DISPLAY=-8; }',
            'android/util/Log.java': 'package android.util; public class Log {public static int e(String t,String m,Throwable x){throw new AssertionError(x);} }',
            'android/view/InputDevice.java': 'package android.view; public class InputDevice {public static final int SOURCE_JOYSTICK=1;}',
            'android/view/KeyEvent.java': '''package android.view; public class KeyEvent {
                %s
                int action,code,repeat; public KeyEvent(int a,int c,int r){action=a;code=c;repeat=r;}
                public int getAction(){return action;} public int getKeyCode(){return code;}
                public int getRepeatCount(){return repeat;} public int getScanCode(){return 0;}
            }''' % '\n'.join(f'public static final int {c}={i};' for i,c in enumerate(constants)),
            'android/view/MotionEvent.java': '''package android.view; public class MotionEvent {
                %s
                public boolean isFromSource(int s){return true;} public float getAxisValue(int a){return 0;}
            }''' % '\n'.join(f'public static final int {c}={i};' for i,c in enumerate(sorted(set(re.findall(r'MotionEvent\.(AXIS_\w+)',bridge))))),
            'ro/cobrabm/fexdroid/MenuTest.java': r'''package ro.cobrabm.fexdroid;
import android.view.KeyEvent; import java.io.*; import java.nio.file.*; import java.util.*;
public class MenuTest {
 static File file=new File("pad").getAbsoluteFile();static PadBridge p=new PadBridge(file);
 static void event(int code,int action,int repeat){assert p.key(new KeyEvent(action,code,repeat));}
 static void down(int c){event(c,KeyEvent.ACTION_DOWN,0);}static void up(int c){event(c,KeyEvent.ACTION_UP,0);}
 static String state()throws Exception{return new String(Files.readAllBytes(file.toPath()),"US-ASCII");}
 public static void main(String[] args)throws Exception{
  p.setDpadSwipe(true);assert !p.isMenuMode();assert state().contains("input_menu=0");
  down(KeyEvent.KEYCODE_BUTTON_START);assert p.isMenuMode();assert state().contains("options ");
  event(KeyEvent.KEYCODE_BUTTON_START,KeyEvent.ACTION_DOWN,1);assert p.isMenuMode();up(KeyEvent.KEYCODE_BUTTON_START);
  down(KeyEvent.KEYCODE_DPAD_UP);down(KeyEvent.KEYCODE_BUTTON_A);assert state().contains("up ")&&state().contains("cross ");
  up(KeyEvent.KEYCODE_DPAD_UP);up(KeyEvent.KEYCODE_BUTTON_A);
  down(KeyEvent.KEYCODE_BUTTON_START);assert !p.isMenuMode();up(KeyEvent.KEYCODE_BUTTON_START);
  down(KeyEvent.KEYCODE_BUTTON_L1);down(KeyEvent.KEYCODE_BUTTON_R1);down(KeyEvent.KEYCODE_BUTTON_START);
  assert p.isMenuMode()&&!state().contains("options ");up(KeyEvent.KEYCODE_BUTTON_START);
  p.setMenuMode(false);p.touch(true,-10,5000);p.virtual(Collections.singleton("cross"),128,128,128,128,false,false,0,0);
  assert state().contains("finger=1 tx=0 ty=942 tid=1")&&state().contains("cross ");
  p.touch(true,1200,400);p.virtual(Collections.emptySet(),128,128,128,128,false,false,0,0);
  assert state().contains("tx=1200 ty=400 tid=1");
  p.touch(false,0,0);p.virtual(Collections.emptySet(),128,128,128,128,false,false,0,0);assert !state().contains("finger=1");
  p.touch(true,500,200);p.virtual(Collections.emptySet(),128,128,128,128,false,false,0,0);assert state().contains("tid=2");
  p.setMenuMode(true);assert !state().contains("finger=1")&&!state().contains("cross ");
  p.touch(true,500,200);p.virtual(Collections.emptySet(),128,128,128,128,false,false,0,0);assert !state().contains("finger=1");
  p.reset();assert !state().contains("finger=1");
 }
}''',
        }
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); files=[]
            for name,content in sources.items():
                p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content);files.append(str(p))
            subprocess.run(['java','com.sun.tools.javac.Main','-d',temp]+files,check=True,capture_output=True,text=True)
            subprocess.run(['java','-ea','-cp',temp,'ro.cobrabm.fexdroid.MenuTest'],cwd=temp,check=True,capture_output=True,text=True)

    @unittest.skipUnless(shutil.which('cc'), 'C compiler unavailable')
    def test_native_menu_bypasses_swipe_and_graffiti_and_releases_finger(self):
        runtime=(ROOT/'port/src/runtime_pad.c').read_text()
        start=runtime.index('static void sample(PadData *d) {')
        sample=runtime[start:runtime.index('\nstatic ABI int32_t pad_init',start)]
        source=r'''
#include <assert.h>
#include <string.h>
#include <stdint.h>
#include "dpad_swipe.h"
#include "iss_menu_state.h"
enum {BTN_UP=0x10,BTN_RIGHT=0x20,BTN_DOWN=0x40,BTN_LEFT=0x80,BTN_L2=0x100,BTN_R2=0x200,
 BTN_L3=2,BTN_R3=4,BTN_CROSS=0x4000,BTN_TOUCHPAD=0x100000,CLICK_LEFT=0x40000000u,CLICK_RIGHT=0x80000000u};
typedef struct {uint16_t x,y;uint8_t id;} PadTouch;
typedef struct {uint32_t buttons;uint8_t left_x,left_y,right_x,right_y,l2,r2,touch_count;PadTouch touches[2];} PadData;
static struct {uint32_t buttons;int stick[4],trigger[2];} injected={0,{-1,-1,-1,-1},{-1,-1}};
static struct {int side;} click;static struct {int active;} two;
static struct {int mode;} motion;static struct {int down,x,y,id;} android_finger;
static IssDpadSwipe android_swipe;static int android_dpad_swipe,android_menu_mode,hold_after_capture;
static int android_menu_automatic;static const uint32_t *game_menu_counter;
static uint64_t tick;static uint64_t now_us(void){return tick+=16000;}
static void read_inject(void){}static void sample_host(PadData *d){memset(d,0,sizeof(*d));d->left_x=d->left_y=d->right_x=d->right_y=128;}
static int tlggpu_overlay_captures_input(void){return 0;}static int android_pad_enabled(void){return 1;}
static void record_sample(PadData *d){(void)d;}static void replay_sample(PadData *d){(void)d;}
static int play_two_finger(PadData *d){(void)d;return 0;}static void play_click(PadData *d){(void)d;}
''' + sample + r'''
int main(void){
 PadData d;android_dpad_swipe=1;motion.mode=1;android_menu_mode=1;
 injected.buttons=BTN_CROSS|BTN_UP;injected.stick[0]=200;
 sample(&d);assert(d.buttons==(BTN_CROSS|BTN_UP)&&d.touch_count==0&&d.left_x==200);
 android_menu_mode=0;sample(&d);assert(!(d.buttons&(BTN_CROSS|BTN_UP))&&d.touch_count==1&&d.left_x==128);
 android_menu_mode=1;sample(&d);assert(!android_swipe.active&&d.buttons==(BTN_CROSS|BTN_UP)&&!d.touch_count);
 android_menu_mode=0;motion.mode=0;injected.buttons=BTN_CROSS;
 android_finger.down=1;android_finger.x=500;android_finger.y=300;android_finger.id=7;
 sample(&d);assert(d.touch_count==1&&d.touches[0].x==500&&d.touches[0].y==300&&d.touches[0].id==7&&d.buttons==BTN_CROSS);
 android_finger.x=1800;sample(&d);assert(d.touches[0].x==1800&&d.touches[0].id==7);
 android_finger.down=0;sample(&d);assert(!d.touch_count&&d.buttons==BTN_CROSS);
 android_finger.down=1;android_menu_mode=1;sample(&d);assert(!d.touch_count&&d.buttons==BTN_CROSS);
 // The game's state wins over a stale Start guess, including exits via a menu item.
 uint32_t real_count=1;game_menu_counter=&real_count;android_menu_automatic=1;android_menu_mode=0;
 android_finger.down=0;injected.buttons=BTN_UP|BTN_CROSS;motion.mode=1;
 sample(&d);assert(android_menu_mode==1&&d.buttons==(BTN_UP|BTN_CROSS)&&!d.touch_count);
 real_count=0;sample(&d);assert(android_menu_mode==0&&!(d.buttons&(BTN_UP|BTN_CROSS))&&d.touch_count==1);
 real_count=2;sample(&d);assert(android_menu_mode==1&&d.buttons==(BTN_UP|BTN_CROSS));
 android_menu_automatic=0;android_menu_mode=0;sample(&d);assert(!android_menu_mode&&d.touch_count==1);
 return 0;
}'''
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp);(p/'test.c').write_text(source)
            subprocess.run(['cc','-std=c11','-I'+str(ROOT/'port/src'),str(p/'test.c'),'-o',str(p/'test')],check=True,capture_output=True,text=True)
            subprocess.run([str(p/'test')],check=True,capture_output=True,text=True)

    @unittest.skipUnless(shutil.which('cc'), 'C compiler unavailable')
    def test_menu_signature_gates_and_counter_range(self):
        source=r'''
#include "iss_menu_state.h"
#include <assert.h>
#include <stdlib.h>
int main(void){
 uint32_t n=0;assert(iss_menu_read(NULL)==-1&&iss_menu_read(&n)==0);
 n=1;assert(iss_menu_read(&n)==1);n=4;assert(iss_menu_read(&n)==1);
 n=UINT32_MAX;assert(iss_menu_read(&n)==-1);n=65;assert(iss_menu_read(&n)==-1);
 assert(!iss_menu_signatures_match(NULL,UINT64_MAX));
 size_t size=ISS_MENU_COUNT_OFFSET+4;uint8_t *p=calloc(1,size);assert(p);
 assert(!iss_menu_signatures_match(p,size));
 memcpy(p+ISS_MENU_OPEN_SITE,iss_menu_open_signature,sizeof(iss_menu_open_signature));
 memcpy(p+ISS_MENU_CLOSE_SITE,iss_menu_close_signature,sizeof(iss_menu_close_signature));
 assert(iss_menu_signatures_match(p,size));assert(!iss_menu_signatures_match(p,size-1));
 p[ISS_MENU_OPEN_SITE+3]^=1;assert(!iss_menu_signatures_match(p,size));p[ISS_MENU_OPEN_SITE+3]^=1;
 p[ISS_MENU_CLOSE_SITE+3]^=1;assert(!iss_menu_signatures_match(p,size));free(p);
 return 0;
}'''
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp);(p/'test.c').write_text(source)
            subprocess.run(['cc','-std=c11','-I'+str(ROOT/'port/src'),str(p/'test.c'),'-o',str(p/'test')],check=True,capture_output=True,text=True)
            subprocess.run([str(p/'test')],check=True,capture_output=True,text=True)


if __name__ == '__main__':
    unittest.main()
