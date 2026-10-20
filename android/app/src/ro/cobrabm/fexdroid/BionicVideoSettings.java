package ro.cobrabm.fexdroid;
import android.app.*;import android.widget.*;
final class BionicVideoSettings {
 static void show(Activity activity){
  LinearLayout l=new LinearLayout(activity);l.setOrientation(1);l.setPadding(28,20,28,20);
  android.content.SharedPreferences prefs=activity.getSharedPreferences("iss",0);
  TextView driverLabel=new TextView(activity);driverLabel.setText("Driver Vulkan:");l.addView(driverLabel);
  Spinner driver=new Spinner(activity);
  String[] drivers={"Turnip Gen8 V37 Patched (padrão)", "Driver Vulkan do sistema", "Automático (detectar KGSL)"};
  driver.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,drivers));
  String selectedDriver=prefs.getString("bionic_driver","gen8");
  driver.setSelection("system".equals(selectedDriver)?1:"auto".equals(selectedDriver)?2:0);
  driver.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("bionic_driver",pos==1?"system":pos==2?"auto":"gen8").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(driver);
  TextView displayLabel=new TextView(activity);displayLabel.setText("Exibição:");l.addView(displayLabel);
  Spinner display=new Spinner(activity);
  String[] displayValues={"16:9 sem cortes (barras laterais)","Fullscreen sem cortes (esticar)","Fullscreen proporcional (cortar bordas)"};
  display.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,displayValues));
  String currentMode=prefs.getString("display_mode","fill");
  display.setSelection("fit".equals(currentMode)?0:"crop".equals(currentMode)?2:1);
  display.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("display_mode",pos==0?"fit":pos==2?"crop":"stretch").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(display);
  TextView resolutionLabel=new TextView(activity);resolutionLabel.setText("Resolução de saída (não altera o patch interno do jogo):");l.addView(resolutionLabel);
  Spinner output=new Spinner(activity);String[] outputValues={"1280 × 720 (720p)","1920 × 1080 (1080p)"};
  output.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,outputValues));
  output.setSelection("1280x720".equals(prefs.getString("output_res","1920x1080"))?0:1);
  output.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("output_res",pos==0?"1280x720":"1920x1080").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(output);
  TextView patchLabel=new TextView(activity);patchLabel.setText("Patches internos — CUSA00004 01.00");patchLabel.setTextSize(18);patchLabel.setPadding(0,16,0,8);l.addView(patchLabel);
  Spinner resolutionPatch=new Spinner(activity);
  String[] modes={"Original (sem patch)","1280 × 720 interno (720p)","1600 × 900 interno (900p)"};
  resolutionPatch.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,modes));
  String selected=prefs.getString("internal_res","native");
  resolutionPatch.setSelection("720p".equals(selected)?1:"900p".equals(selected)?2:0);
  resolutionPatch.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("internal_res",pos==1?"720p":pos==2?"900p":"native").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(resolutionPatch);
  CheckBox blur=new CheckBox(activity);blur.setText("Desativar Motion Blur");
  blur.setChecked(prefs.getBoolean("patch_motion_blur",false));
  blur.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_motion_blur",checked).apply());l.addView(blur);
  CheckBox dof=new CheckBox(activity);dof.setText("Desativar Depth of Field");
  dof.setChecked(prefs.getBoolean("patch_dof",false));
  dof.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_dof",checked).apply());l.addView(dof);
  CheckBox depth=new CheckBox(activity);depth.setText("Desativar Depth Blur");
  depth.setChecked(prefs.getBoolean("patch_depth_blur",false));
  depth.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_depth_blur",checked).apply());l.addView(depth);

  ScrollView scroll=new ScrollView(activity);scroll.addView(l);
  new AlertDialog.Builder(activity).setTitle("Vídeo e patches — próximo início").setView(scroll).setPositiveButton("Concluir",null).show();
 }
}
