package ro.cobrabm.fexdroid;
import android.app.Activity;
import android.view.Display;
import android.view.WindowManager;
/** Ask both the Android window and the actual AHardwareBuffer child surface for high refresh. */
final class BionicDisplayRate {
 static float apply(Activity a){
  Display display=a.getWindowManager().getDefaultDisplay();Display.Mode current=display.getMode(),best=null;
  android.content.SharedPreferences p=a.getSharedPreferences("iss",0);int multiplier=p.getInt("fg_multiplier",0);
  float target=BionicPerformanceSettings.limit(p)*(multiplier>=2&&multiplier<=4?multiplier:1);
  for(Display.Mode mode:display.getSupportedModes()){
   if(mode.getPhysicalWidth()!=current.getPhysicalWidth()||mode.getPhysicalHeight()!=current.getPhysicalHeight())continue;
   float rate=mode.getRefreshRate();if(best==null || (rate>=target-.5f && (best.getRefreshRate()<target-.5f || rate<best.getRefreshRate())) || (best.getRefreshRate()<target-.5f && rate>best.getRefreshRate()))best=mode;
  }
  if(best==null)best=current;
  WindowManager.LayoutParams attrs=a.getWindow().getAttributes();
  if(attrs.preferredDisplayModeId!=best.getModeId() || attrs.preferredRefreshRate!=best.getRefreshRate()){
   attrs.preferredDisplayModeId=best.getModeId();attrs.preferredRefreshRate=best.getRefreshRate();a.getWindow().setAttributes(attrs);
  }
  return best.getRefreshRate();
 }
}
