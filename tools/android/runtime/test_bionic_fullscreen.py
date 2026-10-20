"""Run production fullscreen policy/window methods across toggle and relaunch."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_bionic_resource_reuse import method
ROOT=Path(__file__).resolve().parents[3]
JAVA=shutil.which('java')

@unittest.skipUnless(JAVA,'JDK runtime unavailable')
class FullscreenTests(unittest.TestCase):
    def test_default_off_switching_and_relaunch(self):
        source=(ROOT/'android/app/src/ro/cobrabm/fexdroid/BionicVideoSettings.java').read_text()
        policy='\n'.join(method(source,s) for s in (' static boolean fullscreen(', ' static String gameDisplayMode('))
        activity=(ROOT/'android/app/src/ro/cobrabm/fexdroid/BionicGameActivity.java').read_text()
        apply=method(activity,' private void applyFullscreen()')
        code=r'''
import android.content.SharedPreferences;
class FullscreenCase {
 static class Prefs implements SharedPreferences {
  boolean enabled=false;String mode="stretch";
  public boolean getBoolean(String k,boolean fallback){assert k.equals("fullscreen");return enabled;}
  public String getString(String k,String fallback){assert k.equals("display_mode");return mode;}
 }
 static class BionicVideoSettings {
''' + policy + r'''
 }
 static class View {static final int SYSTEM_UI_FLAG_VISIBLE=0;int flags=-1;
  void setSystemUiVisibility(int value){flags=value;}}
 static class WindowManager {static class LayoutParams {static final int FLAG_FULLSCREEN=1024;}}
 static class Window {int flags;View view=new View();void addFlags(int f){flags|=f;}
  void clearFlags(int f){flags&=~f;}View getDecorView(){return view;}}
 static class Frame {void post(Runnable f){f.run();}}
 static class Game {
  Prefs prefs;Window window=new Window();Frame frame=new Frame();String screenMode;int updates;
  Game(Prefs p){prefs=p;}SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
  Window getWindow(){return window;}void updateVideoSize(){++updates;}
''' + apply + r'''
 }
 public static void main(String[] args){
  Prefs p=new Prefs();Game g=new Game(p);g.applyFullscreen();
  assert g.screenMode.equals("fit") && g.window.flags==0 && g.window.view.flags==0;
  p.enabled=true;g.applyFullscreen();
  assert g.screenMode.equals("stretch") && g.window.flags==1024 && g.window.view.flags==5894;
  p.mode="crop";g.applyFullscreen();assert g.screenMode.equals("crop");
  p.enabled=false;g.applyFullscreen();
  assert g.screenMode.equals("fit") && g.window.flags==0 && g.window.view.flags==0;
  assert p.mode.equals("crop"); // turning off must not discard the chosen fullscreen aspect
  Game restarted=new Game(p);restarted.applyFullscreen();assert restarted.screenMode.equals("fit");
  p.enabled=true;restarted.applyFullscreen();assert restarted.screenMode.equals("crop");
  p.mode="fill";restarted.applyFullscreen();assert restarted.screenMode.equals("stretch");
  p.mode="fit";restarted.applyFullscreen();assert restarted.screenMode.equals("fit");
  assert g.updates==4;
 }
}
'''
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp);stub=path/'android/content/SharedPreferences.java';stub.parent.mkdir(parents=True)
            stub.write_text('package android.content; public interface SharedPreferences {boolean getBoolean(String k,boolean fallback);String getString(String k,String fallback);}')
            source=path/'FullscreenCase.java';source.write_text(code)
            result=subprocess.run([JAVA,'com.sun.tools.javac.Main','-d',temp,str(stub),str(source)],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            result=subprocess.run([JAVA,'-ea','-cp',temp,'FullscreenCase'],capture_output=True,text=True,timeout=10)
            self.assertEqual(result.returncode,0,result.stderr)

if __name__=='__main__':unittest.main()
