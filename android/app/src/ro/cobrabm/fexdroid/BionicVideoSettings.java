package ro.cobrabm.fexdroid;
import android.app.*;import android.widget.*;
final class BionicVideoSettings {
 static boolean fullscreen(android.content.SharedPreferences prefs){return prefs.getBoolean("fullscreen",false);}
 static String gameDisplayMode(android.content.SharedPreferences prefs){
  if(!fullscreen(prefs))return "fit";
  String mode=prefs.getString("display_mode","stretch");
  return "crop".equals(mode)?"crop":"stretch";
 }
 static String outputResolution(android.content.SharedPreferences prefs){
  String patch=prefs.getString("internal_res","native");
  return "720p".equals(patch)?"1280x720":"900p".equals(patch)?"1600x900":"1920x1080";
 }
 static LinearLayout content(Activity activity,int page){
  LinearLayout l=new LinearLayout(activity);l.setOrientation(1);l.setPadding(28,20,28,20);
  android.content.SharedPreferences prefs=activity.getSharedPreferences("iss",0);
  if(page!=2){
  Switch toggle=new Switch(activity);toggle.setText(AppLanguage.text("Fullscreen","Tela cheia (Fullscreen)"));
  toggle.setPadding(0,16,0,16);toggle.setChecked(fullscreen(prefs));l.addView(toggle);
  TextView status=new TextView(activity);l.addView(status);
  TextView label=new TextView(activity);label.setText(AppLanguage.text("Fullscreen mode","Modo de Fullscreen"));label.setPadding(0,24,0,8);l.addView(label);
  RadioGroup modes=new RadioGroup(activity);modes.setOrientation(1);
  RadioButton stretch=new RadioButton(activity);stretch.setId(android.view.View.generateViewId());stretch.setText(AppLanguage.text("Stretch — no cropping","Esticar — sem cortes"));modes.addView(stretch);
  RadioButton crop=new RadioButton(activity);crop.setId(android.view.View.generateViewId());crop.setText(AppLanguage.text("Proportional — crop edges","Proporcional — cortar bordas"));modes.addView(crop);
  modes.check("crop".equals(prefs.getString("display_mode","stretch"))?crop.getId():stretch.getId());l.addView(modes);
  TextView hint=new TextView(activity);hint.setText(AppLanguage.text("Enable Fullscreen to choose a mode.","Ative Fullscreen para escolher o modo."));l.addView(hint);
  Runnable refresh=()->{boolean enabled=fullscreen(prefs);stretch.setEnabled(enabled);crop.setEnabled(enabled);modes.setAlpha(enabled?1f:0.4f);label.setAlpha(enabled?1f:0.4f);
   status.setText(enabled?AppLanguage.text("The mode selected below will be applied.","O modo selecionado abaixo será aplicado."):AppLanguage.text("Automatic 16:9 with Fullscreen off.","16:9 automático com Fullscreen desligado."));hint.setVisibility(enabled?android.view.View.GONE:android.view.View.VISIBLE);};
  toggle.setOnCheckedChangeListener((button,checked)->{prefs.edit().putBoolean("fullscreen",checked).apply();refresh.run();});
  modes.setOnCheckedChangeListener((group,id)->{if(fullscreen(prefs))prefs.edit().putString("display_mode",id==crop.getId()?"crop":"stretch").apply();});
  refresh.run();
  }
  if(page!=1){
  TextView patchLabel=new TextView(activity);patchLabel.setText(AppLanguage.text("Internal patches — CUSA00004 01.00", "Patches internos — CUSA00004 01.00"));patchLabel.setTextSize(18);patchLabel.setPadding(0,16,0,8);l.addView(patchLabel);
  Spinner resolutionPatch=new Spinner(activity);
  String[] modes={AppLanguage.text("Original (no patch)", "Original (sem patch)"),AppLanguage.text("1280 × 720 internal (720p)", "1280 × 720 interno (720p)"),AppLanguage.text("1600 × 900 internal (900p)", "1600 × 900 interno (900p)")};
  resolutionPatch.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,modes));
  String selected=prefs.getString("internal_res","native");
  resolutionPatch.setSelection("720p".equals(selected)?1:"900p".equals(selected)?2:0);
  resolutionPatch.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("internal_res",pos==1?"720p":pos==2?"900p":"native").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(resolutionPatch);
  CheckBox blur=new CheckBox(activity);blur.setText(AppLanguage.text("Disable Motion Blur", "Desativar Motion Blur"));
  blur.setChecked(prefs.getBoolean("patch_motion_blur",false));
  blur.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_motion_blur",checked).apply());l.addView(blur);
  CheckBox dof=new CheckBox(activity);dof.setText(AppLanguage.text("Disable Depth of Field", "Desativar Depth of Field"));
  dof.setChecked(prefs.getBoolean("patch_dof",false));
  dof.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_dof",checked).apply());l.addView(dof);
  CheckBox depth=new CheckBox(activity);depth.setText(AppLanguage.text("Disable Depth Blur", "Desativar Depth Blur"));
  depth.setChecked(prefs.getBoolean("patch_depth_blur",false));
  depth.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_depth_blur",checked).apply());l.addView(depth);

  }return l;
 }
 static void show(Activity a){showPage(a,2);}
 static void showVideo(Activity a){showPage(a,1);}
 private static void showPage(Activity a,int page){ScrollView scroll=new ScrollView(a);scroll.addView(content(a,page));new AlertDialog.Builder(a).setTitle(page==2?AppLanguage.text("Patches — next game start","Patches — próximo início"):AppLanguage.text("Video","Vídeo")).setView(scroll).setPositiveButton(AppLanguage.text("Done", "Concluir"),null).show();}
}
