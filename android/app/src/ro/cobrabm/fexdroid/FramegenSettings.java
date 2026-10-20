package ro.cobrabm.fexdroid;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Live state shared with the native Bloodframe presenter; DLL supplied by the player. */
final class FramegenSettings {
 static final int IMPORT_DLL=14;
 private static final java.util.concurrent.atomic.AtomicBoolean importing=new java.util.concurrent.atomic.AtomicBoolean();
 static File base(Activity a){return new File(a.getFilesDir(),"issport");}
 static synchronized void write(Activity a)throws IOException{
  android.content.SharedPreferences p=a.getSharedPreferences("iss",0);
  File dir=base(a);dir.mkdirs();File temp=new File(dir,"framegen.state.tmp");
  String text="multiplier="+p.getInt("fg_multiplier",0)+"\nflow="+p.getFloat("fg_flow",0.75f)+"\nperformance="+(p.getBoolean("fg_performance",true)?1:0)+"\nfp16="+(p.getBoolean("fg_fp16",true)?1:0)+"\n";
  try(FileOutputStream out=new FileOutputStream(temp)){out.write(text.getBytes(StandardCharsets.US_ASCII));out.getFD().sync();}
  if(!temp.renameTo(new File(dir,"framegen.state")))throw new IOException(AppLanguage.text("Could not apply frame generation.", "Não foi possível aplicar a geração de quadros."));
 }
 static void chooseDll(Activity a){
  a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),IMPORT_DLL);
 }
 static void importDll(Activity a,Uri uri,Runnable done){
  if(!importing.compareAndSet(false,true))return;
  new Thread(()->{String error=null;File temp=new File(base(a),"Lossless.dll.import");
   try{base(a).mkdirs();
    try(InputStream in=a.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){
     if(in==null)throw new IOException(AppLanguage.text("File unavailable.", "Arquivo indisponível."));
     int m=in.read(),z=in.read();if(m!='M'||z!='Z')throw new IOException(AppLanguage.text("Select the original Lossless.dll from Lossless Scaling.", "Selecione a Lossless.dll original do Lossless Scaling."));
     out.write(m);out.write(z);byte[] bytes=new byte[65536];long total=2;int n;
     while((n=in.read(bytes))!=-1){total+=n;if(total>256L*1024*1024)throw new IOException(AppLanguage.text("DLL exceeds 256 MiB.", "DLL maior que 256 MiB."));out.write(bytes,0,n);}
     if(total<4096)throw new IOException(AppLanguage.text("Incomplete DLL.", "DLL incompleta."));out.getFD().sync();
    }
    if(!temp.renameTo(new File(base(a),"Lossless.dll")))throw new IOException(AppLanguage.text("Could not save the DLL.", "Falha ao salvar a DLL."));
    write(a);
   }catch(Exception e){temp.delete();error=e.getMessage();}
   importing.set(false);
   final String message=error;
   a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;new AlertDialog.Builder(a).setTitle(message==null?AppLanguage.text("Lossless.dll imported", "Lossless.dll importada"):AppLanguage.text("Import failed", "Falha na importação")).setMessage(message==null?AppLanguage.text("Ready. If frame generation was already on, turn it off and on again to reload the DLL.", "Pronto. Se a geração já estava ligada, desligue e ligue novamente para recarregar a DLL."):message).setPositiveButton("OK",null).show();if(done!=null)done.run();});
  },"iss-dll-import").start();
 }
 static void show(Activity a,Runnable updated){
  android.content.SharedPreferences p=a.getSharedPreferences("iss",0);
  LinearLayout l=new LinearLayout(a);l.setOrientation(1);l.setPadding(28,16,28,12);
  TextView info=new TextView(a);info.setText(AppLanguage.text("BloodFrame · Lossless Scaling\nImport Lossless.dll from your Lossless Scaling installation. Shaders are not included with this app.", "BloodFrame · Lossless Scaling\nImporte a Lossless.dll da sua instalação do Lossless Scaling. Os shaders não acompanham o aplicativo."));l.addView(info);
  Button dll=new Button(a);dll.setText(new File(base(a),"Lossless.dll").isFile()?AppLanguage.text("Replace Lossless.dll", "Substituir Lossless.dll"):AppLanguage.text("Import Lossless.dll", "Importar Lossless.dll"));dll.setOnClickListener(v->chooseDll(a));l.addView(dll);
  RadioGroup multiplier=new RadioGroup(a);int m=p.getInt("fg_multiplier",0);for(int n:new int[]{0,2,3,4}){RadioButton b=new RadioButton(a);b.setId(android.view.View.generateViewId());b.setTag(n);b.setText(n==0?AppLanguage.text("Off", "Desligado"):n+AppLanguage.text("× frames", "× quadros"));b.setTextSize(18);multiplier.addView(b);if(n==m || (n==0&&(m<2||m>4)))multiplier.check(b.getId());}l.addView(multiplier);
  TextView flowLabel=new TextView(a);flowLabel.setText(AppLanguage.text("Optical flow scale", "Escala do fluxo óptico"));l.addView(flowLabel);
  Spinner flow=new Spinner(a);flow.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"25%","50%","75%","100%"}));flow.setSelection(Math.max(0,Math.min(3,Math.round(p.getFloat("fg_flow",0.75f)*4)-1)));l.addView(flow);
  CheckBox performance=new CheckBox(a);performance.setText(AppLanguage.text("Performance mode", "Modo desempenho"));performance.setChecked(p.getBoolean("fg_performance",true));l.addView(performance);
  CheckBox fp16=new CheckBox(a);fp16.setText(AppLanguage.text("FP16 precision", "Precisão FP16"));fp16.setChecked(p.getBoolean("fg_fp16",true));l.addView(fp16);
  ScrollView scroll=new ScrollView(a);scroll.addView(l);
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle(AppLanguage.text("Frame generation (BloodFrame)", "Geração de quadros (BloodFrame)")).setView(scroll).setNegativeButton(AppLanguage.text("Cancel", "Cancelar"),null).setPositiveButton(AppLanguage.text("Apply", "Aplicar"),null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
   int value=(Integer)multiplier.findViewById(multiplier.getCheckedRadioButtonId()).getTag();
   if(value>0&&!new File(base(a),"Lossless.dll").isFile()){info.setText(AppLanguage.text("Import Lossless.dll before enabling frame generation.", "Importe a Lossless.dll antes de ativar a geração de quadros."));return;}
   p.edit().putInt("fg_multiplier",value).putFloat("fg_flow",(flow.getSelectedItemPosition()+1)/4f).putBoolean("fg_performance",performance.isChecked()).putBoolean("fg_fp16",fp16.isChecked()).commit();
   try{write(a);if(a instanceof BionicGameActivity)((BionicGameActivity)a).updateDisplayRate();if(updated!=null)updated.run();dialog.dismiss();}catch(IOException e){info.setText(e.getMessage());}
  }));dialog.show();
 }
}
