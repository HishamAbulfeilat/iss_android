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
   case 1: return AppLanguage.text("Hybrid V5", "Híbrido V5");
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
   AppLanguage.text("Hybrid V4 — staged copies, smaller readback windows",
                    "Híbrido V4 — staging, janelas de readback menores"),
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
   "Hybrid V5 restores the 512 KiB staging window and publishes previously requested GPU copies as soon as they finish. No speculative fence copies. Restart to apply.",
   "O híbrido V5 restaura a janela de staging de 512 KiB e publica cópias já solicitadas assim que terminam na GPU, sem cópias antecipadas. Reinicie para aplicar."));
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
