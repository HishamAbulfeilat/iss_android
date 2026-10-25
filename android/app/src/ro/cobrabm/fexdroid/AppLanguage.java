package ro.cobrabm.fexdroid;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

/** One resolved locale for the Android UI and the game's next boot. */
final class AppLanguage {
 static final String[] CODES={"auto","en","pt","es","fr","de","it","ru","ja","ko","zh","tr","pl"};
 static final String[] LABELS={"Automatic","English","Português","Español","Français","Deutsch","Italiano","Русский","日本語","한국어","简体中文","Türkçe","Polski"};
 private static volatile String selected="en",gameCode="en";
 static String resolve(String choice,Locale system){
  String code="auto".equals(choice)?system.getLanguage():choice;
  for(int i=1;i<CODES.length;i++)if(CODES[i].equals(code))return code;
  return "en";
 }
 static Context attach(Context base){
  String choice=base.getSharedPreferences("iss",0).getString("app_language","auto");
  Locale system=Resources.getSystem().getConfiguration().getLocales().get(0);
  selected=resolve(choice,system);
  // Preserve regional variants which the game exposes as separate language IDs.
  String region=system.getCountry();
  gameCode=selected;
  if(selected.equals("pt"))gameCode=region.equals("PT")?"pt_PT":"pt_BR";
  if(selected.equals("es"))gameCode=region.equals("ES")?"es":"es_LA";
  if(selected.equals("fr")&&region.equals("CA"))gameCode="fr_CA";
  if(selected.equals("zh")&&(region.equals("TW")||region.equals("HK")))gameCode="zh_TW";
  Configuration cfg=new Configuration(base.getResources().getConfiguration());
  cfg.setLocale(Locale.forLanguageTag(selected.equals("zh")?"zh-CN":gameCode.replace('_','-')));
  return base.createConfigurationContext(cfg);
 }
 static String gameLanguage(){return gameCode;}
 static String text(String english,String pt){
  if(selected.equals("pt"))return pt;
  return LanguageCatalog.text(selected,english);
 }
 static String summary(Context a){
  String choice=a.getSharedPreferences("iss",0).getString("app_language","auto");
  int index=0;for(int i=0;i<CODES.length;i++)if(CODES[i].equals(choice))index=i;
  return text("Language","Idioma")+" · "+(index==0?text("Automatic","Automático")+" ("+LABELS[java.util.Arrays.asList(CODES).indexOf(selected)]+")":LABELS[index]);
 }
 static void show(Activity a){
  String choice=a.getSharedPreferences("iss",0).getString("app_language","auto");
  int found=java.util.Arrays.asList(CODES).indexOf(choice);final int current=Math.max(0,found);
  String[] labels=LABELS.clone();labels[0]=text("Automatic","Automático");
  new AlertDialog.Builder(a).setTitle(text("Language","Idioma"))
   .setSingleChoiceItems(labels,current,(dialog,index)->{
    if(index==current){dialog.dismiss();return;}
    if(a.getSharedPreferences("iss",0).edit().putString("app_language",CODES[index]).commit()){
     dialog.dismiss();
     a.getWindow().getDecorView().post(() -> {
      if (!a.isFinishing() && !a.isDestroyed()) a.recreate();
     });
    }
   }).setNegativeButton(text("Cancel","Cancelar"),null).show();
 }
}
