package ro.cobrabm.fexdroid;
import android.app.Activity;
import android.os.PowerManager;
import java.util.function.Consumer;

/** Sustained play on phones such as the Galaxy S25 Ultra: the SoC heats up within minutes at
 * 120 FPS, the GPU clocks drop and frame rate halves. Ask Android for sustained performance
 * where it is offered, log thermal changes to iss.log, and lower the real-frame limit one step
 * at SEVERE or worse until the device has cooled back to MODERATE. */
final class BionicThermal {
 private final Activity activity;private final Consumer<String> log;private final Runnable changed;
 private PowerManager power;private PowerManager.OnThermalStatusChangedListener listener;
 BionicThermal(Activity a,Consumer<String> log,Runnable changed){activity=a;this.log=log;this.changed=changed;}
 static String name(int s){
  switch(s){case PowerManager.THERMAL_STATUS_NONE:return "none";case PowerManager.THERMAL_STATUS_LIGHT:return "light";case PowerManager.THERMAL_STATUS_MODERATE:return "moderate";
   case PowerManager.THERMAL_STATUS_SEVERE:return "severe";case PowerManager.THERMAL_STATUS_CRITICAL:return "critical";case PowerManager.THERMAL_STATUS_EMERGENCY:return "emergency";
   case PowerManager.THERMAL_STATUS_SHUTDOWN:return "shutdown";default:return Integer.toString(s);}
 }
 void start(){
  power=(PowerManager)activity.getSystemService(Activity.POWER_SERVICE);if(power==null)return;
  if(power.isSustainedPerformanceModeSupported()){activity.getWindow().setSustainedPerformanceMode(true);log.accept("Thermal: sustained performance mode on");}
  else log.accept("Thermal: sustained performance mode not offered by this device");
  listener=s->{
   float headroom=power.getThermalHeadroom(10);
   int steps=s>=PowerManager.THERMAL_STATUS_SEVERE?1:s<=PowerManager.THERMAL_STATUS_MODERATE?0:BionicPerformanceSettings.thermalSteps;
   log.accept("Thermal status: "+name(s)+(Float.isNaN(headroom)?"":String.format(java.util.Locale.ROOT,", headroom %.2f",headroom)));
   if(steps!=BionicPerformanceSettings.thermalSteps){BionicPerformanceSettings.thermalSteps=steps;log.accept(steps>0?"Thermal: lowering the real-frame limit one step until the device cools":"Thermal: real-frame limit restored");changed.run();}
  };
  power.addThermalStatusListener(activity.getMainExecutor(),listener);
 }
 void stop(){
  if(power!=null&&listener!=null)power.removeThermalStatusListener(listener);
  listener=null;BionicPerformanceSettings.thermalSteps=0;
 }
}
