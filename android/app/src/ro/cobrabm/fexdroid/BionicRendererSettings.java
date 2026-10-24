package ro.cobrabm.fexdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Map;

/** Reversible renderer changes, read only when a fresh game process starts. */
final class BionicRendererSettings {
 static void environment(SharedPreferences p,Map<String,String> env){
  final boolean precise = p.getBoolean("fix_smoke_powers_precise_readback", false);
  env.put("TLG_READBACKS", precise ? "2" : "1");
  env.put("TLG_ISS_HYBRID_READ_PROTECT", (!precise && p.getBoolean("renderer_experimental_hybrid_readback",false)) ? "1" : "0");
  // The PC deferred readback path moves the Precise GPU wait to guest fault handling.
  // Do not force eager downloads: they can increase synchronization cost.
  if (precise) env.put("TLG_DEFERRED_READBACK", "1");
  env.put("TLG_DYNAMIC_STRIDE",p.getBoolean("renderer_dynamic_stride",true)?"1":"0");
  env.put("TLG_OCCLUSION_MODE",p.getBoolean("renderer_real_occlusion",true)?"4":"3");
  env.put("TLG_GC_BATCH_DOWNLOAD",p.getBoolean("renderer_batch_gc",true)?"1":"0");
  env.put("ISS_VMA_BLOCK_MB",p.getBoolean("renderer_small_blocks",true)?"64":"0");
 }
 static void show(Activity a){
  SharedPreferences p=a.getSharedPreferences("iss",0);
  LinearLayout panel=new LinearLayout(a);panel.setOrientation(1);panel.setPadding(24,12,24,12);
  TextView help=new TextView(a);help.setText(AppLanguage.text("Changes apply on next game start. Disable a correction to compare rendering and stability.","As alterações valem no próximo início do jogo. Desative uma correção para comparar imagem e estabilidade."));panel.addView(help);
  String[] keys={"renderer_dynamic_stride","renderer_real_occlusion","renderer_batch_gc","renderer_small_blocks"};
  String[] names={AppLanguage.text("Dynamic compute stride (smoke)","Stride dinâmico de compute (fumaça)"),AppLanguage.text("Real light occlusion","Oclusão real das luzes"),AppLanguage.text("Batched texture write-backs","Retorno de texturas em lote"),AppLanguage.text("Smaller memory blocks","Blocos menores de memória")};
  for(int i=0;i<keys.length;i++){final String key=keys[i];CheckBox control=new CheckBox(a);control.setText(names[i]);control.setChecked(p.getBoolean(key,true));control.setOnCheckedChangeListener((b,on)->p.edit().putBoolean(key,on).apply());panel.addView(control);}
  CheckBox hybrid=new CheckBox(a);
  hybrid.setText(AppLanguage.text("Experimental Relaxed + Precise read protection (smoke/powers; may reduce FPS)",
    "Experimental: Relaxed + proteção de leitura Precise (fumaça/poderes; pode reduzir FPS)"));
  hybrid.setChecked(p.getBoolean("renderer_experimental_hybrid_readback",false));
  hybrid.setOnCheckedChangeListener((b,on)->p.edit().putBoolean("renderer_experimental_hybrid_readback",on).apply());
  panel.addView(hybrid);
  new AlertDialog.Builder(a).setTitle(AppLanguage.text("Renderer corrections","Correções do renderer")).setView(panel).setPositiveButton(AppLanguage.text("Done","Concluir"),null).show();
 }
}
