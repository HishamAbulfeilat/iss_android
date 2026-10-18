package ro.cobrabm.fexdroid;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.*;

/** Only the experimental Bionic APK uses this activity.
 * No Xvfb, FEXDroid bridge, rootfs or Linux/glibc Vulkan loader.
 * Keeps the existing gamepad overlay, virtual graffiti and fullscreen modes.
 */
public final class BionicGameActivity extends Activity implements SurfaceHolder.Callback {
 private FrameLayout frame;
 private SurfaceView surface;
 private TextView status;
 private TextView fpsHud;
 private boolean showFps;
 private long fpsSampleFrames=-1;
 private long fpsSampleNs;
 private PadBridge pad;
 private AudioBridge audio;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private Process game;
 private boolean destroyed,started,failed;
 private String files,base,screenMode;
 private long lastFrames=-1;
 private synchronized void record(String line){
  try(FileOutputStream f=new FileOutputStream(new File(files,"iss.log"),true)){
   f.write(("[Bionic] "+line+"\n").getBytes(StandardCharsets.UTF_8));
  }catch(Exception ignored){}
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  getWindow().getDecorView().setSystemUiVisibility(5894);
  files=getFilesDir().getPath();base=files+"/issport";
  screenMode=getSharedPreferences("iss",0).getString("display_mode","stretch");
  if("fill".equals(screenMode))screenMode="stretch";
  showFps=getSharedPreferences("iss",0).getBoolean("show_fps",false);
  try(FileOutputStream out=new FileOutputStream(new File(files,"iss.log"),false)){
   out.write("ISS Android Bionic experimental — no glibc or Xvfb\n".getBytes(StandardCharsets.UTF_8));
  }catch(Exception ignored){}
  pad=new PadBridge(new File(base,"android-pad.state"));
  audio=new AudioBridge(new File(base,"audio.fifo"));
  frame=new FrameLayout(this);frame.setBackgroundColor(0xff000000);
  surface=new SurfaceView(this);frame.addView(surface);
  frame.addView(new TouchControllerOverlay(this,pad,false));
  status=new TextView(this);status.setText("Starting inFAMOUS Bionic…");
  status.setGravity(Gravity.CENTER);status.setTextColor(0xffe0c891);status.setTextSize(18);

  // Native Bionic loads a Turnip HAL directly; ImGui's Vulkan backend may not
  // resolve all of its entry points. Render the requested FPS independently
  // in the Android window, above the SurfaceView (and above the pad overlay).
  // Count the frames received by IssPresent, never a guessed 60/120 Hz value.
  fpsHud=new TextView(this);
  fpsHud.setTextColor(0xfff2f8e9);
  fpsHud.setTextSize(17);
  fpsHud.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
  fpsHud.setGravity(Gravity.CENTER);
  fpsHud.setClickable(false);
  fpsHud.setFocusable(false);
  final float density=getResources().getDisplayMetrics().density;
  int horizontal=Math.round(10*density), vertical=Math.round(6*density);
  fpsHud.setPadding(horizontal,vertical,horizontal,vertical);
  android.graphics.drawable.GradientDrawable fpsBg=new android.graphics.drawable.GradientDrawable();
  fpsBg.setColor(0xb51b2224);
  fpsBg.setCornerRadius(6*density);
  fpsHud.setBackground(fpsBg);
  FrameLayout.LayoutParams fpsParams=new FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,
      Gravity.TOP|Gravity.RIGHT);
  fpsParams.topMargin=Math.round(14*density);
  fpsParams.rightMargin=Math.round(14*density);
  frame.addView(status);
  if(showFps){fpsHud.setVisibility(View.GONE);frame.addView(fpsHud,fpsParams);}
  setContentView(frame);
  frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->updateVideoSize());
  frame.post(this::updateVideoSize);
  surface.getHolder().addCallback(this);
  record("Activity initialized");
 }
 private void updateVideoSize(){
  if(frame==null||surface==null)return;
  int w=frame.getWidth(),h=frame.getHeight();if(w<1||h<1)return;
  float scale="crop".equals(screenMode)?Math.max(w/1280f,h/720f):Math.min(w/1280f,h/720f);
  int vw="stretch".equals(screenMode)?w:Math.max(1,Math.round(1280*scale));
  int vh="stretch".equals(screenMode)?h:Math.max(1,Math.round(720*scale));
  FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)surface.getLayoutParams();
  if(p.width!=vw||p.height!=vh||p.gravity!=Gravity.CENTER){
   p.width=vw;p.height=vh;p.gravity=Gravity.CENTER;surface.setLayoutParams(p);
  }
 }
 private Process process(String... command)throws IOException{
  ProcessBuilder p=new ProcessBuilder(command).redirectErrorStream(true);
  Map<String,String> e=p.environment();e.put("FXD_FILES",files);
  e.put("ISS_GAME_DIR",getIntent().getStringExtra("game_dir"));
  e.put("ISS_BIONIC","1");e.put("ISS_ANDROID_INPUT","1");
  e.put("ISS_BIONIC_DRIVER",getSharedPreferences("iss",0).getString("bionic_driver","gen8"));
  String outputResolution=getSharedPreferences("iss",0).getString("output_res","1920x1080");
  boolean output1080p="1920x1080".equals(outputResolution);
  e.put("ISS_SCREEN_W",output1080p?"1920":"1280");
  e.put("ISS_SCREEN_H",output1080p?"1080":"720");
  e.put("ISS_PRESENT_SOCKET",base+"/present.sock");
  e.put("TLG_PAD_FILE",base+"/android-pad.state");
  e.put("TLG_SHOW_FPS",showFps?"1":"0");
  e.put("TLG_ANDROID_FPS_ONLY","1");
  java.util.ArrayList<String> patches=new java.util.ArrayList<>();
  android.content.SharedPreferences prefs=getSharedPreferences("iss",0);
  String resolution=prefs.getString("internal_res","native");
  if("720p".equals(resolution))patches.add("Render resolution 1280x720");
  else if("900p".equals(resolution))patches.add("Render resolution 1600x900");
  if(prefs.getBoolean("patch_motion_blur",false))patches.add("Disable motion blur");
  if(prefs.getBoolean("patch_dof",false))patches.add("Disable depth of field");
  if(prefs.getBoolean("patch_depth_blur",false))patches.add("Disable depth blur");
  e.put("ISS_PATCHES",android.text.TextUtils.join(";",patches));
  e.put("TLG_BP09","0");e.put("TLG_BC_TINY","0");
  e.put("TLG_OUTPUT_RES",prefs.getString("output_res","1920x1080"));
  return p.start();
 }
 @Override public void surfaceCreated(SurfaceHolder h){
  if(destroyed||started)return;
  started=true;
  try{
   String result=IssPresent.start(h,base+"/present.sock","stretch".equals(screenMode));
   record(result);
   if(!result.startsWith("bridge running:")){error(result);return;}
   audio.start();if(!audio.awaitReady(5000)){error("Audio FIFO did not start");return;}
   worker.execute(()->{
    try{
     record("Executing native Bionic inFAMOUS");
     game=process("/system/bin/sh",base+"/run-bionic.sh");
     try(InputStream in=game.getInputStream();FileOutputStream out=new FileOutputStream(new File(files,"iss.log"),true)){
      byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
     }
     int exit=game.waitFor();record("Bionic game exited "+exit);
     if(!destroyed)error("Game stopped ("+exit+"). Export log.");
    }catch(Exception ex){error(ex.toString());}
   });
   poll();
  }catch(Exception ex){error(ex.toString());}
 }
 private void poll(){
  if(destroyed)return;
  long n=IssPresent.frames();
  if(!failed&&n>0)status.setVisibility(View.GONE);
  if(n>0&&lastFrames<0){record("Bionic direct AHardwareBuffer frames active");lastFrames=n;}
  // IssPresent.nativeFrames counts guest frames delivered to the Android
  // presenter. Sample over elapsed *monotonic* time, not UI refresh events.
  if(showFps&&fpsHud!=null&&n>0){
   long now=android.os.SystemClock.elapsedRealtimeNanos();
   if(fpsSampleFrames<0||n<fpsSampleFrames){
    fpsSampleFrames=n;fpsSampleNs=now;
   }else if(now-fpsSampleNs>=400000000L){
    double fps=(n-fpsSampleFrames)*1000000000.0/(now-fpsSampleNs);
    fpsHud.setText(String.format(java.util.Locale.US,"%.1f FPS",fps));
    fpsHud.setVisibility(View.VISIBLE);
    fpsSampleFrames=n;fpsSampleNs=now;
   }
  }
  main.postDelayed(this::poll,showFps?500:1000);
 }
 private void error(String message){failed=true;record("ERROR: "+message);main.post(()->{status.setText(message);status.setVisibility(View.VISIBLE);});}
 @Override public void surfaceChanged(SurfaceHolder h,int format,int w,int ht){}
 @Override public void surfaceDestroyed(SurfaceHolder h){}
 @Override public boolean dispatchKeyEvent(KeyEvent e){return pad!=null&&pad.key(e)||super.dispatchKeyEvent(e);}
 @Override public boolean onGenericMotionEvent(MotionEvent e){return pad!=null&&pad.motion(e)||super.onGenericMotionEvent(e);}
 @Override protected void onPause(){
  super.onPause();fpsSampleFrames=-1;fpsSampleNs=0;
  if(fpsHud!=null)fpsHud.setVisibility(View.GONE);
  if(pad!=null)pad.reset();if(audio!=null)audio.pause();
 }
 @Override protected void onResume(){
  super.onResume();fpsSampleFrames=-1;fpsSampleNs=0;
  if(audio!=null)audio.resume();
 }
 @Override protected void onDestroy(){
  destroyed=true;main.removeCallbacksAndMessages(null);
  if(game!=null)game.destroy();IssPresent.stop();if(audio!=null)audio.stop();
  worker.shutdownNow();super.onDestroy();
 }
}
