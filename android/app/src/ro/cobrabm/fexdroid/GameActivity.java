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
public final class GameActivity extends Activity implements SurfaceHolder.Callback {
 private SurfaceView surface;private FrameLayout frame;private TextView status;private PadBridge pad;private AudioBridge audio;private String screenMode;
 private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private Process game,xvfb;private volatile boolean destroyed,started,failed;private int shmid=-1;private String files,root,base;
 private int displayLogLines;
 private long attachedAtMs;
 private boolean directLogged, fallbackLogged;
 private synchronized void record(String message){
  if(files==null)return;
  try(FileOutputStream out=new FileOutputStream(new File(files,"iss.log"),true)){
   out.write(("[Android] "+message+"\\n").replace("\\n","\n").getBytes(StandardCharsets.UTF_8));
  }catch(IOException ignored){}
 }
 @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  screenMode=getSharedPreferences("iss",0).getString("display_mode","fill");
  if("fill".equals(screenMode))screenMode="stretch";
  getWindow().getDecorView().setSystemUiVisibility(5894);
  files=getFilesDir().getPath();root=files+"/rootfs";base=files+"/issport";
  try(FileOutputStream reset=new FileOutputStream(new File(files,"iss.log"),false)){
   reset.write(("ISS Android glibc startup log — "+getPackageName()+"\n").getBytes(StandardCharsets.UTF_8));
  }catch(IOException ignored){}
  record("Activity created; prepare display and audio (no game launched yet)");
  new File(base,"android-pad.state.guide").delete(); // an interrupted previous session may have left the dialog marker
  pad=new PadBridge(new File(base,"android-pad.state"));audio=new AudioBridge(new File(base,"audio.fifo"));
  frame=new FrameLayout(this);frame.setBackgroundColor(0xff000000);
  surface=new SurfaceView(this);frame.addView(surface);
  frame.addView(new TouchControllerOverlay(this,pad,false));
  status=new TextView(this);status.setText("Starting inFAMOUS Second Son…");status.setTextColor(0xffe0c891);status.setTextSize(18);status.setGravity(17);frame.addView(status);
  setContentView(frame);
  frame.addOnLayoutChangeListener((v,left,top,right,bottom,ol,ot,oright,obottom)->updateVideoSize());
  frame.post(this::updateVideoSize);
  surface.getHolder().addCallback(this);
 }
 private void updateVideoSize(){
  if(frame==null||surface==null)return;
  int w=frame.getWidth(),h=frame.getHeight();if(w<=0||h<=0)return;
  // Fit preserves 16:9 with side bars, Stretch fills every pixel without
  // cropping the HUD, and Crop zooms while retaining aspect ratio (opt in).
  float scale="crop".equals(screenMode)?Math.max(w/1280f,h/720f):Math.min(w/1280f,h/720f);
  int vw="stretch".equals(screenMode)?w:Math.max(1,Math.round(1280f*scale));
  int vh="stretch".equals(screenMode)?h:Math.max(1,Math.round(720f*scale));
  FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)surface.getLayoutParams();
  if(p.width!=vw||p.height!=vh||p.gravity!=Gravity.CENTER){
   p.width=vw;p.height=vh;p.gravity=Gravity.CENTER;surface.setLayoutParams(p);
  }
 }
 private Process process(String... args)throws IOException{
  ProcessBuilder p=new ProcessBuilder(args).redirectErrorStream(true);Map<String,String> e=p.environment();e.clear();e.put("PATH",root+"/usr/bin:"+root+"/bin:/system/bin");e.put("HOME",files+"/home/iss");e.put("LANG","C.UTF-8");e.put("TMPDIR",root+"/tmp");e.put("XDG_RUNTIME_DIR",root+"/tmp");e.put("FXD_FILES",files);e.put("FXD_ROOT",root);e.put("FXD_SHM_SOCKET",root+"/tmp/.fxshm/sock");e.put("FEXDROID_PRESENT",root+"/tmp/fxpresent.sock");e.put("LD_LIBRARY_PATH",base+"/lib:"+root+"/usr/lib/aarch64-linux-gnu:"+root+"/usr/lib:"+root+"/usr/lib/aarch64-linux-gnu/pulseaudio");e.put("ISS_GAME_DIR",getIntent().getStringExtra("game_dir"));e.put("ISS_ANDROID_INPUT","1");
  android.content.SharedPreferences prefs=getSharedPreferences("iss",0);
  e.put("TLG_SHOW_FPS",prefs.getBoolean("show_fps",false)?"1":"0");
  e.put("TLG_ANDROID_FPS_ONLY","1");
  String output=prefs.getString("output_res","1920x1080");
  e.put("TLG_OUTPUT_RES","1280x720".equals(output)?"1280x720":"1920x1080");
  java.util.ArrayList<String> enabled=new java.util.ArrayList<>();
  String internal=prefs.getString("internal_res","native");
  if("720p".equals(internal))enabled.add("Render resolution 1280x720");
  else if("900p".equals(internal))enabled.add("Render resolution 1600x900");
  if(prefs.getBoolean("patch_motion_blur",false))enabled.add("Disable motion blur");
  if(prefs.getBoolean("patch_dof",false))enabled.add("Disable depth of field");
  if(prefs.getBoolean("patch_depth_blur",false))enabled.add("Disable depth blur");
  e.put("ISS_PATCHES",android.text.TextUtils.join(";",enabled));
  // Both experiments were rejected on-device (BP09 worsened graphics).
  // Force their OFF state even for users with old persisted preferences.
  e.put("TLG_BP09","0");
  e.put("TLG_BC_TINY","0");
  if(args.length>1&&args[1].endsWith("/Xvfb"))e.put("LD_PRELOAD",root+"/usr/lib/fexdroid/libfxpath.so");return p.start();
 }
 @Override public void surfaceCreated(SurfaceHolder holder){if(shmid>=0){attach(holder);return;}if(started)return;started=true;
  worker.execute(()->{try{
   record("Preparing Xvfb filesystem, app UID and audio bridge");
   new File(root,"tmp/.fxshm").mkdirs();new File(root,"tmp/.X11-unix").mkdirs();new File(files,"home/iss").mkdirs();new File(root,"tmp/.X0-lock").delete();new File(root,"tmp/.X11-unix/X0").delete();
   try(PrintWriter passwd=new PrintWriter(new File(root,"etc/passwd"));PrintWriter group=new PrintWriter(new File(root,"etc/group"))){int uid=android.os.Process.myUid();passwd.println("root:x:0:0:root:/root:/bin/sh");passwd.println("user:x:"+uid+":"+uid+":inFAMOUS:"+files+"/home/iss:"+root+"/bin/sh");group.println("root:x:0:");group.println("user:x:"+uid+":");}
   audio.start();if(!audio.awaitReady(5000))throw new IOException("Audio bridge did not start.");record("Audio bridge ready");String loader=root+"/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1";
   record("Starting shared-memory daemon");
   Process daemon=process(loader,root+"/usr/libexec/fxshmd","--dir",root+"/tmp/.fxshm");
   if(!daemon.waitFor(10,TimeUnit.SECONDS)){daemon.destroy();throw new IOException("Shared memory startup timed out.");}
   if(daemon.exitValue()!=0)throw new IOException("Shared memory service failed ("+daemon.exitValue()+"): "+RuntimeInstaller.read(daemon.getInputStream()).length+" bytes output");
   record("Shared-memory daemon ready");
   record("Starting Xvfb glibc display");
   xvfb=process(loader,root+"/usr/bin/Xvfb",":0","-screen","0","1280x720x24","-shmem","-ac","-nolisten","tcp","-pn");main.postDelayed(()->{if(shmid<0&&!destroyed){if(xvfb!=null)xvfb.destroy();error("Display startup timed out.");}},15000);
   BufferedReader reader=new BufferedReader(new InputStreamReader(xvfb.getInputStream()));Pattern ready=Pattern.compile("screen 0 shmid (\\d+)");String line;
   while((line=reader.readLine())!=null&&!destroyed){
    if(displayLogLines++<160)record("Xvfb: "+line.substring(0,Math.min(line.length(),512)));
    Matcher m=ready.matcher(line);
    if(m.find()){shmid=Integer.parseInt(m.group(1));record("Xvfb ready, shmid="+shmid);main.post(()->attach(surface.getHolder()));break;}
   }
   if(shmid<0)throw new IOException("Display failed to initialize.");Thread drain=new Thread(()->{try{while(reader.readLine()!=null){}}catch(IOException ignored){}},"X11-log");drain.setDaemon(true);drain.start();
  }catch(Exception e){error(e.toString());}});
 }
 private void attach(SurfaceHolder h){if(destroyed||!h.getSurface().isValid())return;
  record("Attaching Xvfb shared memory to Android Surface");
  String result=DisplayBridge.start(h.getSurface(),root+"/tmp/.fxshm/sock",shmid,60,root+"/tmp/fxpresent.sock");
  record("DisplayBridge result: "+result);
  if(!result.startsWith("bridge running:")){error(result);return;}
  attachedAtMs=android.os.SystemClock.uptimeMillis();
  directLogged=false;fallbackLogged=false;
  XInput.connect(0);
  if(game==null)worker.execute(()->{try{
   record("Running game preflight /system/bin/sh -> explicit glibc loader");
   game=process("/system/bin/sh",base+"/run-iss.sh");
   try(InputStream in=game.getInputStream();OutputStream out=new FileOutputStream(new File(files,"iss.log"),true)){
    byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);
   }
   int code=game.waitFor();record("Game process exit="+code);
   if(!destroyed)error("Game stopped ("+code+"). Export the log from the launcher.");
  }catch(Exception e){error(e.toString());}});poll();
 }
 private void poll(){
  if(destroyed)return;
  long frames=DisplayBridge.frames();
  if(!failed&&frames>0)status.setVisibility(View.GONE);
  long direct=DisplayBridge.directFrames();
  if(direct>0&&!directLogged){
   directLogged=true;
   record("DIRECT_PRESENT_ACTIVE: "+direct+" complete Mesa frames, total displayed="+frames);
  }else if(direct==0&&!fallbackLogged&&frames>0
           &&android.os.SystemClock.uptimeMillis()-attachedAtMs>=60000){
   fallbackLogged=true;
   record("WARNING: no direct Mesa frames after 60s; Xvfb polling fallback can cause horizontal tearing");
  }
  main.postDelayed(this::poll,1000);
 }
 private void error(String s){failed=true;record("ERROR: "+s);main.post(()->{status.setVisibility(View.VISIBLE);status.setText(s);});}
 @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}
 @Override public void surfaceDestroyed(SurfaceHolder h){DisplayBridge.stop();}
 @Override public boolean dispatchKeyEvent(KeyEvent e){return pad!=null&&pad.key(e)||super.dispatchKeyEvent(e);}
 @Override public boolean onGenericMotionEvent(MotionEvent e){return pad!=null&&pad.motion(e)||super.onGenericMotionEvent(e);}
 @Override protected void onPause(){super.onPause();if(pad!=null)pad.reset();if(audio!=null)audio.pause();}
 @Override protected void onResume(){super.onResume();if(audio!=null)audio.resume();}
 @Override protected void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);if(game!=null)game.destroy();if(xvfb!=null)xvfb.destroy();DisplayBridge.stop();if(audio!=null)audio.stop();worker.shutdownNow();super.onDestroy();}
}
