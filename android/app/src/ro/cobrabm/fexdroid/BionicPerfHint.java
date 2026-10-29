package ro.cobrabm.fexdroid;
import android.app.Activity;
import android.os.Build;
import android.os.PerformanceHintManager;
import java.io.*;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Android Dynamic Performance Framework hint for the native game threads. The guest thread
 * spends much of each frame waiting on the GPU, so the CPU governor sees it half idle and lowers
 * clocks; a hint session with the frame-time target lets the scheduler keep them up. Reported
 * once a second from presented frames; off with Renderer corrections, logged in iss.log. */
final class BionicPerfHint {
 private static final String[] HOT={"iss-probe","GpuComm","VkRecorder","tlg:Present","shadPS4:Present","JobWorker"};
 private final Activity activity;private final Consumer<String> log;
 private PerformanceHintManager.Session session;private boolean failed;private long target,lastFrames=-1,lastTime;private int tries;
 BionicPerfHint(Activity a,Consumer<String> log){activity=a;this.log=log;}
 static boolean enabled(android.content.SharedPreferences p){return p.getBoolean("renderer_perf_hint",true);}
 /** Called about once a second with the presented-frame counter and the real-frame target. */
 void tick(long frames,int realLimit,int multiplier){
  if(failed||Build.VERSION.SDK_INT<31||realLimit<=0)return;
  long now=System.nanoTime(),want=1000000000L/realLimit;
  if(session==null){
   if(frames<=0)return;
   int[] tids=gameThreads();
   if(tids.length==0){if(++tries>30){failed=true;log.accept("Performance hint: game threads not found");}return;}
   try{
    PerformanceHintManager m=activity.getSystemService(PerformanceHintManager.class);
    session=m==null?null:m.createHintSession(tids,want);
   }catch(Exception e){failed=true;log.accept("Performance hint: not allowed ("+e.getMessage()+")");return;}
   if(session==null){failed=true;log.accept("Performance hint: not supported on this device");return;}
   target=want;lastFrames=frames;lastTime=now;log.accept("Performance hint: session for "+tids.length+" game threads, target "+(want/1000000.0)+" ms");return;
  }
  if(want!=target){session.updateTargetWorkDuration(want);target=want;}
  long shown=frames-lastFrames,elapsed=now-lastTime;lastFrames=frames;lastTime=now;
  long real=multiplier>=2?shown/multiplier:shown;
  if(real>0&&elapsed>0)try{session.reportActualWorkDuration(elapsed/real);}catch(Exception e){failed=true;log.accept("Performance hint stopped: "+e.getMessage());stop();}
 }
 void stop(){if(session!=null){try{session.close();}catch(Exception ignored){}session=null;}}
 /** Hot threads of the native game process: same UID, not this process. */
 private static int[] gameThreads(){
  String uid=Integer.toString(android.os.Process.myUid()),self=Integer.toString(android.os.Process.myPid());
  ArrayList<Integer> out=new ArrayList<>();File[] procs=new File("/proc").listFiles();if(procs==null)return new int[0];
  for(File d:procs){
   String pid=d.getName();if(pid.isEmpty()||!Character.isDigit(pid.charAt(0))||pid.equals(self)||!uid.equals(uidOf(d)))continue;
   File[] tasks=new File(d,"task").listFiles();if(tasks==null)continue;
   for(File t:tasks){
    String comm=read(new File(t,"comm"));if(comm==null)continue;
    for(String h:HOT)if(comm.contains(h)){try{out.add(Integer.parseInt(t.getName()));}catch(NumberFormatException ignored){}break;}
    if(out.size()>=16)break;
   }
  }
  int[] r=new int[out.size()];for(int i=0;i<r.length;i++)r[i]=out.get(i);return r;
 }
 private static String uidOf(File proc){
  try(BufferedReader in=new BufferedReader(new FileReader(new File(proc,"status")))){
   for(String l;(l=in.readLine())!=null;)if(l.startsWith("Uid:")){String[] f=l.trim().split("\\s+");return f.length>1?f[1]:null;}
  }catch(Exception ignored){}
  return null;
 }
 private static String read(File f){try(BufferedReader in=new BufferedReader(new FileReader(f))){return in.readLine();}catch(Exception e){return null;}}
}
