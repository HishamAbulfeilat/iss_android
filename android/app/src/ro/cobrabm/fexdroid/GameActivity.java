package ro.cobrabm.fexdroid;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.*;
public final class GameActivity extends Activity implements SurfaceHolder.Callback {
 private SurfaceView surface;private TextView status;private PadBridge pad;private AudioBridge audio;
 private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private Process game,xvfb;private volatile boolean destroyed,started,failed;private int shmid=-1;private String files,root,base;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);
  files=getFilesDir().getPath();root=files+"/rootfs";base=files+"/issport";pad=new PadBridge(new File(base,"android-pad.state"));audio=new AudioBridge(new File(base,"audio.fifo"));
  FrameLayout l=new FrameLayout(this);surface=new SurfaceView(this);l.addView(surface);l.addView(new TouchControllerOverlay(this,pad,false));status=new TextView(this);status.setText("Starting inFAMOUS Second Son…");status.setTextColor(0xffe0c891);status.setTextSize(18);status.setGravity(17);l.addView(status);setContentView(l);surface.getHolder().addCallback(this);
 }
 private Process process(String... args)throws IOException{
  ProcessBuilder p=new ProcessBuilder(args).redirectErrorStream(true);Map<String,String> e=p.environment();e.clear();e.put("PATH",root+"/usr/bin:"+root+"/bin:/system/bin");e.put("HOME",files+"/home/iss");e.put("LANG","C.UTF-8");e.put("TMPDIR",root+"/tmp");e.put("XDG_RUNTIME_DIR",root+"/tmp");e.put("FXD_FILES",files);e.put("FXD_ROOT",root);e.put("FXD_SHM_SOCKET",root+"/tmp/.fxshm/sock");e.put("FEXDROID_PRESENT",root+"/tmp/fxpresent.sock");e.put("LD_LIBRARY_PATH",base+"/lib:"+root+"/usr/lib/aarch64-linux-gnu:"+root+"/usr/lib");e.put("ISS_GAME_DIR",getIntent().getStringExtra("game_dir"));e.put("ISS_ANDROID_INPUT","1");
  if(args.length>1&&args[1].endsWith("/Xvfb"))e.put("LD_PRELOAD",root+"/usr/lib/fexdroid/libfxpath.so");return p.start();
 }
 @Override public void surfaceCreated(SurfaceHolder holder){if(shmid>=0){attach(holder);return;}if(started)return;started=true;
  worker.execute(()->{try{
   new File(root,"tmp/.fxshm").mkdirs();new File(root,"tmp/.X11-unix").mkdirs();new File(files,"home/iss").mkdirs();new File(root,"tmp/.X0-lock").delete();new File(root,"tmp/.X11-unix/X0").delete();
   try(PrintWriter passwd=new PrintWriter(new File(root,"etc/passwd"));PrintWriter group=new PrintWriter(new File(root,"etc/group"))){int uid=android.os.Process.myUid();passwd.println("root:x:0:0:root:/root:/bin/sh");passwd.println("user:x:"+uid+":"+uid+":inFAMOUS:"+files+"/home/iss:"+root+"/bin/sh");group.println("root:x:0:");group.println("user:x:"+uid+":");}
   audio.start();if(!audio.awaitReady(5000))throw new IOException("Audio bridge did not start.");String loader=root+"/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1";
   Process daemon=process(loader,root+"/usr/libexec/fxshmd","--dir",root+"/tmp/.fxshm");if(!daemon.waitFor(10,TimeUnit.SECONDS)){daemon.destroy();throw new IOException("Shared memory startup timed out.");}if(daemon.exitValue()!=0)throw new IOException("Shared memory service failed.");
   xvfb=process(loader,root+"/usr/bin/Xvfb",":0","-screen","0","1280x720x24","-shmem","-ac","-nolisten","tcp","-pn");main.postDelayed(()->{if(shmid<0&&!destroyed){if(xvfb!=null)xvfb.destroy();error("Display startup timed out.");}},15000);
   BufferedReader reader=new BufferedReader(new InputStreamReader(xvfb.getInputStream()));Pattern ready=Pattern.compile("screen 0 shmid (\\d+)");String line;
   while((line=reader.readLine())!=null&&!destroyed){Matcher m=ready.matcher(line);if(m.find()){shmid=Integer.parseInt(m.group(1));main.post(()->attach(surface.getHolder()));break;}}
   if(shmid<0)throw new IOException("Display failed to initialize.");Thread drain=new Thread(()->{try{while(reader.readLine()!=null){}}catch(IOException ignored){}},"X11-log");drain.setDaemon(true);drain.start();
  }catch(Exception e){error(e.toString());}});
 }
 private void attach(SurfaceHolder h){if(destroyed||!h.getSurface().isValid())return;String result=DisplayBridge.start(h.getSurface(),root+"/tmp/.fxshm/sock",shmid,60,root+"/tmp/fxpresent.sock");if(!result.startsWith("bridge running:")){error(result);return;}XInput.connect(0);
  if(game==null)worker.execute(()->{try{game=process("/system/bin/sh",base+"/run-iss.sh");try(InputStream in=game.getInputStream();OutputStream out=new FileOutputStream(new File(files,"iss.log"))){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}int code=game.waitFor();if(!destroyed)error("Game stopped ("+code+"). Export the log from the launcher.");}catch(Exception e){error(e.toString());}});poll();
 }
 private void poll(){if(destroyed)return;if(!failed&&DisplayBridge.frames()>0)status.setVisibility(View.GONE);main.postDelayed(this::poll,1000);}
 private void error(String s){failed=true;main.post(()->{status.setVisibility(View.VISIBLE);status.setText(s);});}
 @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}
 @Override public void surfaceDestroyed(SurfaceHolder h){DisplayBridge.stop();}
 @Override public boolean dispatchKeyEvent(KeyEvent e){return pad!=null&&pad.key(e)||super.dispatchKeyEvent(e);}
 @Override public boolean onGenericMotionEvent(MotionEvent e){return pad!=null&&pad.motion(e)||super.onGenericMotionEvent(e);}
 @Override protected void onPause(){super.onPause();if(pad!=null)pad.reset();if(audio!=null)audio.pause();}
 @Override protected void onResume(){super.onResume();if(audio!=null)audio.resume();}
 @Override protected void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);if(game!=null)game.destroy();if(xvfb!=null)xvfb.destroy();DisplayBridge.stop();if(audio!=null)audio.stop();worker.shutdownNow();super.onDestroy();}
}
