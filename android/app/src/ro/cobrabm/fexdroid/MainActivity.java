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
public final class MainActivity extends Activity {
 private TextView status; private boolean busy;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(32,48,32,32);
  TextView title=new TextView(this);title.setText("inFAMOUS SECOND SON\nDaedalus Android Fork\nLinux/glibc + FEX — Experimental");title.setTextSize(24);l.addView(title);
  status=new TextView(this);status.setPadding(0,24,0,24);l.addView(status);
  button(l,"Select game folder",this::choose);button(l,"Launch game",this::launch);
  button(l,"Export latest log",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"iss-android.log"),3));
  TextView help=new TextView(this);help.setText("Use an extracted game with update 1.07 merged into the same folder. Upstream reference: CUSA00223.\n\nGraffiti: L3 + R3 toggles sensor mode; Cross shakes, R2 sprays, left stick aims.\n\nBased on HayatoG/iss_pc, FEX-Emu and shadPS4. Android infrastructure adapted from God of War III — Daedalus Fork, Bloodborne-Android and fexdroid.\n\nNo game files included.");l.addView(help);ScrollView sc=new ScrollView(this);sc.addView(l);setContentView(sc);refresh();
  if(!access())requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
 }
 private void button(LinearLayout l,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());l.addView(b);}
 private boolean access(){return Build.VERSION.SDK_INT>=30&&Environment.isExternalStorageManager()||checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
 private void refresh(){status.setText(getSharedPreferences("iss",0).getString("game_dir","Select the extracted game folder."));}
 private void choose(){if(!access()){requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);return;}startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),2);}
 private void launch(){if(busy)return;String path=getSharedPreferences("iss",0).getString("game_dir","");if(!new File(path,"eboot.bin").canRead()||!new File(path,"art").isDirectory()||!new File(path,"art").canRead()){status.setText("Choose the inFAMOUS game folder containing eboot.bin and readable art/.");return;}
  busy=true;status.setText("Preparing runtime…");new Thread(()->{try{RuntimeInstaller.install(this);runOnUiThread(()->{busy=false;refresh();startActivity(new Intent(this,GameActivity.class).putExtra("game_dir",path));});}catch(Exception e){runOnUiThread(()->{busy=false;status.setText(e.toString());});}},"runtime-install").start();}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
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
