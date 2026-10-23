"""Exercise the production touch overlay's mobile camera, multitouch and saved layouts."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[3]

class MobileControlsTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('java'),'Java unavailable')
 def test_drag_camera_with_move_and_fire_release_and_classic_layout(self):
  sources={
   'android/content/SharedPreferences.java':'''package android.content;import java.util.*;public class SharedPreferences{
    Map<String,Object> data=new HashMap<>();public boolean getBoolean(String k,boolean d){return data.containsKey(k)?(Boolean)data.get(k):d;}
    public float getFloat(String k,float d){return data.containsKey(k)?(Float)data.get(k):d;}public Editor edit(){return new Editor();}
    public class Editor{public Editor putFloat(String k,float v){data.put(k,v);return this;}public Editor putBoolean(String k,boolean v){data.put(k,v);return this;}public Editor remove(String k){data.remove(k);return this;}public void apply(){}}
   }''',
   'android/content/Context.java':'''package android.content;public class Context{public static int MODE_PRIVATE=0;public SharedPreferences prefs=new SharedPreferences();public SharedPreferences getSharedPreferences(String k,int m){return prefs;}public java.io.File getFilesDir(){return new java.io.File(".");}}''',
   'android/graphics/PointF.java':'''package android.graphics;public class PointF{public float x,y;public PointF(float a,float b){set(a,b);}public void set(float a,float b){x=a;y=b;}}''',
   'android/graphics/Typeface.java':'package android.graphics;public class Typeface{public static Typeface DEFAULT_BOLD=new Typeface();}',
   'android/graphics/Paint.java':'''package android.graphics;public class Paint{public static int ANTI_ALIAS_FLAG=1;public enum Style{FILL,STROKE}public enum Align{CENTER}public Paint(int a){}public void setStyle(Style s){}public void setColor(int c){}public void setStrokeWidth(float n){}public void setTextSize(float n){}public void setTypeface(Typeface t){}public void setTextAlign(Align a){}public float ascent(){return -10;}public float descent(){return 2;}public float measureText(String s){return s.length()*8;}}''',
   'android/graphics/Canvas.java':'''package android.graphics;public class Canvas{public void save(){}public void restore(){}public void scale(float a,float b){}public void scale(float a,float b,float x,float y){}public void translate(float x,float y){}public void drawCircle(float a,float b,float r,Paint p){}public void drawText(String s,float a,float b,Paint p){}public void drawRoundRect(float a,float b,float c,float d,float e,float f,Paint p){}public void drawLine(float a,float b,float c,float d,Paint p){}}''',
   'android/util/SparseArray.java':'''package android.util;import java.util.*;public class SparseArray<T>{private TreeMap<Integer,T> m=new TreeMap<>();public int size(){return m.size();}public T get(int k){return m.get(k);}public void put(int k,T v){m.put(k,v);}public void remove(int k){m.remove(k);}public void clear(){m.clear();}public int keyAt(int i){return new ArrayList<>(m.keySet()).get(i);}public T valueAt(int i){return m.get(keyAt(i));}}''',
   'android/view/View.java':'''package android.view;import android.content.*;import android.graphics.*;public class View{public View(Context c){}public java.util.List<Runnable> delayed=new java.util.ArrayList<>();public boolean postDelayed(Runnable r,long ms){delayed.add(r);return true;}public void runDelayed(){for(Runnable r:delayed)r.run();delayed.clear();}public void invalidate(){}public void setContentDescription(String s){}public int getWidth(){return 1280;}public int getHeight(){return 720;}public boolean onTouchEvent(MotionEvent e){return false;}protected void onDraw(Canvas c){}}''',
   'android/view/MotionEvent.java':'''package android.view;public class MotionEvent{public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6;int a,at;int[] ids;float[] xy;public MotionEvent(int a,int at,int[] ids,float... xy){this.a=a;this.at=at;this.ids=ids;this.xy=xy;}public int getActionMasked(){return a;}public int getActionIndex(){return at;}public int getPointerId(int i){return ids[i];}public int getPointerCount(){return ids.length;}public int findPointerIndex(int id){for(int i=0;i<ids.length;i++)if(ids[i]==id)return i;return -1;}public float getX(int i){return xy[2*i];}public float getY(int i){return xy[2*i+1];}}''',
   'ro/cobrabm/fexdroid/PadBridge.java':'''package ro.cobrabm.fexdroid;import java.util.*;class PadBridge{Set<String> buttons;int lx,ly,rx,ry,l2,r2;boolean finger;Runnable change;void onModeChanged(Runnable r){change=r;}boolean isMenuMode(){return false;}boolean isAutomaticMode(){return true;}void touch(boolean f,int x,int y){finger=f;}void virtual(Set<String>b,int lx,int ly,int rx,int ry,boolean l,boolean r,int l2,int r2){buttons=new HashSet<>(b);this.lx=lx;this.ly=ly;this.rx=rx;this.ry=ry;this.l2=l2;this.r2=r2;}void cycleInputMode(){change.run();}void gameMenuButton(){change.run();}}''',
   'ro/cobrabm/fexdroid/AppLanguage.java':'package ro.cobrabm.fexdroid;class AppLanguage{static String text(String e,String p){return e;}}',
   'ro/cobrabm/fexdroid/XInput.java':'package ro.cobrabm.fexdroid;class XInput{static void moveTo(int x,int y){}static void button(int b,boolean d){}}',
   'ro/cobrabm/fexdroid/MobileTest.java':'''package ro.cobrabm.fexdroid;
import android.content.*;import android.view.*;import java.util.*;import android.graphics.*;
class MobileTest{
 static void event(TouchControllerOverlay v,int a,int at,int[] ids,float... xy){assert v.onTouchEvent(new MotionEvent(a,at,ids,xy));}
 public static void main(String[] args)throws Exception{
  Context c=new Context();PadBridge p=new PadBridge();TouchControllerOverlay v=new TouchControllerOverlay(c,p,false);
  event(v,0,0,new int[]{1},180,575);assert p.lx==128&&p.ly==128;
  event(v,5,1,new int[]{1,2},180,575,800,300);assert p.lx==128&&p.rx==128;
  event(v,2,0,new int[]{1,2},210,550,840,280);assert p.lx>128&&p.ly<128&&p.rx>128&&p.ry<128;
  event(v,5,2,new int[]{1,2,3},210,550,840,280,1190,355);assert p.r2==255&&p.rx>128&&p.lx>128;
  v.runDelayed();assert p.rx==128&&p.ry==128&&p.r2==255;
  event(v,6,1,new int[]{1,2,3},210,550,840,280,1190,355);assert p.rx==128&&p.ry==128&&p.r2==255;
  event(v,3,0,new int[]{1},210,550);assert p.lx==128&&p.r2==0&&p.buttons.isEmpty();
  // Toggling layout must release active presses, retain old classic positions,
  // and restore mobile positions independently when switching back.
  c.prefs.edit().putFloat("overlay_x_cross",1111).putBoolean("mobile_controls",false).apply();v.applyPreferences();
  java.lang.reflect.Field f=v.getClass().getDeclaredField("centers");f.setAccessible(true);Map<String,PointF> centers=(Map<String,PointF>)f.get(v);
  assert centers.get("cross").x==1111;
  c.prefs.edit().putBoolean("mobile_controls",true).apply();v.applyPreferences();assert centers.get("cross").x==1160;
  c.prefs.edit().putBoolean("swipepad_enabled",true).apply();v.applyPreferences();
  event(v,0,0,new int[]{1},640,600);assert p.finger;v.release();assert !p.finger;
  v.resetLayout();assert c.prefs.getFloat("overlay_x_cross",0)==1111;
 }
}'''
  }
  sources['ro/cobrabm/fexdroid/TouchControllerOverlay.java']=(ROOT/'android/app/src/ro/cobrabm/fexdroid/TouchControllerOverlay.java').read_text()
  with tempfile.TemporaryDirectory() as tmp:
   paths=[]
   for name,source in sources.items():
    p=Path(tmp)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);paths.append(str(p))
   result=subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-d',tmp,*paths],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
   result=subprocess.run(['java','-ea','-cp',tmp,'ro.cobrabm.fexdroid.MobileTest'],cwd=tmp,capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
