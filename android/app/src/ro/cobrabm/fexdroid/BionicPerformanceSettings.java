package ro.cobrabm.fexdroid;
import android.app.*;
import android.content.SharedPreferences;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** One persisted setting for launcher and in-game UI; atomic publication to the presenter. */
final class BionicPerformanceSettings {
 static final int[] LIMITS={30,60,120,240};
 static int limit(SharedPreferences p){int n=p.getInt("fps_limit",60);for(int v:LIMITS)if(n==v)return n;return 60;}
 /** Highest refresh rate the panel offers at its current resolution (120 Hz on Galaxy S25 Ultra). */
 static float maxRefresh(Activity a){
  android.view.Display d=a.getWindowManager().getDefaultDisplay();android.view.Display.Mode c=d.getMode();float best=c.getRefreshRate();
  for(android.view.Display.Mode m:d.getSupportedModes())if(m.getPhysicalWidth()==c.getPhysicalWidth()&&m.getPhysicalHeight()==c.getPhysicalHeight())best=Math.max(best,m.getRefreshRate());
  return best;
 }
 /** Real-frame limit sent to the game. With Bloodframe xN, real frames above refresh/N are
  * generated but never shown (120 real x2 = 240 on a 120 Hz panel), so they only cost heat. */
 static int effective(Activity a){
  SharedPreferences p=a.getSharedPreferences("iss",0);int n=limit(p),m=p.getInt("fg_multiplier",0);
  if(m>=2&&m<=4){float hz=maxRefresh(a);int cap=LIMITS[0];for(int v:LIMITS)if(v*m<=hz+.5f)cap=v;n=Math.min(n,cap);}
  return n;
 }
 static String summary(Activity a){SharedPreferences p=a.getSharedPreferences("iss",0);return limit(p)+" FPS · VSync "+(p.getBoolean("vsync",true)?AppLanguage.text("on", "ligado"):AppLanguage.text("off", "desligado"));}
 static synchronized void write(Activity a)throws IOException{
  SharedPreferences p=a.getSharedPreferences("iss",0);File dir=new File(a.getFilesDir(),"issport");dir.mkdirs();File tmp=new File(dir,"performance.state.tmp");
  String data="fps_limit="+effective(a)+"\nvsync="+(p.getBoolean("vsync",true)?1:0)+"\ncomplete=1\n";
  try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data.getBytes(StandardCharsets.US_ASCII));out.getFD().sync();}
  if(!tmp.renameTo(new File(dir,"performance.state")))throw new IOException(AppLanguage.text("Could not apply FPS and VSync.", "Não foi possível aplicar FPS e VSync."));
 }
 static void show(Activity a,Runnable updated){
  SharedPreferences p=a.getSharedPreferences("iss",0);LinearLayout content=new LinearLayout(a);content.setOrientation(1);int pad=Math.round(20*a.getResources().getDisplayMetrics().density);content.setPadding(pad,pad,pad,pad);
  Switch sync=new Switch(a);sync.setText("VSync");sync.setChecked(p.getBoolean("vsync",true));content.addView(sync);
  TextView title=new TextView(a);title.setText(AppLanguage.text("FPS limit", "Limite de FPS"));title.setTextSize(18);title.setPadding(0,pad,0,8);content.addView(title);
  RadioGroup rates=new RadioGroup(a);for(int n:LIMITS){RadioButton b=new RadioButton(a);b.setId(android.view.View.generateViewId());b.setTag(n);b.setText(n+" FPS");rates.addView(b);if(n==limit(p))rates.check(b.getId());}content.addView(rates);
  TextView note=new TextView(a);note.setText(AppLanguage.text("This limits real game frames. Bloodframe multiplies the output. With VSync off, output does not throttle the game to the display refresh rate; visible frames still depend on the screen.", "O limite vale para quadros reais. Bloodframe multiplica a saída. Com VSync desligado, a saída não limita o jogo à frequência da tela; os quadros visíveis ainda dependem da tela."));content.addView(note);
  ScrollView scroll=new ScrollView(a);scroll.addView(content);
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle(AppLanguage.text("FPS and synchronization", "FPS e sincronização")).setView(scroll).setNegativeButton(AppLanguage.text("Cancel", "Cancelar"),null).setPositiveButton(AppLanguage.text("Apply", "Aplicar"),null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{int value=(Integer)rates.findViewById(rates.getCheckedRadioButtonId()).getTag();int old=limit(p);boolean oldSync=p.getBoolean("vsync",true);p.edit().putInt("fps_limit",value).putBoolean("vsync",sync.isChecked()).commit();try{write(a);if(a instanceof BionicGameActivity)((BionicGameActivity)a).updateDisplayRate();if(updated!=null)updated.run();dialog.dismiss();}catch(IOException e){p.edit().putInt("fps_limit",old).putBoolean("vsync",oldSync).commit();note.setText(e.getMessage());}}));dialog.show();
 }
}
