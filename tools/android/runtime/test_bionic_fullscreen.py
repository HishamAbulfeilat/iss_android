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
        policy='\n'.join(method(source,s) for s in (
            ' static boolean fullscreen(', ' static String gameDisplayMode(',
            ' static String fsrMode(', ' static String fsrInternalResolution(',
            ' static String effectiveResolutionPatch(', ' static String fsrInputSize(',
            ' static int fsrEnginePreset(', ' static String outputResolution(',
            ' static String internalPatchName('))
        activity=(ROOT/'android/app/src/ro/cobrabm/fexdroid/BionicGameActivity.java').read_text()
        apply=method(activity,' private void applyFullscreen()')
        code=r'''
import android.content.SharedPreferences;
class FullscreenCase {
 static class Prefs implements SharedPreferences {
  Boolean enabled=null;boolean fsr3=false;String mode="stretch",resolution="native",fsrMode=null;int oldFsrPreset=1;
  public boolean getBoolean(String k,boolean fallback){if(k.equals("fsr3"))return fsr3;assert k.equals("fullscreen");return enabled==null?fallback:enabled;}
  public String getString(String k,String fallback){if(k.equals("internal_res"))return resolution;if(k.equals("fsr3_mode"))return fsrMode;assert k.equals("display_mode");return mode;}
  public int getInt(String k,int fallback){assert k.equals("fsr3_preset");return oldFsrPreset;}
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
  assert g.screenMode.equals("fit") && g.window.flags==1024 && g.window.view.flags==5894;
  p.enabled=true;g.applyFullscreen();
  assert g.screenMode.equals("stretch") && g.window.flags==1024 && g.window.view.flags==5894;
  p.mode="crop";g.applyFullscreen();assert g.screenMode.equals("crop");
  p.enabled=false;g.applyFullscreen();
  assert g.screenMode.equals("fit") && g.window.flags==1024 && g.window.view.flags==5894;
  assert p.mode.equals("crop"); // turning off must not discard the chosen fullscreen aspect
  Game restarted=new Game(p);restarted.applyFullscreen();assert restarted.screenMode.equals("fit");
  p.enabled=true;restarted.applyFullscreen();assert restarted.screenMode.equals("crop");
  p.mode="fill";restarted.applyFullscreen();assert restarted.screenMode.equals("stretch");
  p.mode="fit";restarted.applyFullscreen();assert restarted.screenMode.equals("stretch");
  assert g.updates==4;
  assert BionicVideoSettings.outputResolution(p).equals("1920x1080");
  p.resolution="720p";assert BionicVideoSettings.outputResolution(p).equals("1280x720");
  p.resolution="900p";assert BionicVideoSettings.outputResolution(p).equals("1600x900");
  p.fsr3=true;assert BionicVideoSettings.outputResolution(p).equals("1280x720");
  p.resolution="720p";assert BionicVideoSettings.outputResolution(p).equals("1280x720");
  p.fsrMode="quality";
  assert BionicVideoSettings.fsrMode(p).equals("quality");
  assert BionicVideoSettings.fsrInternalResolution(p).equals("540p");
  assert BionicVideoSettings.effectiveResolutionPatch(p).equals("540p");
  assert BionicVideoSettings.fsrInputSize(p).equals("960x540");
  assert BionicVideoSettings.fsrEnginePreset(p)==1;
  p.fsrMode="performance";
  assert BionicVideoSettings.fsrInternalResolution(p).equals("360p");
  assert BionicVideoSettings.effectiveResolutionPatch(p).equals("360p");
  assert BionicVideoSettings.fsrInputSize(p).equals("640x360");
  assert BionicVideoSettings.fsrEnginePreset(p)==3;
  p.fsrMode=null;p.oldFsrPreset=3;
  assert BionicVideoSettings.fsrMode(p).equals("performance");
  p.oldFsrPreset=1;assert BionicVideoSettings.fsrMode(p).equals("quality");
  p.fsr3=false;
  assert BionicVideoSettings.effectiveResolutionPatch(p).equals("720p");
  for(String key:new String[]{"540p","480p","360p"}){
   p.resolution=key;assert BionicVideoSettings.outputResolution(p).equals("1920x1080");
  }
  assert BionicVideoSettings.internalPatchName("540p").equals("Render resolution 960x540");
  assert BionicVideoSettings.internalPatchName("480p").equals("Render resolution 854x480");
  assert BionicVideoSettings.internalPatchName("360p").equals("Render resolution 640x360");
  assert BionicVideoSettings.internalPatchName("native")==null;
  p.resolution="old-invalid";assert BionicVideoSettings.outputResolution(p).equals("1920x1080");
 }
}
'''
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp);stub=path/'android/content/SharedPreferences.java';stub.parent.mkdir(parents=True)
            stub.write_text('package android.content; public interface SharedPreferences {boolean getBoolean(String k,boolean fallback);String getString(String k,String fallback);int getInt(String k,int fallback);}')
            source=path/'FullscreenCase.java';source.write_text(code)
            result=subprocess.run([JAVA,'com.sun.tools.javac.Main','-d',temp,str(stub),str(source)],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            result=subprocess.run([JAVA,'-ea','-cp',temp,'FullscreenCase'],capture_output=True,text=True,timeout=10)
            self.assertEqual(result.returncode,0,result.stderr)

if __name__=='__main__':unittest.main()
