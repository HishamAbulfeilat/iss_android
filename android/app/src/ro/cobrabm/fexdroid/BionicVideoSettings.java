package ro.cobrabm.fexdroid;
import android.app.*;import android.widget.*;
final class BionicVideoSettings {
 static boolean fullscreen(android.content.SharedPreferences prefs){return prefs.getBoolean("fullscreen",false);}
 static String gameDisplayMode(android.content.SharedPreferences prefs){
  if(!fullscreen(prefs))return "fit";
  String mode=prefs.getString("display_mode","stretch");
  return "crop".equals(mode)?"crop":"fit".equals(mode)?"fit":"stretch";
 }
 static void fullscreenOption(Activity activity,LinearLayout parent){
  android.content.SharedPreferences prefs=activity.getSharedPreferences("iss",0);
  Switch toggle=new Switch(activity);toggle.setText(AppLanguage.text("Fullscreen","Tela cheia (Fullscreen)"));
  toggle.setChecked(fullscreen(prefs));toggle.setPadding(0,16,0,16);
  toggle.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("fullscreen",checked).apply());
  parent.addView(toggle);
 }
 static LinearLayout content(Activity activity,int page){
  LinearLayout l=new LinearLayout(activity);l.setOrientation(1);l.setPadding(28,20,28,20);
  android.content.SharedPreferences prefs=activity.getSharedPreferences("iss",0);
  if(page!=2){
  fullscreenOption(activity,l);
  BionicDriverSettings.add(activity,l);
  TextView displayLabel=new TextView(activity);displayLabel.setText(AppLanguage.text("Image fit when Fullscreen is enabled:", "Ajuste da imagem com Fullscreen ativado:"));l.addView(displayLabel);
  Spinner display=new Spinner(activity);
  String[] displayValues={AppLanguage.text("16:9 without cropping (side bars)", "16:9 sem cortes (barras laterais)"),AppLanguage.text("Fullscreen without cropping (stretch)", "Fullscreen sem cortes (esticar)"),AppLanguage.text("Fullscreen with aspect ratio (crop edges)", "Fullscreen proporcional (cortar bordas)")};
  display.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,displayValues));
  String currentMode=prefs.getString("display_mode","stretch");
  display.setSelection("fit".equals(currentMode)?0:"crop".equals(currentMode)?2:1);
  display.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("display_mode",pos==0?"fit":pos==2?"crop":"stretch").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(display);
  TextView resolutionLabel=new TextView(activity);resolutionLabel.setText(AppLanguage.text("Output resolution (does not change the internal game patch):", "Resolução de saída (não altera o patch interno do jogo):"));l.addView(resolutionLabel);
  Spinner output=new Spinner(activity);String[] outputValues={"1280 × 720 (720p)","1920 × 1080 (1080p)"};
  output.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,outputValues));
  output.setSelection("1280x720".equals(prefs.getString("output_res","1920x1080"))?0:1);
  output.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("output_res",pos==0?"1280x720":"1920x1080").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(output);
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
 static void show(Activity a){showPage(a,0);}
 static void showVideo(Activity a){showPage(a,1);}
 private static void showPage(Activity a,int page){ScrollView scroll=new ScrollView(a);scroll.addView(content(a,page));new AlertDialog.Builder(a).setTitle(AppLanguage.text("Video — next game start", "Vídeo — próximo início")).setView(scroll).setPositiveButton(AppLanguage.text("Done", "Concluir"),null).show();}
}
