package ro.cobrabm.fexdroid;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.regex.*;

/** orbis-pkg-util, installed directly into the selected local directory. */
final class BionicPkgInstaller {
 static final int BASE=20, UPDATE=21, DESTINATION=22;
 interface State {void set(boolean busy,String message);}
 private final Activity activity;private final State state;
 private Uri selected;private boolean update;private volatile boolean running;
 BionicPkgInstaller(Activity a,State s){activity=a;state=s;}
 boolean busy(){return running||selected!=null;}
 void pick(boolean replacing){
  if(busy())return;
  if(replacing&&!new File(game(),"eboot.bin").isFile()){state.set(false,AppLanguage.text("Select the base game before installing an update.", "Selecione o jogo base antes da atualização."));return;}
  update=replacing;
  activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),replacing?UPDATE:BASE);
 }
 private File game(){return new File(activity.getSharedPreferences("iss",0).getString("game_dir",""));}
 boolean result(int request,int result,Intent data){
  if(request!=BASE&&request!=UPDATE&&request!=DESTINATION)return false;
  if(result!=Activity.RESULT_OK||data==null||data.getData()==null){selected=null;state.set(false,AppLanguage.text("Installation canceled.", "Instalação cancelada."));return true;}
  try{
   if(request==BASE||request==UPDATE){selected=data.getData();update=request==UPDATE;
    if(update){new AlertDialog.Builder(activity).setTitle(AppLanguage.text("Install update?", "Instalar atualização?")).setMessage(AppLanguage.text("Matching files will be replaced directly in the game folder. TITLE_ID will be validated before extraction.", "Os arquivos correspondentes serão substituídos diretamente na pasta do jogo. TITLE_ID será validado antes da extração.")).setNegativeButton(AppLanguage.text("Cancel", "Cancelar"),(d,w)->{selected=null;state.set(false,AppLanguage.text("Installation canceled.", "Instalação cancelada."));}).setPositiveButton(AppLanguage.text("Install", "Instalar"),(d,w)->install(Uri.fromFile(game()))).setOnCancelListener(d->{selected=null;state.set(false,AppLanguage.text("Installation canceled.", "Instalação cancelada."));}).show();}
    else activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),DESTINATION);
   }else{
    Uri dest=data.getData();try{activity.getContentResolver().takePersistableUriPermission(dest,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(SecurityException ignored){}
    install(dest);
   }
  }catch(Exception e){selected=null;state.set(false,e.getMessage());}return true;
 }
 private void install(Uri destination){
  if(running||selected==null)return;final Uri source=selected;selected=null;running=true;state.set(true,AppLanguage.text("Preparing PKG installation…", "Preparando instalação PKG…"));
  new Thread(()->{String message;
   try(BionicSession.Guard guard=BionicSession.acquire(activity.getFilesDir())){
    BionicRuntimeInstaller.install(activity);
    File pkg=local(source,false),dest=local(destination,true);
    if(!pkg.isFile()||!pkg.canRead())throw new IOException(AppLanguage.text("Local PKG unavailable. Choose a file from device storage.", "PKG local indisponível. Escolha um arquivo no armazenamento do aparelho."));
    if(!dest.isDirectory()||!dest.canWrite())throw new IOException(AppLanguage.text("No write permission for the selected folder.", "Sem permissão de escrita na pasta escolhida."));
    File[] entries=dest.listFiles();if(entries==null)throw new IOException(AppLanguage.text("Could not list the destination folder.", "Não foi possível listar a pasta de destino."));
    if(!update&&entries.length!=0)throw new IOException(AppLanguage.text("Select an empty folder to install the base game.", "Selecione uma pasta vazia para instalar o jogo base."));
    if(!update&&pkg.toPath().startsWith(dest.toPath()))throw new IOException(AppLanguage.text("The PKG must be outside the destination folder.", "O PKG deve ficar fora da pasta de destino."));
    try(DataInputStream in=new DataInputStream(new FileInputStream(pkg))){if(in.readInt()!=0x7f434e54)throw new IOException(AppLanguage.text("File is not a PS4 PKG.", "Arquivo não é um PKG de PS4."));}
    File helper=new File(activity.getFilesDir(),"issport/arm64/bin/orbis-pkg-util");
    String info=execute(helper,dest,"info",pkg.getPath());
    Matcher title=Pattern.compile("(?im)^\\s*Title ID:\\s*(CUSA[0-9]{5})\\s*$").matcher(info);
    if(!title.find())throw new IOException(AppLanguage.text("TITLE_ID missing from PKG header.", "TITLE_ID ausente no cabeçalho PKG."));String id=title.group(1).toUpperCase(java.util.Locale.US);
    if(update&&(!dest.equals(game().getCanonicalFile())||!id.equals(BionicSaveArchive.readTitle(dest))))throw new IOException(AppLanguage.text("The update does not match the selected game.", "A atualização não corresponde ao jogo selecionado."));
    stateOnUi(true,AppLanguage.text("Extracting PKG directly into the selected folder…", "Extraindo PKG diretamente na pasta escolhida…"));
    if(update)execute(helper,dest,"extract",pkg.getPath(),"--output",dest.getPath(),"--quiet","--force");
    else execute(helper,dest,"extract",pkg.getPath(),"--output",dest.getPath(),"--quiet");
    if(!new File(dest,"eboot.bin").isFile()||!new File(dest,"art").isDirectory()||!id.equals(BionicSaveArchive.readTitle(dest)))throw new IOException(AppLanguage.text("Extraction does not contain a complete inFAMOUS Second Son dump. Check eboot.bin, art/ and sce_sys/param.sfo.", "Extração não contém um dump completo de inFAMOUS Second Son. Confira eboot.bin, art/ e sce_sys/param.sfo."));
    if(!activity.getSharedPreferences("iss",0).edit().putString("game_dir",dest.getPath()).commit())throw new IOException(AppLanguage.text("Content extracted, but the folder could not be selected.", "Conteúdo extraído, mas não foi possível selecionar a pasta."));
    message=(update?AppLanguage.text("Update", "Atualização"):AppLanguage.text("Game", "Jogo"))+" "+id+AppLanguage.text(" installed at ", " instalado em ")+dest.getPath();
   }catch(Exception e){message=AppLanguage.text("Installation failed: ", "Falha na instalação: ")+e.getMessage();}
   running=false;stateOnUi(false,message);
  },"iss-pkg-install").start();
 }
 private void stateOnUi(boolean busy,String message){activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())state.set(busy,message);});}
 private String execute(File helper,File dest,String... args)throws Exception{
  java.util.ArrayList<String> command=new java.util.ArrayList<>();command.add("/system/bin/linker64");command.add(helper.getPath());java.util.Collections.addAll(command,args);
  ProcessBuilder builder=new ProcessBuilder(command).directory(dest).redirectErrorStream(true);builder.environment().put("LD_LIBRARY_PATH",new File(activity.getFilesDir(),"issport/arm64/lib").getPath());
  Process process=builder.start();StringBuilder text=new StringBuilder();
  try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))){String line;while((line=reader.readLine())!=null){if(text.length()<24000)text.append(line).append('\n');}}
  int exit=process.waitFor();if(exit!=0)throw new IOException(AppLanguage.text("PKG extractor (", "Extrator PKG (")+exit+"): "+text.substring(Math.max(0,text.length()-1600)));return text.toString();
 }
 private File local(Uri uri,boolean directory)throws Exception{
  if(directory&&"file".equals(uri.getScheme())){File file=new File(uri.getPath()).getCanonicalFile();if(file.equals(game().getCanonicalFile()))return file;throw new IOException(AppLanguage.text("Invalid destination.", "Destino inválido."));}
  String path=null;
  if("com.android.externalstorage.documents".equals(uri.getAuthority())){
   String id=directory?DocumentsContract.getTreeDocumentId(uri):DocumentsContract.getDocumentId(uri);int at=id.indexOf(':');if(at<1)throw new IOException(AppLanguage.text("Select local storage.", "Selecione armazenamento local."));
   String volume=id.substring(0,at);if(!volume.matches("[A-Za-z0-9_-]+"))throw new IOException(AppLanguage.text("Invalid volume.", "Volume inválido."));
   File root="primary".equalsIgnoreCase(volume)?Environment.getExternalStorageDirectory():new File("/storage",volume);
   File file=new File(root,id.substring(at+1)).getCanonicalFile();if(!file.toPath().startsWith(root.getCanonicalFile().toPath()))throw new IOException(AppLanguage.text("Invalid path.", "Caminho inválido."));return file;
  }
  if(!directory){try(android.database.Cursor c=activity.getContentResolver().query(uri,new String[]{"_data"},null,null,null)){if(c!=null&&c.moveToFirst())path=c.getString(0);}catch(Exception ignored){}
   if(path==null)try{String id=DocumentsContract.getDocumentId(uri);if(id.startsWith("raw:"))path=id.substring(4);}catch(Exception ignored){}
  }
  if(path!=null){File file=new File(path).getCanonicalFile();if(file.toPath().startsWith(new File("/storage").getCanonicalFile().toPath()))return file;}
  throw new IOException(AppLanguage.text("Select a local folder or PKG. Cloud files do not support direct installation.", "Selecione uma pasta ou PKG local. Arquivos em nuvem não permitem instalação direta."));
 }
}
