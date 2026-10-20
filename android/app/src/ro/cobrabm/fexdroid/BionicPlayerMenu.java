package ro.cobrabm.fexdroid;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.File;

/** Android drawer above the persistent game surface, adapted from the GoW3 player. */
final class BionicPlayerMenu {
 private final Activity activity;private final FrameLayout root;private final TouchControllerOverlay controls;private final PadBridge pad;
 private final LinearLayout panel,items;private final View scrim,handle;private final PerfHudOverlay hud;
 private final File hudFile;private final Runnable restart;private boolean open;
 BionicPlayerMenu(Activity a,FrameLayout frame,TouchControllerOverlay controller,PadBridge bridge,Runnable restartGame){
  activity=a;root=frame;controls=controller;pad=bridge;restart=restartGame;
  hudFile=new File(a.getFilesDir(),"issport/hud.ini");hudFile.getParentFile().mkdirs();HudConfig.migrateLegacy(a,hudFile);
  hud=new PerfHudOverlay(a);root.addView(hud,new FrameLayout.LayoutParams(-1,-1));
  FrameLayout grip=new FrameLayout(a);grip.setContentDescription(AppLanguage.text("Open game menu", "Abrir menu do jogo"));
  View bar=new View(a);GradientDrawable gold=new GradientDrawable();gold.setColor(0xccff642d);gold.setCornerRadius(dp(3));bar.setBackground(gold);grip.addView(bar,new FrameLayout.LayoutParams(dp(4),dp(48),Gravity.CENTER));
  handle=grip;handle.setElevation(dp(4));root.addView(handle,new FrameLayout.LayoutParams(dp(24),dp(80),Gravity.LEFT|Gravity.CENTER_VERTICAL));handle.setOnClickListener(v->show());
  scrim=new View(a);scrim.setBackgroundColor(0x88000000);scrim.setVisibility(View.GONE);scrim.setOnClickListener(v->close());root.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
  panel=new LinearLayout(a);panel.setOrientation(1);panel.setElevation(dp(16));panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff291116,0xff12090c}));panel.setVisibility(View.GONE);
  ScrollView scroll=new ScrollView(a);items=new LinearLayout(a);items.setOrientation(1);items.setPadding(dp(20),dp(22),dp(18),dp(18));scroll.addView(items);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  TextView footer=text("inFAMOUS SECOND SON\nDaedalus 0.1 · Android Bionic",12,0xff8e939c);footer.setPadding(dp(22),dp(10),dp(16),dp(18));panel.addView(footer);
  root.addView(panel,new FrameLayout.LayoutParams(Math.min(dp(340),Math.round(a.getResources().getDisplayMetrics().widthPixels*0.87f)),-1,Gravity.LEFT));
  apply();
 }
 private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
 private TextView text(String value,int size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
 private void section(String value){TextView t=text(value,13,0xffff642d);t.setTypeface(Typeface.DEFAULT_BOLD);t.setPadding(dp(5),dp(24),0,dp(8));items.addView(t);}
 private void row(String icon,String title,String detail,Runnable action){
  LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(13),dp(4),dp(13));row.setFocusable(true);row.setClickable(true);
  android.util.TypedValue selected=new android.util.TypedValue();activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,selected,true);row.setBackgroundResource(selected.resourceId);
  TextView symbol=text(icon,25,0xffff642d);row.addView(symbol,new LinearLayout.LayoutParams(dp(38),-2));
  LinearLayout words=new LinearLayout(activity);words.setOrientation(1);TextView label=text(title,18,0xfffff1e9);words.addView(label);if(detail!=null){TextView sub=text(detail,12,0xffc7aba8);sub.setPadding(0,dp(3),0,0);words.addView(sub);}row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->action.run());items.addView(row);
 }
 private void contents(){
  items.removeAllViews();items.addView(text(AppLanguage.text("Swipe left or press B to go back", "Deslize para a esquerda ou pressione B para voltar"),12,0xff8e939c));
  section(AppLanguage.text("GAME", "JOGO"));row("▶",AppLanguage.text("Resume game", "Retomar jogo"),null,this::close);
  row("↻",AppLanguage.text("Restart game", "Reiniciar jogo"),AppLanguage.text("Unsaved progress will be lost", "O progresso não salvo será perdido"),()->new AlertDialog.Builder(activity).setTitle(AppLanguage.text("Restart inFAMOUS?", "Reiniciar inFAMOUS?")).setMessage(AppLanguage.text("Unsaved progress will be lost.", "O progresso não salvo será perdido.")).setNegativeButton(AppLanguage.text("Cancel", "Cancelar"),null).setPositiveButton(AppLanguage.text("Restart", "Reiniciar"),(d,w)->{close();restart.run();}).show());
  section(AppLanguage.text("GRAPHICS", "GRÁFICOS"));row("◷",AppLanguage.text("FPS and synchronization", "FPS e sincronização"),BionicPerformanceSettings.summary(activity),()->BionicPerformanceSettings.show(activity,this::contents));row("☷",AppLanguage.text("Patches", "Patches"),AppLanguage.text("Applied on next game start", "Aplicados no próximo início"),()->BionicVideoSettings.show(activity));
  int m=activity.getSharedPreferences("iss",0).getInt("fg_multiplier",0);
  row("◉",AppLanguage.text("Frame generation (BloodFrame)", "Geração de quadros (BloodFrame)"),m>=2?m+"×":AppLanguage.text("Off", "Desligado"),()->FramegenSettings.show(activity,()->{apply();contents();}));
  HudConfig config=HudConfig.load(hudFile);row("☷","HUD — FPS / GPU / CPU",config.enabled?AppLanguage.text("On", "Ligado"):AppLanguage.text("Off", "Desligado"),()->HudControls.show(activity,hudFile,()->{apply();contents();}));
  section(AppLanguage.text("CONTROLS", "CONTROLES"));boolean visible=activity.getSharedPreferences("iss",0).getBoolean("controller_overlay",true);
  row("✚",AppLanguage.text("On-screen controller", "Controle na tela"),visible?AppLanguage.text("On", "Ligado"):AppLanguage.text("Off", "Desligado"),()->{activity.getSharedPreferences("iss",0).edit().putBoolean("controller_overlay",!visible).apply();apply();contents();});
  row("✚",AppLanguage.text("D-pad swipes", "Swipe pelo D-pad"),activity.getSharedPreferences("iss",0).getBoolean("dpad_swipe",true)?AppLanguage.text("Swipe the touchpad", "Deslizar o touchpad"):AppLanguage.text("Directional buttons", "Botões direcionais"),()->controlSettings(activity,pad,()->{apply();contents();}));
  row("✎",AppLanguage.text("Edit controller layout", "Editar layout do controle"),AppLanguage.text("Drag to move · two fingers to resize", "Arraste para mover · dois dedos para tamanho"),()->{close();activity.getSharedPreferences("iss",0).edit().putBoolean("controller_overlay",true).apply();apply();controls.edit(null);});
  row("↺",AppLanguage.text("Reset controller layout", "Restaurar layout do controle"),null,()->{controls.resetLayout();close();});
  row("⇥",AppLanguage.text("Exit game", "Sair do jogo"),null,()->new AlertDialog.Builder(activity).setTitle(AppLanguage.text("Exit inFAMOUS?", "Sair de inFAMOUS?")).setMessage(AppLanguage.text("Unsaved progress will be lost.", "O progresso não salvo será perdido.")).setNegativeButton(AppLanguage.text("Cancel", "Cancelar"),null).setPositiveButton(AppLanguage.text("Exit", "Sair"),(d,w)->activity.finish()).show());
 }
 static void controlSettings(Activity a,PadBridge pad,Runnable updated){
  android.content.SharedPreferences p=a.getSharedPreferences("iss",0);LinearLayout l=new LinearLayout(a);l.setOrientation(1);l.setPadding(24,12,24,12);
  CheckBox swipe=new CheckBox(a);swipe.setText(AppLanguage.text("D-pad swipes the touchpad (without holding Select)", "D-pad desliza o touchpad (sem segurar Select)"));swipe.setChecked(p.getBoolean("dpad_swipe",true));l.addView(swipe);
  CheckBox swap=new CheckBox(a);swap.setText(AppLanguage.text("Swap □ / △", "Trocar □ / △"));swap.setChecked(p.getBoolean("swap_square_triangle",false));l.addView(swap);
  TextView help=new TextView(a);help.setText(AppLanguage.text("Hold ↑ ↓ ← → to swipe gradually. Release to lift the finger. Disable to send normal directional buttons.\n\nThe same mapping applies to physical and on-screen controllers.", "Segure ↑ ↓ ← → para deslizar progressivamente. Solte para levantar o dedo. Desative para enviar os botões direcionais normais.\n\nO mesmo mapeamento vale para o controle físico e o controle na tela."));l.addView(help);
  new AlertDialog.Builder(a).setTitle(AppLanguage.text("Controls", "Controles")).setView(l).setNegativeButton(AppLanguage.text("Cancel", "Cancelar"),null).setPositiveButton(AppLanguage.text("Apply", "Aplicar"),(d,w)->{p.edit().putBoolean("dpad_swipe",swipe.isChecked()).putBoolean("swap_square_triangle",swap.isChecked()).apply();if(pad!=null){pad.setSwapFaceButtons(swap.isChecked());pad.setDpadSwipe(swipe.isChecked());}if(updated!=null)updated.run();}).show();
 }
 void apply(){if(activity instanceof BionicGameActivity)((BionicGameActivity)activity).updateDisplayRate();HudConfig c=HudConfig.load(hudFile);hud.setConfiguration(c);hud.setActive(c.enabled);pad.setSwapFaceButtons(activity.getSharedPreferences("iss",0).getBoolean("swap_square_triangle",false));pad.setDpadSwipe(activity.getSharedPreferences("iss",0).getBoolean("dpad_swipe",true));controls.setVisibility(!open&&activity.getSharedPreferences("iss",0).getBoolean("controller_overlay",true)?View.VISIBLE:View.GONE);}
 void show(){if(open)return;open=true;controls.release();pad.reset();controls.setVisibility(View.GONE);contents();handle.setVisibility(View.GONE);scrim.setVisibility(View.VISIBLE);panel.setVisibility(View.VISIBLE);panel.setTranslationX(-panel.getLayoutParams().width);panel.animate().translationX(0).setDuration(180).start();items.getChildAt(2).requestFocus();}
 void close(){if(!open)return;open=false;panel.animate().cancel();panel.setVisibility(View.GONE);scrim.setVisibility(View.GONE);handle.setVisibility(View.VISIBLE);pad.reset();apply();}
 boolean opened(){return open;}
 void toggle(){if(open)close();else show();}
 void pause(){controls.release();pad.reset();hud.setActive(false);}
 void resume(){apply();}
 void destroy(){panel.animate().cancel();hud.release();}
}
