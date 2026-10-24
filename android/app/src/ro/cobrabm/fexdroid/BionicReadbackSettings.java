package ro.cobrabm.fexdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/** One mutually exclusive menu for Relaxed, experimental Hybrid and Precise readbacks. */
final class BionicReadbackSettings {
 static final String KEY = "fix_smoke_powers_precise_readback";
 static final String HYBRID_KEY = "renderer_experimental_hybrid_readback";

 static int mode(SharedPreferences preferences) {
  // Preserve the existing Precise preference; it wins over any old hybrid checkbox.
  if (preferences.getBoolean(KEY, false)) return 2;
  return preferences.getBoolean(HYBRID_KEY, false) ? 1 : 0;
 }
 static boolean enabled(Activity activity) {
  return mode(activity.getSharedPreferences("iss",0)) == 2;
 }
 static String summary(Activity activity) {
  switch (mode(activity.getSharedPreferences("iss",0))) {
   case 1: return AppLanguage.text("Hybrid experimental", "Híbrido experimental");
   case 2: return "Precise 2";
   default: return "Relaxed 1";
  }
 }
 static void show(Activity activity, Runnable refreshed) {
  SharedPreferences preferences = activity.getSharedPreferences("iss",0);
  LinearLayout body = new LinearLayout(activity);
  body.setOrientation(LinearLayout.VERTICAL);
  int pad = Math.round(18 * activity.getResources().getDisplayMetrics().density);
  body.setPadding(pad,pad,pad,pad);

  RadioGroup choices = new RadioGroup(activity);
  choices.setOrientation(RadioGroup.VERTICAL);
  String[] labels = {
   AppLanguage.text("Relaxed 1 — faster; smoke/powers may be incorrect",
                    "Relaxed 1 — mais rápido; fumaça/poderes podem falhar"),
   AppLanguage.text("Hybrid experimental — Relaxed with Precise read protection",
                    "Híbrido experimental — Relaxed com proteção de leitura Precise"),
   AppLanguage.text("Precise 2 — correct smoke/powers; major FPS impact",
                    "Precise 2 — corrige fumaça/poderes; grande queda de FPS")
  };
  int[] ids = new int[labels.length];
  for (int i=0;i<labels.length;i++) {
   RadioButton choice = new RadioButton(activity);
   ids[i] = View.generateViewId();
   choice.setId(ids[i]);
   choice.setText(labels[i]);
   choice.setTextSize(15);
   choice.setPadding(0,pad/2,0,pad/2);
   choices.addView(choice);
  }
  choices.check(ids[mode(preferences)]);
  body.addView(choices);

  TextView help = new TextView(activity);
  help.setText(AppLanguage.text(
   "Hybrid is experimental: compare smoke, powers and FPS. Only one mode is active. Changes apply after restarting the game.",
   "O híbrido é experimental: compare fumaça, poderes e FPS. Só um modo fica ativo. Reinicie o jogo para aplicar."));
  help.setPadding(0,pad,0,0);
  body.addView(help);

  new AlertDialog.Builder(activity)
   .setTitle(AppLanguage.text("Smoke and powers — Readback mode",
                              "Fumaça e poderes — modo Readback"))
   .setView(body)
   .setNegativeButton(AppLanguage.text("Cancel","Cancelar"),null)
   .setPositiveButton(AppLanguage.text("Apply","Aplicar"),(dialog,which)->{
    int selected = choices.getCheckedRadioButtonId();
    int choice = selected == ids[2] ? 2 : selected == ids[1] ? 1 : 0;
    preferences.edit().putBoolean(KEY,choice==2)
     .putBoolean(HYBRID_KEY,choice==1).apply();
    if (refreshed!=null) refreshed.run();
   }).show();
 }
}
