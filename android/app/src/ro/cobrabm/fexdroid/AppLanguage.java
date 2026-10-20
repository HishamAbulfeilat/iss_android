package ro.cobrabm.fexdroid;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import java.util.Locale;

/** App-owned language, independent of the phone language. English is the first-run default. */
final class AppLanguage {
 private static volatile boolean portuguese;
 static Context attach(Context base){
  portuguese="pt".equals(base.getSharedPreferences("iss",0).getString("app_language","en"));
  Configuration cfg=new Configuration(base.getResources().getConfiguration());
  cfg.setLocale(portuguese?new Locale("pt","BR"):Locale.ENGLISH);
  return base.createConfigurationContext(cfg);
 }
 static String text(String english,String pt){return portuguese?pt:english;}
 static void show(Activity a){
  int current=portuguese?1:0;
  new AlertDialog.Builder(a).setTitle(text("Language","Idioma"))
   .setSingleChoiceItems(new String[]{"English", "Português (Brasil)"},current,(dialog,index)->{
    if(index==current){dialog.dismiss();return;}
    if(a.getSharedPreferences("iss",0).edit().putString("app_language",index==1?"pt":"en").commit()){
     portuguese=index==1;dialog.dismiss();a.recreate();
    }
   }).setNegativeButton(text("Cancel","Cancelar"),null).show();
 }
}
