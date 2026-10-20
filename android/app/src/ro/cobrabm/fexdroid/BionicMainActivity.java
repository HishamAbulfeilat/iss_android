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
 private BionicPkgInstaller pkg;
 private ProgressBar progress;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  getWindow().setStatusBarColor(0xff111318);getWindow().setNavigationBarColor(0xff111318);
  LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(28,32,28,28);l.setBackgroundColor(0xff111318);
  LinearLayout brand=new LinearLayout(this);brand.setGravity(android.view.Gravity.CENTER_VERTICAL);brand.setPadding(0,8,0,24);
  ImageView icon=new ImageView(this);icon.setImageResource(getResources().getIdentifier("infamous","drawable",getPackageName()));
  int size=Math.round(84*getResources().getDisplayMetrics().density);brand.addView(icon,new LinearLayout.LayoutParams(size,size));
  TextView title=new TextView(this);title.setText("inFAMOUS\nSECOND SON");title.setTextColor(0xffe9cf87);title.setTextSize(28);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);title.setPadding(24,0,0,0);brand.addView(title);l.addView(brand);
  TextView subtitle=new TextView(this);subtitle.setText("DAEDALUS  ·  ANDROID BIONIC");subtitle.setTextColor(0xff9095a1);subtitle.setPadding(0,0,0,16);l.addView(subtitle);
  status=new TextView(this);status.setTextSize(15);status.setTextColor(0xffdededc);status.setPadding(18,18,18,18);l.addView(status);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(android.view.View.GONE);l.addView(progress);
  pkg=new BionicPkgInstaller(this,(active,message)->{setBusy(active);status.setText(message);});
  section(l,"JOGO");button(l,"▶  Jogar",this::launch);
  button(l,"Selecionar pasta do jogo",this::choose);
  button(l,"Instalar jogo por PKG",()->pkg.pick(false));
  button(l,"Instalar atualização por PKG",()->pkg.pick(true));
  section(l,"VÍDEO E CONTROLES");
  button(l,"Vídeo e patches",()->BionicVideoSettings.show(this));
  File hud=new File(getFilesDir(),"issport/hud.ini");hud.getParentFile().mkdirs();HudConfig.migrateLegacy(this,hud);
  button(l,"HUD — FPS / GPU / CPU",()->HudControls.show(this,hud,null));
  button(l,"Geração de quadros — BloodFrame",()->FramegenSettings.show(this,null));
  button(l,"Controles e swipe pelo D-pad",()->BionicPlayerMenu.controlSettings(this,null,null));
  section(l,"SAVES E DIAGNÓSTICO");
  TextView saveHelp=new TextView(this);saveHelp.setText("Transfira os saves com o jogo fechado. ZIPs devem ser descriptografados e da mesma região.");saveHelp.setTextColor(0xff969aa3);l.addView(saveHelp);
  button(l,"Importar saves (ZIP)",()->chooseSave(true));button(l,"Exportar saves (ZIP)",()->chooseSave(false));
  button(l,"Exportar último log",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"iss-android.log"),3));
  TextView help=new TextView(this);help.setText("Grafite: L3 + R3 ativa o sensor virtual. ✕ sacode a lata, R2 pinta e o analógico esquerdo mira.\n\nO menu lateral pode ser aberto pela borda esquerda ou pelo botão Voltar durante o jogo.\n\nNenhum arquivo do jogo está incluído.");help.setTextColor(0xff8e939d);help.setPadding(0,24,0,0);l.addView(help);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.addView(l);setContentView(sc);refresh();
  if(!access())requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
 }
 private void section(LinearLayout l,String title){TextView t=new TextView(this);t.setText(title);t.setTextColor(0xffc6ad76);t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);t.setTextSize(13);t.setPadding(2,28,0,10);l.addView(t);}
 private void button(LinearLayout l,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);b.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.LEFT);b.setPadding(22,10,22,10);b.setTextColor(0xffe4e2d9);
  android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(text.contains("Jogar")?0xffb49350:0xff20242c);bg.setCornerRadius(12);if(text.contains("Jogar"))b.setTextColor(0xff111318);b.setBackground(bg);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.round(52*getResources().getDisplayMetrics().density));lp.topMargin=8;l.addView(b,lp);
  b.setOnClickListener(v->{if(!busy&&(pkg==null||!pkg.busy()))action.run();});actions.add(b);}
 private boolean access(){return Build.VERSION.SDK_INT>=30&&Environment.isExternalStorageManager()||checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
 private void refresh(){status.setText(getSharedPreferences("iss",0).getString("game_dir","Selecione a pasta do jogo extraído ou instale um PKG."));}
 private void choose(){if(!access()){requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);return;}startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),2);}
 private void launch(){if(busy)return;
  try(BionicSession.Guard ignored=BionicSession.acquire(getFilesDir())){}catch(IOException e){status.setText(e.getMessage());return;}
  String path=getSharedPreferences("iss",0).getString("game_dir","");if(!new File(path,"eboot.bin").canRead()||!new File(path,"art").isDirectory()||!new File(path,"art").canRead()){status.setText("Choose the inFAMOUS game folder containing eboot.bin and readable art/.");return;}
  setBusy(true);status.setText("Preparando runtime…");new Thread(()->{try{BionicRuntimeInstaller.install(this);runOnUiThread(()->{setBusy(false);refresh();startActivity(new Intent(this,BionicGameActivity.class).putExtra("game_dir",path));});}catch(Exception e){runOnUiThread(()->{setBusy(false);status.setText(e.toString());});}},"runtime-install").start();}
 private void setBusy(boolean value){busy=value;if(progress!=null)progress.setVisibility(value?android.view.View.VISIBLE:android.view.View.GONE);for(Button b:actions)b.setEnabled(!value);}
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
  if(req==FramegenSettings.IMPORT_DLL){if(result==RESULT_OK&&data!=null&&data.getData()!=null)FramegenSettings.importDll(this,data.getData(),null);return;}
  if(pkg!=null&&pkg.result(req,result,data))return;
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
 @Override public void onBackPressed(){if(busy){status.setText("Aguarde a operação terminar.");return;}super.onBackPressed();}
 @Override public void onRequestPermissionsResult(int req,String[] perms,int[] results){super.onRequestPermissionsResult(req,perms,results);if(req==1&&!access()&&Build.VERSION.SDK_INT>=30)new AlertDialog.Builder(this).setTitle("File access required").setMessage("Allow file access before selecting the game folder.").setPositiveButton("Open settings",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("Cancel",null).show();}
}
