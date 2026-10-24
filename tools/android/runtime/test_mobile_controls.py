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
   'android/graphics/Canvas.java':'''package android.graphics;public class Canvas{public static java.util.List<String> text=new java.util.ArrayList<>();public void save(){}public void restore(){}public void scale(float a,float b){}public void scale(float a,float b,float x,float y){}public void translate(float x,float y){}public void drawCircle(float a,float b,float r,Paint p){}public void drawText(String s,float a,float b,Paint p){text.add(s);}public void drawRoundRect(float a,float b,float c,float d,float e,float f,Paint p){}public void drawLine(float a,float b,float c,float d,Paint p){}}''',
   'android/util/SparseArray.java':'''package android.util;import java.util.*;public class SparseArray<T>{private TreeMap<Integer,T> m=new TreeMap<>();public int size(){return m.size();}public T get(int k){return m.get(k);}public void put(int k,T v){m.put(k,v);}public void remove(int k){m.remove(k);}public void clear(){m.clear();}public int keyAt(int i){return new ArrayList<>(m.keySet()).get(i);}public T valueAt(int i){return m.get(keyAt(i));}}''',
   'android/view/View.java':'''package android.view;import android.content.*;import android.graphics.*;public class View{public View(Context c){}static class Task{long at;Runnable r;Task(long a,Runnable b){at=a;r=b;}}long now;public java.util.List<Task> delayed=new java.util.ArrayList<>();public boolean postDelayed(Runnable r,long ms){delayed.add(new Task(now+ms,r));return true;}public void advance(long ms){now+=ms;while(true){Task next=null;for(Task t:delayed)if(t.at<=now&&(next==null||t.at<next.at))next=t;if(next==null)break;delayed.remove(next);next.r.run();}}public void runDelayed(){advance(1000);}public void invalidate(){}public void setContentDescription(String s){}public int getWidth(){return 1280;}public int getHeight(){return 720;}public boolean onTouchEvent(MotionEvent e){return false;}protected void onDraw(Canvas c){}}''',
   'android/view/MotionEvent.java':'''package android.view;public class MotionEvent{public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6;int a,at;int[] ids;float[] xy;public MotionEvent(int a,int at,int[] ids,float... xy){this.a=a;this.at=at;this.ids=ids;this.xy=xy;}public int getActionMasked(){return a;}public int getActionIndex(){return at;}public int getPointerId(int i){return ids[i];}public int getPointerCount(){return ids.length;}public int findPointerIndex(int id){for(int i=0;i<ids.length;i++)if(ids[i]==id)return i;return -1;}public float getX(int i){return xy[2*i];}public float getY(int i){return xy[2*i+1];}}''',
   'ro/cobrabm/fexdroid/PadBridge.java':'''package ro.cobrabm.fexdroid;import java.util.*;class PadBridge{Set<String> buttons;int lx,ly,rx,ry,l2,r2;boolean finger,menu,sensor;boolean isSensorMode(){return sensor;}void toggleSensorMode(){sensor=!sensor;}Runnable change;void onModeChanged(Runnable r){change=r;}boolean isMenuMode(){return menu;}boolean isAutomaticMode(){return true;}void touch(boolean f,int x,int y){finger=f;}void virtual(Set<String>b,int lx,int ly,int rx,int ry,boolean l,boolean r,int l2,int r2){buttons=new HashSet<>(b);this.lx=lx;this.ly=ly;this.rx=rx;this.ry=ry;this.l2=l2;this.r2=r2;}void cycleInputMode(){change.run();}void gameMenuButton(){menu=!menu;change.run();}}''',
   'ro/cobrabm/fexdroid/AppLanguage.java':'package ro.cobrabm.fexdroid;class AppLanguage{static String text(String e,String p){return e;}}',
   'ro/cobrabm/fexdroid/XInput.java':'package ro.cobrabm.fexdroid;class XInput{static void moveTo(int x,int y){}static void button(int b,boolean d){}}',
   'ro/cobrabm/fexdroid/MobileTest.java':'''package ro.cobrabm.fexdroid;
import android.content.*;import android.view.*;import java.util.*;import android.graphics.*;
class MobileTest{
 static void event(TouchControllerOverlay v,int a,int at,int[] ids,float... xy){assert v.onTouchEvent(new MotionEvent(a,at,ids,xy));}
 static Map<String,PointF> centers(TouchControllerOverlay v)throws Exception{java.lang.reflect.Field f=v.getClass().getDeclaredField("centers");f.setAccessible(true);return (Map<String,PointF>)f.get(v);}
 static String hit(TouchControllerOverlay v,float x,float y)throws Exception{java.lang.reflect.Method m=v.getClass().getDeclaredMethod("hit",float.class,float.class);m.setAccessible(true);return (String)m.invoke(v,x,y);}
 static void down(TouchControllerOverlay v,String name)throws Exception{PointF q=centers(v).get(name);event(v,0,0,new int[]{1},q.x,q.y);}
 static void up(TouchControllerOverlay v,String name)throws Exception{PointF q=centers(v).get(name);event(v,1,0,new int[]{1},q.x,q.y);}
 public static void main(String[] args)throws Exception{
  Context c=new Context();PadBridge p=new PadBridge();TouchControllerOverlay v=new TouchControllerOverlay(c,p,false);
  Canvas drawing=new Canvas();v.onDraw(drawing);assert Canvas.text.contains("L1")&&Canvas.text.contains("R1")&&Canvas.text.contains("×");
  c.prefs.edit().putBoolean("controller_labels",false).apply();v.applyPreferences();Canvas.text.clear();v.onDraw(drawing);
  assert !Canvas.text.contains("L1")&&!Canvas.text.contains("R1")&&!Canvas.text.contains("×");
  c.prefs.edit().putBoolean("controller_labels",true).apply();v.applyPreferences();Canvas.text.clear();v.onDraw(drawing);assert Canvas.text.contains("L1");
  Map<String,PointF> layout=centers(v);
  assert layout.get("l1").x<640&&layout.get("l2").x<640&&layout.get("spray").x<640;
  assert layout.get("l1").y<360&&layout.get("l2").y<360;
  event(v,0,0,new int[]{1},230,585);assert p.lx==128&&p.ly==128;
  event(v,5,1,new int[]{1,2},230,585,700,400);assert p.lx==128&&p.rx==128;
  event(v,2,0,new int[]{1,2},250,565,740,380);assert p.lx>128&&p.ly<128&&p.rx>128&&p.ry<128;
  PointF fire=layout.get("r2");
  event(v,5,2,new int[]{1,2,3},250,565,740,380,fire.x,fire.y);assert p.r2==255&&p.rx>128&&p.lx>128;
  v.runDelayed();assert p.rx==128&&p.ry==128&&p.r2==255;
  event(v,6,1,new int[]{1,2,3},250,565,740,380,fire.x,fire.y);
  event(v,2,0,new int[]{1,3},250,565,fire.x+20,fire.y-15);assert p.r2==255&&p.rx>128&&p.ry<128&&p.lx>128;
  event(v,3,0,new int[]{1},250,565);assert p.lx==128&&p.r2==0&&p.buttons.isEmpty();
  // Holding a power or aim never consumes the opposite camera finger.
  for(String name:new String[]{"l1","l2"}){
   PointF q=layout.get(name);down(v,name);
   event(v,5,1,new int[]{1,2},q.x,q.y,700,400);
   event(v,2,0,new int[]{1,2},q.x,q.y,735,380);
   assert p.rx>128&&p.ry<128;
   assert name.equals("l1")?p.buttons.contains("l1"):p.l2==255;
   v.release();
  }
  down(v,"spray");assert p.r2==255&&!p.buttons.contains("l3");
  PointF spray=layout.get("spray");event(v,5,1,new int[]{1,2},spray.x,spray.y,700,400);
  event(v,2,0,new int[]{1,2},spray.x,spray.y,730,380);assert p.r2==255&&p.rx>128;v.release();
  down(v,"graffiti");assert p.sensor;up(v,"graffiti");assert "shake".equals(hit(v,986,151));assert !"cross".equals(hit(v,1126,619));down(v,"shake");assert p.buttons.contains("cross");v.release();down(v,"graffiti");assert !p.sensor;up(v,"graffiti");
  // A touch inside the top swipepad is a click; motion is a real swipe.
  c.prefs.edit().putBoolean("swipepad_enabled",true).apply();v.applyPreferences();
  assert !"touchpad".equals(hit(v,640,60));
  down(v,"swipepad");assert p.finger&&!p.buttons.contains("touchpad_left");
  up(v,"swipepad");assert !p.finger&&p.buttons.contains("touchpad_left")&&p.buttons.contains("touchpad_right");
  v.advance(90);assert p.buttons.isEmpty();
  down(v,"swipepad");event(v,2,0,new int[]{1},675,220);v.advance(200);
  assert p.finger&&!p.buttons.contains("touchpad_left");up(v,"swipepad");assert p.buttons.isEmpty()&&!p.finger;
  down(v,"swipepad");v.advance(180);assert !p.finger&&p.buttons.contains("touchpad_left");up(v,"swipepad");assert p.buttons.isEmpty();
  down(v,"swipepad");v.release();v.runDelayed();assert !p.finger&&p.buttons.isEmpty();
  // Closing auxiliary controls must release a held D-pad, not latch it invisibly.
  down(v,"utilities");up(v,"utilities");
  PointF sensorTarget=layout.get("graffiti");assert "graffiti".equals(hit(v,sensorTarget.x,sensorTarget.y));
  event(v,0,0,new int[]{1},185,390);assert p.buttons.contains("right");
  PointF utility=layout.get("utilities");event(v,5,1,new int[]{1,2},185,390,utility.x,utility.y);assert p.buttons.isEmpty();v.release();
  // Classic positions and mobile positions remain independent, including reset.
  c.prefs.edit().putFloat("overlay_x_cross",1111).putBoolean("mobile_controls",false).apply();v.applyPreferences();
  assert centers(v).get("cross").x==1111;
  c.prefs.edit().putBoolean("mobile_controls",true).apply();v.applyPreferences();assert centers(v).get("cross").x==1126;
  v.resetLayout();assert c.prefs.getFloat("overlay_x_cross",0)==1111;
  c.prefs.edit().putBoolean("swipepad_enabled",false).apply();v.applyPreferences();assert !"touchpad".equals(hit(v,640,60));
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
