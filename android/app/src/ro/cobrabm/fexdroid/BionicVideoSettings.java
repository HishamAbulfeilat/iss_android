package ro.cobrabm.fexdroid;
import android.app.*;import android.widget.*;
final class BionicVideoSettings {
 static boolean fullscreen(android.content.SharedPreferences prefs){return prefs.getBoolean("fullscreen",false);}
 static String gameDisplayMode(android.content.SharedPreferences prefs){
  if(!fullscreen(prefs))return "fit";
  String mode=prefs.getString("display_mode","stretch");
  return "crop".equals(mode)?"crop":"stretch";
 }
 // FSR is a complete resolution mode. The manual resolution-patch
 // preference is preserved for FSR-off sessions but never used alongside FSR.
 static String fsrMode(android.content.SharedPreferences prefs){
  String value=prefs.getString("fsr3_mode",null);
  if("performance".equals(value)||"quality".equals(value))return value;
  // Migrate the old four-option spinner: preset=3 meant Performance.
  return prefs.getInt("fsr3_preset",1)==3?"performance":"quality";
 }
 static String fsrInternalResolution(android.content.SharedPreferences prefs){
  return "performance".equals(fsrMode(prefs))?"360p":"540p";
 }
 static String effectiveResolutionPatch(android.content.SharedPreferences prefs){
  return prefs.getBoolean("fsr3",false)?fsrInternalResolution(prefs):
    prefs.getString("internal_res","native");
 }
 static String fsrInputSize(android.content.SharedPreferences prefs){
  return "performance".equals(fsrMode(prefs))?"640x360":"960x540";
 }
 static int fsrEnginePreset(android.content.SharedPreferences prefs){
  // FSR3 fixed-render session: quality=1, performance=3.
  return "performance".equals(fsrMode(prefs))?3:1;
 }
 // 0–200%: 0–100% is the FidelityFX RCAS pass, 101–200% additionally
 // runs a post-FSR sharpening pass before game UI is composed. 25% preserves
 // the previous fixed Android setting for existing installs.
 static int fsrSharpnessPercent(android.content.SharedPreferences prefs){
  return Math.max(0,Math.min(200,prefs.getInt("fsr3_sharpness_pct",25)));
 }
 static boolean fsrSharpenEnabled(android.content.SharedPreferences prefs){
  return prefs.getBoolean("fsr3_sharpen_enabled",true);
 }
 static String fsrSharpnessValue(android.content.SharedPreferences prefs){
  return Float.toString(fsrSharpnessPercent(prefs)/100.0f);
 }
 static String outputResolution(android.content.SharedPreferences prefs){
  String patch=prefs.getString("internal_res","native");
  if(prefs.getBoolean("fsr3",false))return "1280x720";
  return "720p".equals(patch)?"1280x720":"900p".equals(patch)?"1600x900":"1920x1080";
 }
 static String internalPatchName(String key){
  switch(key){
   case "720p":return "Render resolution 1280x720";
   case "900p":return "Render resolution 1600x900";
   case "540p":return "Render resolution 960x540";
   case "480p":return "Render resolution 854x480";
   case "360p":return "Render resolution 640x360";
   default:return null;
  }
 }
 static LinearLayout content(Activity activity,int page){
  LinearLayout l=new LinearLayout(activity);l.setOrientation(1);l.setPadding(28,20,28,20);
  android.content.SharedPreferences prefs=activity.getSharedPreferences("iss",0);
  if(page!=2){
  Switch toggle=new Switch(activity);toggle.setText(AppLanguage.text("Fullscreen","Tela cheia"));
  toggle.setPadding(0,16,0,16);toggle.setChecked(fullscreen(prefs));l.addView(toggle);
  TextView status=new TextView(activity);l.addView(status);
  TextView label=new TextView(activity);label.setText(AppLanguage.text("Fullscreen mode","Modo de tela cheia"));label.setPadding(0,24,0,8);l.addView(label);
  RadioGroup modes=new RadioGroup(activity);modes.setOrientation(1);
  RadioButton stretch=new RadioButton(activity);stretch.setId(android.view.View.generateViewId());stretch.setText(AppLanguage.text("Stretch — no cropping","Esticar — sem cortes"));modes.addView(stretch);
  RadioButton crop=new RadioButton(activity);crop.setId(android.view.View.generateViewId());crop.setText(AppLanguage.text("Proportional — crop edges","Proporcional — cortar bordas"));modes.addView(crop);
  modes.check("crop".equals(prefs.getString("display_mode","stretch"))?crop.getId():stretch.getId());l.addView(modes);
  TextView hint=new TextView(activity);hint.setText(AppLanguage.text("Enable Fullscreen to choose a mode.","Ative a tela cheia para escolher um modo."));l.addView(hint);
  Runnable refresh=()->{boolean enabled=fullscreen(prefs);stretch.setEnabled(enabled);crop.setEnabled(enabled);modes.setAlpha(enabled?1f:0.4f);label.setAlpha(enabled?1f:0.4f);
   status.setText(enabled?AppLanguage.text("The mode selected below will be applied.","O modo selecionado abaixo será aplicado."):AppLanguage.text("Automatic 16:9 with Fullscreen off.","16:9 automático com a tela cheia desligada."));hint.setVisibility(enabled?android.view.View.GONE:android.view.View.VISIBLE);};
  toggle.setOnCheckedChangeListener((button,checked)->{prefs.edit().putBoolean("fullscreen",checked).apply();refresh.run();});
  modes.setOnCheckedChangeListener((group,id)->{if(fullscreen(prefs))prefs.edit().putString("display_mode",id==crop.getId()?"crop":"stretch").apply();});
  refresh.run();
  }
  if(page!=1){
  TextView fsrTitle=new TextView(activity);
  fsrTitle.setText(AppLanguage.text("FSR 3.1 temporal upscaling","Upscaling temporal FSR 3.1"));
  fsrTitle.setTextSize(18);fsrTitle.setPadding(0,16,0,8);l.addView(fsrTitle);
  CheckBox fsr=new CheckBox(activity);
  fsr.setText(AppLanguage.text("Enable FSR 3.1 (720p output, restart required)","Ativar FSR 3.1 (saída 720p, reinicie o jogo)"));
  fsr.setChecked(prefs.getBoolean("fsr3",false));l.addView(fsr);
  TextView fsrHint=new TextView(activity);
  fsrHint.setText(AppLanguage.text(
    "FSR automatically selects the internal resolution and reconstructs to 1280×720 before the HUD. Manual resolution patches are unavailable while FSR is enabled.",
    "O FSR seleciona automaticamente a resolução interna e reconstrói para 1280×720 antes da HUD. Patches manuais de resolução ficam indisponíveis com FSR ativado."));
  l.addView(fsrHint);
  TextView fsrPresetLabel=new TextView(activity);
  fsrPresetLabel.setText(AppLanguage.text("FSR 3.1 mode","Modo do FSR 3.1"));
  l.addView(fsrPresetLabel);
  Spinner fsrPreset=new Spinner(activity);
  String[] presetLabels={
    AppLanguage.text("Performance · 640×360 → 1280×720","Desempenho · 640×360 → 1280×720"),
    AppLanguage.text("Quality · 960×540 → 1280×720","Qualidade · 960×540 → 1280×720")};
  fsrPreset.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,presetLabels));
  fsrPreset.setSelection("performance".equals(fsrMode(prefs))?0:1);
  fsrPreset.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    if(pos==0||pos==1){
     String mode=pos==0?"performance":"quality";
     prefs.edit().putString("fsr3_mode",mode).putInt("fsr3_preset",pos==0?3:1).apply();
    }
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(fsrPreset);
  CheckBox edgeMask=new CheckBox(activity);
  edgeMask.setText(AppLanguage.text("FSR UI clarity (experimental)","Clareza da interface FSR (experimental)"));
  edgeMask.setChecked(prefs.getBoolean("fsr3_ui_edges",true));
  edgeMask.setOnCheckedChangeListener((button,checked)->
    prefs.edit().putBoolean("fsr3_ui_edges",checked).apply());
  l.addView(edgeMask);
  CheckBox sharpening=new CheckBox(activity);
  sharpening.setText(AppLanguage.text("Enable FSR sharpening","Ativar nitidez do FSR"));
  sharpening.setChecked(fsrSharpenEnabled(prefs));l.addView(sharpening);
  TextView sharpenLabel=new TextView(activity);l.addView(sharpenLabel);
  SeekBar sharpenSlider=new SeekBar(activity);
  sharpenSlider.setMax(200);
  sharpenSlider.setProgress(fsrSharpnessPercent(prefs));l.addView(sharpenSlider);
  TextView sharpenHint=new TextView(activity);
  sharpenHint.setText(AppLanguage.text(
   "0–100%: FSR RCAS sharpening. Above 100%: extra pass before the game HUD. High values can cause halos. Restart the game to apply.",
   "0–100%: nitidez RCAS do FSR. Acima de 100%: etapa extra antes da HUD do jogo. Valores altos podem causar halos. Reinicie o jogo para aplicar."));
  l.addView(sharpenHint);
  Runnable updateSharpText=()->sharpenLabel.setText(
    AppLanguage.text("Sharpening strength","Intensidade da nitidez")+": "+
    sharpenSlider.getProgress()+"%");
  sharpenSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
    if(fromUser)prefs.edit().putInt("fsr3_sharpness_pct",progress).apply();
    updateSharpText.run();
   }
   public void onStartTrackingTouch(SeekBar bar){}
   public void onStopTrackingTouch(SeekBar bar){}
  });
  sharpening.setOnCheckedChangeListener((button,checked)->{
   prefs.edit().putBoolean("fsr3_sharpen_enabled",checked).apply();
   sharpenSlider.setEnabled(checked&&fsr.isChecked());
   sharpenLabel.setEnabled(checked&&fsr.isChecked());
  });
  updateSharpText.run();
  TextView patchLabel=new TextView(activity);patchLabel.setText(AppLanguage.text("Internal patches — verified executable", "Patches internos — executável validado"));patchLabel.setTextSize(18);patchLabel.setPadding(0,16,0,8);l.addView(patchLabel);
  Spinner resolutionPatch=new Spinner(activity);
  String[] keys={"native","720p","900p","540p","480p","360p"};
  String[] modes={AppLanguage.text("Original (no patch)", "Original (sem patch)"),AppLanguage.text("1280 × 720 internal (720p)", "1280 × 720 interno (720p)"),AppLanguage.text("1600 × 900 internal (900p)", "1600 × 900 interno (900p)"),AppLanguage.text("960 × 540 internal (540p, experimental)", "960 × 540 interno (540p, experimental)"),AppLanguage.text("854 × 480 internal (480p, experimental)", "854 × 480 interno (480p, experimental)"),AppLanguage.text("640 × 360 internal (360p, experimental)", "640 × 360 interno (360p, experimental)")};
  resolutionPatch.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,modes));
  String selected=prefs.getString("internal_res","native");
  int selectedIndex=0;for(int i=0;i<keys.length;i++)if(keys[i].equals(selected))selectedIndex=i;
  resolutionPatch.setSelection(selectedIndex);
  resolutionPatch.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    if(!prefs.getBoolean("fsr3",false)&&pos>=0&&pos<keys.length)
     prefs.edit().putString("internal_res",keys[pos]).apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(resolutionPatch);
  TextView resolutionLocked=new TextView(activity);
  resolutionLocked.setText(AppLanguage.text(
    "FSR manages its own 360p/540p resolution patch automatically; disable FSR to select a manual resolution patch.",
    "O FSR aplica automaticamente seu patch de resolução 360p/540p; desative o FSR para escolher patches manuais de resolução."));
  l.addView(resolutionLocked);
  Runnable syncFsr=()->{
   boolean active=fsr.isChecked();
   fsrPreset.setEnabled(active);fsrPresetLabel.setEnabled(active);
   sharpening.setEnabled(active);
   edgeMask.setEnabled(active);
   sharpenSlider.setEnabled(active&&sharpening.isChecked());
   sharpenLabel.setEnabled(active&&sharpening.isChecked());
   sharpenHint.setEnabled(active);
   resolutionPatch.setEnabled(!active);
   resolutionPatch.setAlpha(active?0.4f:1f);
   resolutionLocked.setVisibility(active?android.view.View.VISIBLE:android.view.View.GONE);
  };
  fsr.setOnCheckedChangeListener((button,checked)->{
   prefs.edit().putBoolean("fsr3",checked).apply();
   syncFsr.run();
  });
  syncFsr.run();
  CheckBox blur=new CheckBox(activity);blur.setText(AppLanguage.text("Disable Motion Blur", "Desativar desfoque de movimento"));
  blur.setChecked(prefs.getBoolean("patch_motion_blur",false));
  blur.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_motion_blur",checked).apply());l.addView(blur);
  CheckBox dof=new CheckBox(activity);dof.setText(AppLanguage.text("Disable Depth of Field", "Desativar profundidade de campo"));
  dof.setChecked(prefs.getBoolean("patch_dof",false));
  dof.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_dof",checked).apply());l.addView(dof);
  CheckBox depth=new CheckBox(activity);depth.setText(AppLanguage.text("Disable Depth Blur", "Desativar desfoque de profundidade"));
  depth.setChecked(prefs.getBoolean("patch_depth_blur",false));
  depth.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_depth_blur",checked).apply());l.addView(depth);

  CheckBox shadows=new CheckBox(activity);shadows.setText(AppLanguage.text("Disable shadows", "Desativar sombras"));
  shadows.setChecked(prefs.getBoolean("patch_shadows",false));
  shadows.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_shadows",checked).apply());l.addView(shadows);

  }return l;
 }
 static void show(Activity a){showPage(a,2);}
 static void showVideo(Activity a){showPage(a,1);}
 private static void showPage(Activity a,int page){ScrollView scroll=new ScrollView(a);scroll.addView(content(a,page));new AlertDialog.Builder(a).setTitle(page==2?AppLanguage.text("Patches — next game start","Patches — próximo início"):AppLanguage.text("Video","Vídeo")).setView(scroll).setPositiveButton(AppLanguage.text("Done", "Concluir"),null).show();}
}
