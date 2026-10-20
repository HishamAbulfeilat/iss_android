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
 private LinearLayout pages, navigation; private int selectedPage;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  getWindow().setStatusBarColor(0xff150b0e);getWindow().setNavigationBarColor(0xff150b0e);
  android.widget.FrameLayout root=new android.widget.FrameLayout(this);
  ImageView art=new ImageView(this);art.setImageResource(getResources().getIdentifier("infamous","drawable",getPackageName()));art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setAlpha(.28f);root.addView(art,new android.widget.FrameLayout.LayoutParams(-1,-1));root.setBackgroundColor(0xff12090c);
  LinearLayout chrome=new LinearLayout(this);chrome.setOrientation(1);chrome.setPadding(dp(18),dp(12),dp(18),dp(12));root.addView(chrome,new android.widget.FrameLayout.LayoutParams(-1,-1));
  TextView title=label("inFAMOUS SECOND SON",26,0xfffff1e9);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);chrome.addView(title);
  chrome.addView(label("DAEDALUS  ·  ANDROID BIONIC",12,0xffff642d));
  status=label("",13,0xffddc3be);status.setPadding(0,dp(12),0,dp(8));chrome.addView(status);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(android.view.View.GONE);chrome.addView(progress);
  pages=new LinearLayout(this);pages.setOrientation(1);chrome.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
  navigation=new LinearLayout(this);navigation.setPadding(dp(4),dp(4),dp(4),dp(4));navigation.setBackground(background(0xff211015,0xff86362c));chrome.addView(navigation,new LinearLayout.LayoutParams(-1,-2));
  pkg=new BionicPkgInstaller(this,(active,message)->{setBusy(active);status.setText(message);});
  setContentView(root);showPage(saved==null?0:saved.getInt("page",0));refresh();
  if(!access())requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
 }
 private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private android.graphics.drawable.GradientDrawable background(int fill,int border){android.graphics.drawable.GradientDrawable b=new android.graphics.drawable.GradientDrawable();b.setColor(fill);b.setCornerRadius(dp(22));b.setStroke(dp(1),border);return b;}
 private TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
 private LinearLayout card(LinearLayout parent,String title){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(16),dp(10),dp(16),dp(18));l.setBackground(background(0xf2190d11,0xff70342d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(16);parent.addView(l,lp);section(l,title);return l;}
 private void showPage(int page){
  selectedPage=page;actions.clear();pages.removeAllViews();navigation.removeAllViews();
  String[] tabs={"⌂\nInício","☷\nPatches","⚙\nConfig.","ⓘ\nSobre"};
  for(int i=0;i<tabs.length;i++){final int index=i;Button b=new Button(this);b.setText(tabs[i]);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(i==page?0xfffff1e9:0xffc7aba8);b.setBackground(background(i==page?0xffad2532:0x00211015,0x00000000));navigation.addView(b,new LinearLayout.LayoutParams(0,dp(62),1));b.setOnClickListener(v->{if(!busy)showPage(index);});}
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setPadding(0,0,0,dp(18));sc.addView(content);pages.addView(sc,new LinearLayout.LayoutParams(-1,-1));
  if(page==0){
   LinearLayout game=card(content,"JOGO");button(game,"▶  JOGAR",this::launch);button(game,"Selecionar pasta do jogo",this::choose);button(game,"Instalar jogo por PKG",()->pkg.pick(false));button(game,"Instalar atualização por PKG",()->pkg.pick(true));
   LinearLayout video=card(content,"EXIBIÇÃO E DESEMPENHO");button(video,"FPS e VSync · "+BionicPerformanceSettings.summary(this),()->BionicPerformanceSettings.show(this,()->showPage(0)));button(video,"Vídeo e resolução",()->BionicVideoSettings.showVideo(this));
   button(video,"Geração de quadros — Bloodframe",()->FramegenSettings.show(this,null));
  }else if(page==1){LinearLayout patches=card(content,"PATCHES — inFAMOUS SECOND SON");patches.addView(BionicVideoSettings.content(this,2));
  }else if(page==2){
   LinearLayout display=card(content,"HUD E CONTROLES");File hud=new File(getFilesDir(),"issport/hud.ini");hud.getParentFile().mkdirs();HudConfig.migrateLegacy(this,hud);button(display,"HUD — FPS / GPU / CPU",()->HudControls.show(this,hud,null));button(display,"Controles e swipe pelo D-pad",()->BionicPlayerMenu.controlSettings(this,null,null));button(display,"Vídeo e driver Vulkan",()->BionicVideoSettings.showVideo(this));button(display,"FPS e sincronização",()->BionicPerformanceSettings.show(this,null));
   LinearLayout saves=card(content,"SAVES");saves.addView(label("Importe ou exporte seus saves com o jogo fechado.",14,0xffc7aba8));button(saves,"Exportar saves (ZIP)",()->chooseSave(false));button(saves,"Importar saves (ZIP)",()->chooseSave(true));
   LinearLayout logs=card(content,"LOGS");button(logs,"Exportar último log",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"iss-android.log"),3));
   LinearLayout fg=card(content,"LOSSLESS.DLL · BLOODFRAME");button(fg,"Importar Lossless.dll",()->FramegenSettings.chooseDll(this));button(fg,"Configurar Bloodframe",()->FramegenSettings.show(this,null));
  }else{
   LinearLayout about=card(content,"inFAMOUS SECOND SON — DAEDALUS FORK");about.addView(label("Port para Android com runtime Bionic.\n\nFork e modificações: erickmendes22\nDaedalus Emulator\n\nProjeto base: HayatoG/iss_pc\n\nNenhum arquivo do jogo está incluído.",17,0xfffff1e9));button(about,"YouTube · @daedalusemulator",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/@daedalusemulator"))));button(about,"GitHub · iss_android",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/erickmendes22/iss_android"))));
   LinearLayout help=card(content,"CONTROLES");help.addView(label("TOUCH: dois dedos para mover o carro.\nD-pad: deslize gradual; mantenha pressionado para continuar.\nGrafite: L3 + R3 ativa o sensor virtual; ✕ sacode a lata, R2 pinta e o analógico esquerdo mira.\n\nAbra o menu do jogo pela borda esquerda ou pelo botão Voltar.",16,0xffddc3be));
  }
 }
 @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("page",selectedPage);}
 private void section(LinearLayout l,String title){TextView t=label(title,15,0xffff642d);t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);t.setPadding(0,dp(8),0,dp(12));l.addView(t);}
 private void button(LinearLayout l,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);b.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.LEFT);b.setPadding(dp(14),dp(8),dp(14),dp(8));b.setTextColor(0xfffff1e9);b.setBackground(background(text.contains("JOGAR")?0xffb42635:0xff30171b,0xff79372c));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);b.setMinHeight(dp(54));l.addView(b,lp);b.setOnClickListener(v->{if(!busy&&(pkg==null||!pkg.busy()))action.run();});actions.add(b);}
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
