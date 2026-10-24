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
 static final String BACKOFF_KEY = "renderer_hybrid_eager_backoff";

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
   case 1: return AppLanguage.text("Hybrid V6", "Híbrido V6");
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
   AppLanguage.text("Hybrid V6 — V2 graphics correction; optional copy reduction",
                    "Híbrido V6 — correção visual V2; economia opcional de cópias"),
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

  CheckBox optimized = new CheckBox(activity);
  optimized.setText(AppLanguage.text(
      "Experimental: reduce unused GPU fence copies (backoff 63 instead of 7)",
      "Experimental: reduzir cópias GPU não utilizadas (backoff 63 em vez de 7)"));
  optimized.setChecked(preferences.getBoolean(BACKOFF_KEY,false));
  optimized.setPadding(0,pad/2,0,pad/2);
  body.addView(optimized);
  optimized.setVisibility(choices.getCheckedRadioButtonId()==ids[1] ? View.VISIBLE : View.GONE);
  choices.setOnCheckedChangeListener((group,selectedId)->
      optimized.setVisibility(selectedId==ids[1] ? View.VISIBLE : View.GONE));

  TextView help = new TextView(activity);
  help.setText(AppLanguage.text(
   "Hybrid V6 restores the V2 visual baseline with staged readbacks and fence prefetch. Optional copy reduction skips repeatedly stale, unread speculative pages; all demanded readbacks retain the staging fallback. Restart to apply.",
   "O híbrido V6 restaura o comportamento visual do V2, com staging e antecipação. A economia opcional deixa de repetir cópias antecipadas não utilizadas; as leituras necessárias mantêm o staging. Reinicie para aplicar."));
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
     .putBoolean(BACKOFF_KEY,optimized.isChecked()).apply();
    if (refreshed!=null) refreshed.run();
   }).show();
 }
}
