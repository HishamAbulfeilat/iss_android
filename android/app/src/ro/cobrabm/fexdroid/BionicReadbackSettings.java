package ro.cobrabm.fexdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Shared Bionic readback preference. Applied to the GPU at the next game launch. */
final class BionicReadbackSettings {
 static final String KEY = "fix_smoke_powers_precise_readback";
 static boolean enabled(Activity activity) {
  return activity.getSharedPreferences("iss", 0).getBoolean(KEY, false);
 }
 static String summary(Activity activity) {
  return enabled(activity)
      ? AppLanguage.text("On · restart required", "Ligado · reinício necessário")
      : AppLanguage.text("Off (Relaxed)", "Desligado (Relaxed)");
 }
 static void show(Activity activity, Runnable refreshed) {
  SharedPreferences preferences = activity.getSharedPreferences("iss", 0);
  LinearLayout body = new LinearLayout(activity);
  body.setOrientation(LinearLayout.VERTICAL);
  int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
  body.setPadding(pad, pad, pad, pad);
  Switch precise = new Switch(activity);
  precise.setText(AppLanguage.text("Fix smoke and powers (Readback Precise)",
                                  "Correção para fumaça e poderes (Readback Precise)"));
  precise.setChecked(enabled(activity));
  body.addView(precise);
  TextView description = new TextView(activity);
  description.setText(AppLanguage.text(
      "Off by default: keeps Relaxed readback for performance. Enabling Precise may fix GPU-dependent smoke dash and power effects, but can reduce FPS. The change takes effect after restarting the game.",
      "Desligado por padrão: mantém Readback Relaxed para melhor desempenho. Ativar Precise pode corrigir efeitos de Smoke Dash e poderes dependentes da GPU, mas pode reduzir o FPS. A mudança será aplicada após reiniciar o jogo."));
  description.setPadding(0, pad, 0, 0);
  body.addView(description);
  new AlertDialog.Builder(activity)
      .setTitle(AppLanguage.text("Smoke and powers correction", "Correção para fumaça e poderes"))
      .setView(body)
      .setNegativeButton(AppLanguage.text("Cancel", "Cancelar"), null)
      .setPositiveButton(AppLanguage.text("Apply", "Aplicar"), (dialog, which) -> {
       preferences.edit().putBoolean(KEY, precise.isChecked()).apply();
       if (refreshed != null) refreshed.run();
      }).show();
 }
}
