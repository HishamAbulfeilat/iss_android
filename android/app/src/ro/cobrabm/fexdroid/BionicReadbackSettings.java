package ro.cobrabm.fexdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/** One mutually exclusive menu for Relaxed, experimental Hybrid and Precise readbacks. */
final class BionicReadbackSettings {
 static final String KEY = "fix_smoke_powers_precise_readback";
 static final String HYBRID_KEY = "renderer_experimental_hybrid_readback";
 static final String HYBRID_EAGER_KEY = "renderer_hybrid_eager_prefetch";

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
   case 1: return AppLanguage.text("Hybrid V3", "Híbrido V3");
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
   AppLanguage.text("Hybrid V3 — staged copies, optional fence prefetch",
                    "Híbrido V3 — staging, antecipação opcional nos fences"),
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

  CheckBox eager = new CheckBox(activity);
  eager.setText(AppLanguage.text(
      "Extra experiment: prefetch readbacks at GPU fences (may lower FPS)",
      "Experimento extra: antecipar readbacks nos fences (pode reduzir FPS)"));
  eager.setChecked(preferences.getBoolean(HYBRID_EAGER_KEY,false));
  eager.setPadding(0,pad/2,0,pad/2);
  body.addView(eager);
  eager.setVisibility(choices.getCheckedRadioButtonId() == ids[1] ? View.VISIBLE : View.GONE);
  choices.setOnCheckedChangeListener((group,selectedId)->
      eager.setVisibility(selectedId == ids[1] ? View.VISIBLE : View.GONE));

  TextView help = new TextView(activity);
  help.setText(AppLanguage.text(
   "Hybrid V3 keeps staged copies and read protection; fence prefetch is off by default to measure its FPS cost. Restart the game to apply.",
   "O híbrido V3 mantém staging e proteção de leitura; a antecipação está desligada por padrão para comparar o FPS. Reinicie o jogo para aplicar."));
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
     .putBoolean(HYBRID_KEY,choice==1)
     .putBoolean(HYBRID_EAGER_KEY,eager.isChecked()).apply();
    if (refreshed!=null) refreshed.run();
   }).show();
 }
}
