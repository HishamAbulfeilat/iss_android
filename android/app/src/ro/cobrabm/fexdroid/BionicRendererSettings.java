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
  // The UI label "Precise 2" intentionally executes the Hybrid V6 path.
  // Never select real Readbacks=2; treat older Precise preferences as Hybrid.
  env.put("TLG_READBACKS", "1");
  final boolean hybrid = p.getBoolean("renderer_experimental_hybrid_readback",false)
      || p.getBoolean("fix_smoke_powers_precise_readback",false);
  env.put("TLG_ISS_HYBRID_READ_PROTECT",hybrid ? "1" : "0");
  // Opt-in V6 experiment; when disabled this is exactly the confirmed V2 path.
  env.put("TLG_ISS_EAGER_BACKOFF_63",hybrid && p.getBoolean("renderer_hybrid_eager_backoff",false) ? "1" : "0");
  env.put("TLG_ISS_HYBRID_TARGETED_STAGE",
      hybrid && p.getBoolean("renderer_hybrid_targeted_staging",false) ? "1" : "0");
  // V2: use staged GPU->CPU copies for visibility instead of reading sparse
  // residency directly; prefetch recurring fault pages at fences to overlap waits.
  // Apply these parameters only to Hybrid; plain Relaxed is unchanged.
  if (hybrid) {
   env.put("TLG_MAPPED_READBACK","0");
   env.put("TLG_EAGER_READBACK","1");
  } else {
   env.put("TLG_MAPPED_READBACK","1");
   env.put("TLG_EAGER_READBACK","0");
  }

  env.put("TLG_DYNAMIC_STRIDE",p.getBoolean("renderer_dynamic_stride",true)?"1":"0");
  env.put("TLG_OCCLUSION_MODE",p.getBoolean("renderer_real_occlusion",true)?"4":"3");
  env.put("TLG_GC_BATCH_DOWNLOAD",p.getBoolean("renderer_batch_gc",true)?"1":"0");
  env.put("ISS_VMA_BLOCK_MB",p.getBoolean("renderer_small_blocks",true)?"64":"0");
  // Hot pages (v0.2.5-v0.2.7 experiment) is forced off: it disables deferred readbacks, so every
  // GPU page fault waited ~10 s for the whole GPU queue and in-game loading hung on the S25 Ultra.
  env.put("TLG_HOT_PAGES","0");
  // Opt-in memory experiments. Release: buffer memory the GPU has not used for a while and did
  // not write is freed under memory pressure and re-uploaded on next use. No MSAA: guest
  // multisampled targets are single-sampled (the tested AMD-on-PC workaround).
  if(p.getBoolean("renderer_release_idle",false))env.put("TLG_RELEASE_IDLE_BUFFERS","1");
  if(p.getBoolean("renderer_no_msaa",false))env.put("TLG_NO_MSAA","1");
  env.put("TLG_SHARED_HEAPS",p.getBoolean("renderer_shared_heaps",true)?"1":"0");
  env.put("TLG_SHARED_GPU_WRITES",p.getBoolean("renderer_shared_gpu_writes",true)?"1":"0");
  // Opt-in speed experiments (off by default on Android since GoW3 Bionic): the two-stage draw
  // pipeline (+18-19% measured on PC; Bloodborne-Android keeps it on) and FEX's multiblock JIT.
  if(p.getBoolean("renderer_draw_pipe",false))env.put("TLG_DRAW_PIPE","1");
  if(p.getBoolean("renderer_fex_multiblock",false))env.put("FEX_MULTIBLOCK","1");
  if(p.getBoolean("renderer_prep_workers",false))env.put("TLG_PREP_WORKERS","2");
 }
 static void show(Activity a){
  SharedPreferences p=a.getSharedPreferences("iss",0);
  LinearLayout panel=new LinearLayout(a);panel.setOrientation(1);panel.setPadding(24,12,24,12);
  TextView help=new TextView(a);help.setText(AppLanguage.text("Changes apply on next game start. Disable a correction to compare rendering and stability.","As alterações valem no próximo início do jogo. Desative uma correção para comparar imagem e estabilidade."));panel.addView(help);
  String[] keys={"renderer_dynamic_stride","renderer_real_occlusion","renderer_batch_gc","renderer_small_blocks"};
  String[] names={AppLanguage.text("Dynamic compute stride (smoke)","Stride dinâmico de compute (fumaça)"),AppLanguage.text("Real light occlusion","Oclusão real das luzes"),AppLanguage.text("Batched texture write-backs","Gravação de texturas em lote"),AppLanguage.text("Smaller memory blocks","Blocos menores de memória")};
  for(int i=0;i<keys.length;i++){final String key=keys[i];CheckBox control=new CheckBox(a);control.setText(names[i]);control.setChecked(p.getBoolean(key,true));control.setOnCheckedChangeListener((b,on)->p.edit().putBoolean(key,on).apply());panel.addView(control);}
  CheckBox hint=new CheckBox(a);hint.setText(AppLanguage.text("Android performance hint (keeps game threads clocked up)","Dica de desempenho do Android (mantém as threads do jogo em alta frequência)"));hint.setChecked(BionicPerfHint.enabled(p));hint.setOnCheckedChangeListener((b,on)->p.edit().putBoolean("renderer_perf_hint",on).apply());panel.addView(hint);
  CheckBox release=new CheckBox(a);release.setText(AppLanguage.text("Experimental: free idle GPU buffer memory (fewer low-memory closes; may stutter or glitch)","Experimental: liberar memória ociosa de buffers da GPU (menos fechamentos por memória; pode travar ou falhar a imagem)"));release.setChecked(p.getBoolean("renderer_release_idle",false));release.setOnCheckedChangeListener((b,on)->p.edit().putBoolean("renderer_release_idle",on).apply());panel.addView(release);
  CheckBox msaa=new CheckBox(a);msaa.setText(AppLanguage.text("Disable multisampling (less memory and GPU work; softer edges)","Desligar multisampling (menos memória e trabalho da GPU; bordas mais suaves)"));msaa.setChecked(p.getBoolean("renderer_no_msaa",false));msaa.setOnCheckedChangeListener((b,on)->p.edit().putBoolean("renderer_no_msaa",on).apply());panel.addView(msaa);
  CheckBox shared=new CheckBox(a);shared.setText(AppLanguage.text("Unified memory: the game and the GPU share the geometry heaps (much less RAM; turn off if you see glitches)","Memória unificada: o jogo e a GPU compartilham os heaps de geometria (muito menos RAM; desligue se aparecerem falhas)"));shared.setChecked(p.getBoolean("renderer_shared_heaps",true));shared.setOnCheckedChangeListener((b,on)->p.edit().putBoolean("renderer_shared_heaps",on).apply());panel.addView(shared);
  CheckBox sharedWrites=new CheckBox(a);sharedWrites.setText(AppLanguage.text("Unified memory: GPU results too (higher FPS, no waits for GPU data; turn off if you see glitches)","Memória unificada: resultados da GPU também (mais FPS, sem esperar dados da GPU; desligue se aparecerem falhas)"));sharedWrites.setChecked(p.getBoolean("renderer_shared_gpu_writes",true));sharedWrites.setOnCheckedChangeListener((b,on)->p.edit().putBoolean("renderer_shared_gpu_writes",on).apply());panel.addView(sharedWrites);
  CheckBox pipe=new CheckBox(a);pipe.setText(AppLanguage.text("Experimental: two-stage draw pipeline (draws prepared on a second thread; may raise FPS on 8-core phones)","Experimental: pipeline de desenho em dois estágios (desenhos preparados em outra thread; pode aumentar o FPS em celulares de 8 núcleos)"));pipe.setChecked(p.getBoolean("renderer_draw_pipe",false));pipe.setOnCheckedChangeListener((b,on)->p.edit().putBoolean("renderer_draw_pipe",on).apply());panel.addView(pipe);
  CheckBox multiblock=new CheckBox(a);multiblock.setText(AppLanguage.text("Experimental: FEX multiblock JIT (larger translated code blocks; faster game code, may crash)","Experimental: JIT multiblock do FEX (blocos traduzidos maiores; código do jogo mais rápido, pode travar)"));multiblock.setChecked(p.getBoolean("renderer_fex_multiblock",false));multiblock.setOnCheckedChangeListener((b,on)->p.edit().putBoolean("renderer_fex_multiblock",on).apply());panel.addView(multiblock);
  CheckBox prep=new CheckBox(a);prep.setText(AppLanguage.text("Experimental: draw preparation workers (2 helper threads read the game's GPU commands ahead; use with the draw pipeline)","Experimental: preparadores de desenho (2 threads auxiliares leem antes os comandos de GPU do jogo; use com o pipeline de desenho)"));prep.setChecked(p.getBoolean("renderer_prep_workers",false));prep.setOnCheckedChangeListener((b,on)->p.edit().putBoolean("renderer_prep_workers",on).apply());panel.addView(prep);
  new AlertDialog.Builder(a).setTitle(AppLanguage.text("Renderer corrections","Correções da renderização")).setView(panel).setPositiveButton(AppLanguage.text("Done","Concluir"),null).show();
 }
}
