package ro.cobrabm.fexdroid;

import android.app.Activity;
import android.widget.Switch;

/** One persisted startup preference shared by the launcher and player drawer. */
final class BionicStartupSettings {
 static final String KEY="start_directly_in_game";
 static boolean enabled(Activity activity){return activity.getSharedPreferences("iss",0).getBoolean(KEY,false);}
 static Switch toggle(Activity activity){
  Switch control=new Switch(activity);
  control.setText(AppLanguage.text("Start directly in game", "Abrir direto no jogo"));
  control.setTextColor(0xfffff1e9);
  control.setChecked(enabled(activity));
  control.setOnCheckedChangeListener((button,checked)->activity.getSharedPreferences("iss",0).edit().putBoolean(KEY,checked).apply());
  return control;
 }
}
