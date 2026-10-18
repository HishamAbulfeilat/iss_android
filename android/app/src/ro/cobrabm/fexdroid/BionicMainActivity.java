package ro.cobrabm.fexdroid;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
public final class BionicMainActivity extends Activity {
 private TextView status; private boolean busy;
 private final java.util.ArrayList<Button> actions=new java.util.ArrayList<>();
 private static final int IMPORT_SAVE=10, EXPORT_SAVE=11;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(32,48,32,32);
  TextView title=new TextView(this);title.setText("inFAMOUS SECOND SON\nDaedalus Android Fork\nAndroid Bionic + FEX — Experimental");title.setTextSize(24);l.addView(title);
  status=new TextView(this);status.setPadding(0,24,0,24);l.addView(status);
  android.content.SharedPreferences prefs=getSharedPreferences("iss",0);
  TextView settings=new TextView(this);settings.setText("Opções de vídeo e controles");settings.setTextSize(20);settings.setPadding(0,16,0,8);l.addView(settings);
  CheckBox fps=new CheckBox(this);fps.setText("Mostrar FPS (contador do renderizador)");fps.setChecked(prefs.getBoolean("show_fps",false));
  fps.setOnCheckedChangeListener((b,checked)->prefs.edit().putBoolean("show_fps",checked).apply());l.addView(fps);
  TextView driverLabel=new TextView(this);driverLabel.setText("Driver Vulkan:");l.addView(driverLabel);
  Spinner driver=new Spinner(this);
  String[] drivers={"Turnip Gen8 V37 Patched (padrão)", "Driver Vulkan do sistema", "Automático (detectar KGSL)"};
  driver.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,drivers));
  String selectedDriver=prefs.getString("bionic_driver","gen8");
  driver.setSelection("system".equals(selectedDriver)?1:"auto".equals(selectedDriver)?2:0);
  driver.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("bionic_driver",pos==1?"system":pos==2?"auto":"gen8").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(driver);
  TextView displayLabel=new TextView(this);displayLabel.setText("Exibição:");l.addView(displayLabel);
  Spinner display=new Spinner(this);
  String[] displayValues={"16:9 sem cortes (barras laterais)","Fullscreen sem cortes (esticar)","Fullscreen proporcional (cortar bordas)"};
  display.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,displayValues));
  String currentMode=prefs.getString("display_mode","fill");
  display.setSelection("fit".equals(currentMode)?0:"crop".equals(currentMode)?2:1);
  display.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("display_mode",pos==0?"fit":pos==2?"crop":"stretch").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(display);
  TextView resolutionLabel=new TextView(this);resolutionLabel.setText("Resolução de saída (não altera o patch interno do jogo):");l.addView(resolutionLabel);
  Spinner output=new Spinner(this);String[] outputValues={"1280 × 720 (720p)","1920 × 1080 (1080p)"};
  output.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,outputValues));
  output.setSelection("1280x720".equals(prefs.getString("output_res","1920x1080"))?0:1);
  output.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("output_res",pos==0?"1280x720":"1920x1080").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(output);
  TextView patchLabel=new TextView(this);patchLabel.setText("Patches internos — CUSA00004 01.00");patchLabel.setTextSize(18);patchLabel.setPadding(0,16,0,8);l.addView(patchLabel);
  Spinner resolutionPatch=new Spinner(this);
  String[] modes={"Original (sem patch)","1280 × 720 interno (720p)","1600 × 900 interno (900p)"};
  resolutionPatch.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes));
  String selected=prefs.getString("internal_res","native");
  resolutionPatch.setSelection("720p".equals(selected)?1:"900p".equals(selected)?2:0);
  resolutionPatch.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int pos,long id){
    prefs.edit().putString("internal_res",pos==1?"720p":pos==2?"900p":"native").apply();
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });l.addView(resolutionPatch);
  CheckBox blur=new CheckBox(this);blur.setText("Desativar Motion Blur");
  blur.setChecked(prefs.getBoolean("patch_motion_blur",false));
  blur.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_motion_blur",checked).apply());l.addView(blur);
  CheckBox dof=new CheckBox(this);dof.setText("Desativar Depth of Field");
  dof.setChecked(prefs.getBoolean("patch_dof",false));
  dof.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_dof",checked).apply());l.addView(dof);
  CheckBox depth=new CheckBox(this);depth.setText("Desativar Depth Blur");
  depth.setChecked(prefs.getBoolean("patch_depth_blur",false));
  depth.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("patch_depth_blur",checked).apply());l.addView(depth);
  TextView caveat=new TextView(this);caveat.setText("Os XMLs enviados não possuem patch de sombras. Fullscreen sem corte estica a proporção 16:9 do jogo, sem perder HUD.");l.addView(caveat);
  button(l,"Select game folder",this::choose);button(l,"Launch game",this::launch);
  TextView saveLabel=new TextView(this);saveLabel.setText("Saves — importar e exportar");saveLabel.setTextSize(20);saveLabel.setPadding(0,20,0,8);l.addView(saveLabel);
  TextView saveHelp=new TextView(this);saveHelp.setText("Feche o jogo antes de transferir saves. Importe um ZIP descriptografado do port ou Bachata, da mesma região do jogo. Os slots presentes no ZIP substituem os correspondentes; os demais são mantidos.");l.addView(saveHelp);
  button(l,"Importar saves (ZIP)",()->chooseSave(true));
  button(l,"Exportar saves (ZIP)",()->chooseSave(false));
  button(l,"Export latest log",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"iss-android.log"),3));
  TextView help=new TextView(this);help.setText("Use o dump inFAMOUS com eboot.bin e art/. CUSA00004 01.00 aceita os patches integrados.\n\nGraffiti: L3 + R3 toggles sensor mode; Cross shakes, R2 sprays, left stick aims.\n\nBased on HayatoG/iss_pc, FEX-Emu and shadPS4. Android infrastructure adapted from God of War III — Daedalus Fork, Bloodborne-Android and fexdroid.\n\nNo game files included.");l.addView(help);ScrollView sc=new ScrollView(this);sc.addView(l);setContentView(sc);refresh();
  if(!access())requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
 }
 private void button(LinearLayout l,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->{if(!busy)action.run();});actions.add(b);l.addView(b);}
 private boolean access(){return Build.VERSION.SDK_INT>=30&&Environment.isExternalStorageManager()||checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
 private void refresh(){status.setText(getSharedPreferences("iss",0).getString("game_dir","Select the extracted game folder."));}
 private void choose(){if(!access()){requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);return;}startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),2);}
 private void launch(){if(busy)return;
  try(BionicSession.Guard ignored=BionicSession.acquire(getFilesDir())){}catch(IOException e){status.setText(e.getMessage());return;}
  String path=getSharedPreferences("iss",0).getString("game_dir","");if(!new File(path,"eboot.bin").canRead()||!new File(path,"art").isDirectory()||!new File(path,"art").canRead()){status.setText("Choose the inFAMOUS game folder containing eboot.bin and readable art/.");return;}
  setBusy(true);status.setText("Preparing runtime…");new Thread(()->{try{BionicRuntimeInstaller.install(this);runOnUiThread(()->{setBusy(false);refresh();startActivity(new Intent(this,BionicGameActivity.class).putExtra("game_dir",path));});}catch(Exception e){runOnUiThread(()->{setBusy(false);status.setText(e.toString());});}},"runtime-install").start();}
 private void setBusy(boolean value){busy=value;for(Button b:actions)b.setEnabled(!value);}
 private File saves(){return new File(getFilesDir(),"issport/user/savedata");}
 private void chooseSave(boolean importing){
  if(busy)return;
  try(BionicSession.Guard ignored=BionicSession.acquire(getFilesDir())){
   BionicSaveArchive.recover(saves());
   if(importing)BionicSaveArchive.readTitle(new File(getSharedPreferences("iss",0).getString("game_dir","")));
   else if(!BionicSaveArchive.hasSaves(saves()))throw new IOException("Ainda não há saves para exportar.");
  }catch(Exception e){status.setText(e.getMessage());return;}
  Intent picker=new Intent(importing?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT);
  picker.addCategory(Intent.CATEGORY_OPENABLE);
  if(importing)picker.setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});
  else picker.setType("application/zip").putExtra(Intent.EXTRA_TITLE,"inFAMOUS-Saves-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.US).format(new java.util.Date())+".zip");
  setBusy(true);
  try{startActivityForResult(picker,importing?IMPORT_SAVE:EXPORT_SAVE);}catch(Exception e){setBusy(false);status.setText(e.getMessage());}
 }
 private void transferSave(Uri uri,boolean importing){
  setBusy(true);status.setText(importing?"Importando e validando saves…":"Exportando saves…");
  new Thread(()->{
   String message;
   try(BionicSession.Guard ignored=BionicSession.acquire(getFilesDir())){
    if(importing){
     String title=BionicSaveArchive.readTitle(new File(getSharedPreferences("iss",0).getString("game_dir","")));
     try(InputStream in=getContentResolver().openInputStream(uri)){
      if(in==null)throw new IOException("Não foi possível abrir o ZIP.");
      int slots=BionicSaveArchive.importSaves(saves(),in,title);message=slots+" slot(s) importado(s) para "+title+". Pronto para testar.";
     }
    }else try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
     if(out==null)throw new IOException("Não foi possível criar o ZIP.");
     BionicSaveArchive.exportSaves(saves(),out);message="Saves exportados com sucesso.";
    }
   }catch(Exception e){message="Falha na transferência: "+e.getMessage();}
   final String result=message;runOnUiThread(()->{setBusy(false);status.setText(result);});
  },"iss-save-transfer").start();
 }
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
  if(req==IMPORT_SAVE||req==EXPORT_SAVE){
   if(result==RESULT_OK&&data!=null&&data.getData()!=null)transferSave(data.getData(),req==IMPORT_SAVE);
   else {setBusy(false);refresh();}return;
  }
  if(result!=RESULT_OK||data==null||data.getData()==null)return;
  try{if(req==3){try(InputStream in=new FileInputStream(new File(getFilesDir(),"iss.log"));OutputStream out=getContentResolver().openOutputStream(data.getData())){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}return;}
   if(req!=2)return;Uri uri=data.getData();String id=DocumentsContract.getTreeDocumentId(uri);int colon=id.indexOf(':');if(colon<0)throw new IOException("Choose internal storage or an SD card.");
   String vol=id.substring(0,colon);File base="primary".equalsIgnoreCase(vol)?Environment.getExternalStorageDirectory():new File("/storage/"+vol);File dir=new File(base,id.substring(colon+1)).getCanonicalFile();
   if(!dir.toPath().startsWith(base.getCanonicalFile().toPath())||!new File(dir,"eboot.bin").canRead())throw new IOException("No readable eboot.bin. Check permission and the selected folder.");
   if(!new File(dir,"art").isDirectory()||!new File(dir,"art").canRead())throw new IOException("inFAMOUS game folder must contain accessible art/ resources.");
   try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
   getSharedPreferences("iss",0).edit().putString("game_dir",dir.getPath()).apply();refresh();
  }catch(Exception e){status.setText(e.toString());}}
 @Override public void onRequestPermissionsResult(int req,String[] perms,int[] results){super.onRequestPermissionsResult(req,perms,results);if(req==1&&!access()&&Build.VERSION.SDK_INT>=30)new AlertDialog.Builder(this).setTitle("File access required").setMessage("Allow file access before selecting the game folder.").setPositiveButton("Open settings",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("Cancel",null).show();}
}
