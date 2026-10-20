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
 private TouchControllerOverlay controller;
 private BionicPlayerMenu menu;
 private boolean restarting,edgeGesture,swipeConsumed;
 private float edgeX,edgeY;
 private android.app.AlertDialog graffitiGuideDialog;
 private PadBridge pad;
 private AudioBridge audio;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private volatile Process game;
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
  try{FramegenSettings.write(this);BionicPerformanceSettings.write(this);}catch(IOException e){record(e.toString());}
  new File(base,"framegen.stats").delete();
  try(FileOutputStream out=new FileOutputStream(new File(files,"iss.log"),false)){
   out.write("ISS Android Bionic experimental — no glibc or Xvfb\n".getBytes(StandardCharsets.UTF_8));
  }catch(Exception ignored){}
  pad=new PadBridge(new File(base,"android-pad.state"));
  audio=new AudioBridge(new File(base,"audio.fifo"));
  frame=new FrameLayout(this);frame.setBackgroundColor(0xff000000);
  surface=new SurfaceView(this);frame.addView(surface);
  controller=new TouchControllerOverlay(this,pad,false);frame.addView(controller);
  status=new TextView(this);status.setText("Starting inFAMOUS Bionic…");
  status.setGravity(Gravity.CENTER);status.setTextColor(0xffe0c891);status.setTextSize(18);

  frame.addView(status);
  menu=new BionicPlayerMenu(this,frame,controller,pad,this::restartGame);
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
  e.put("TLG_SHOW_FPS","0");
  e.put("TLG_ANDROID_FPS_ONLY","1");
  java.util.ArrayList<String> patches=new java.util.ArrayList<>();
  android.content.SharedPreferences prefs=getSharedPreferences("iss",0);
  e.put("ISS_PERFORMANCE_STATE_FILE",base+"/performance.state");
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
    try(BionicSession.Guard ignored=BionicSession.acquire(getFilesDir())){
     BionicSaveArchive.recover(new File(base,"user/savedata"));
     synchronized(this){
      if(destroyed||restarting)return;
      record("Executing native Bionic inFAMOUS");
      game=process("/system/bin/sh",base+"/run-bionic.sh");
     }
     try{
     try(InputStream in=game.getInputStream();FileOutputStream out=new FileOutputStream(new File(files,"iss.log"),true)){
      byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
     }
     int exit=game.waitFor();record("Bionic game exited "+exit);
     if(!destroyed&&!restarting)error("Game stopped ("+exit+"). Export log.");
     }finally{
      // Hold the save lock until the native writer has actually exited, even on Activity teardown.
      if(game.isAlive())game.destroyForcibly();
      boolean interrupted=false;
      for(;;)try{game.waitFor();break;}catch(InterruptedException e){interrupted=true;}
      if(interrupted)Thread.currentThread().interrupt();
     }
    }catch(Exception ex){if(!destroyed)error(ex.toString());}
   });
   poll();
  }catch(Exception ex){error(ex.toString());}
 }
 private void showGraffitiGuideIfRequested(){
  if(destroyed || failed || graffitiGuideDialog!=null)return;
  // The native GPU records a guide request without touching an unavailable
  // ImGui context. Show the full instructions using the Android Activity.
  final File marker=new File(base,"android-pad.state.guide");
  if(!marker.isFile())return;
  graffitiGuideDialog=new android.app.AlertDialog.Builder(this)
   .setTitle("Grafite sem giroscópio")
   .setMessage("L3 + R3: ativar ou desativar o modo sensor e segurar a lata de lado.\n\n"
      +"✕ (X): sacudir a lata.\n\n"
      +"R2: pintar. Mire com o analógico esquerdo; movimentos pequenos dão mais precisão.\n\n"
      +"L3 + R3 novamente: voltar a andar.")
   .setPositiveButton("Entendi",(dialog,which)->{})
   .create();
  graffitiGuideDialog.setOnDismissListener(dialog->{
   if(marker.exists()&&!marker.delete())record("Could not delete graffiti guide marker");
   graffitiGuideDialog=null;
  });
  graffitiGuideDialog.show();
  record("Native Android graffiti guide displayed (no ImGui Vulkan dependency)");
 }
 private void poll(){
  if(destroyed)return;
  showGraffitiGuideIfRequested();
  long n=IssPresent.frames();
  if(!failed&&n>0)status.setVisibility(View.GONE);
  if(n>0&&lastFrames<0){record("Bionic direct AHardwareBuffer frames active");lastFrames=n;}
  main.postDelayed(this::poll,1000);
 }
 private void error(String message){failed=true;record("ERROR: "+message);main.post(()->{status.setText(message);status.setVisibility(View.VISIBLE);});}
 @Override public void surfaceChanged(SurfaceHolder h,int format,int w,int ht){}
 @Override public void surfaceDestroyed(SurfaceHolder h){}
 private void restartGame(){
  if(restarting||destroyed)return;restarting=true;status.setText("Reiniciando inFAMOUS…");status.setVisibility(View.VISIBLE);
  synchronized(this){if(game!=null)game.destroyForcibly();}
  worker.execute(()->main.post(()->{if(!destroyed)recreate();}));
 }
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(request==FramegenSettings.IMPORT_DLL&&result==RESULT_OK&&data!=null&&data.getData()!=null)FramegenSettings.importDll(this,data.getData(),null);}
 @Override public void onBackPressed(){if(menu!=null)menu.toggle();}
 @Override public boolean dispatchKeyEvent(KeyEvent e){
  int code=e.getKeyCode();
  if(code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_BUTTON_MODE){if(e.getAction()==KeyEvent.ACTION_UP&&menu!=null)menu.toggle();return true;}
  if(menu!=null&&menu.opened()){
   if(code==KeyEvent.KEYCODE_BUTTON_B){if(e.getAction()==KeyEvent.ACTION_UP)menu.close();return true;}
   return super.dispatchKeyEvent(e);
  }
  return pad!=null&&pad.key(e)||super.dispatchKeyEvent(e);
 }
 @Override public boolean dispatchTouchEvent(MotionEvent e){
  float density=getResources().getDisplayMetrics().density;int action=e.getActionMasked();
  if(action==MotionEvent.ACTION_DOWN){edgeX=e.getX();edgeY=e.getY();edgeGesture=menu!=null&&!menu.opened()&&edgeX<24*density;swipeConsumed=false;}
  if(swipeConsumed){if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)swipeConsumed=false;return true;}
  if(action==MotionEvent.ACTION_MOVE&&menu!=null){float dx=e.getX()-edgeX,dy=e.getY()-edgeY;
   if((edgeGesture&&dx>55*density&&Math.abs(dy)<80*density)||(menu.opened()&&dx< -70*density&&Math.abs(dy)<80*density)){
    MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();menu.toggle();edgeGesture=false;swipeConsumed=true;return true;
   }
  }
  return super.dispatchTouchEvent(e);
 }
 @Override public boolean onGenericMotionEvent(MotionEvent e){return menu!=null&&menu.opened()?super.onGenericMotionEvent(e):pad!=null&&pad.motion(e)||super.onGenericMotionEvent(e);}
 @Override protected void onPause(){
  super.onPause();if(menu!=null)menu.pause();
  if(pad!=null)pad.reset();if(audio!=null)audio.pause();
 }
 @Override protected void onResume(){
  super.onResume();
  if(audio!=null)audio.resume();if(menu!=null)menu.resume();
 }
 @Override protected void onDestroy(){
  synchronized(this){destroyed=true;if(game!=null)game.destroyForcibly();}
  main.removeCallbacksAndMessages(null);
  if(graffitiGuideDialog!=null){
   graffitiGuideDialog.setOnDismissListener(null);
   graffitiGuideDialog.dismiss();
   graffitiGuideDialog=null;
  }
  if(menu!=null)menu.destroy();
  IssPresent.stop();if(audio!=null)audio.stop();
  worker.shutdown();super.onDestroy();
 }
}
