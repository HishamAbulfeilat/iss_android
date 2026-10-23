package ro.cobrabm.fexdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Native mobile action layout and optional classic pad, each with its own saved positions. */
final class TouchControllerOverlay extends View {
    private static final String[] NAMES={
        "l2","l1","touchpad","options","r1","r2",
        "up","left","right","down","triangle","circle","cross","square",
        "leftstick","l3","r3","rightstick","inputmode","swipepad"
    };
    private static final float[][] DEFAULT={
        {87,105},{213,105},{640,105},{773,105},{1067,105},{1193,105},
        {160,395},{90,465},{230,465},{160,535},
        {1120,405},{1190,475},{1120,545},{1050,475},
        {320,612},{420,614},{860,614},{960,612},{492,105},{640,568}
    };
    private static final float[][] MOBILE={
        {955,335},{925,600},{640,97},{1180,90},{1040,515},{1190,355},
        {95,302},{40,357},{150,357},{95,412},
        {1080,420},{1035,630},{1160,620},{1190,490},
        {180,575},{365,645},{445,645},{810,460},{465,97},{640,600}
    };
    private boolean mobile;
    private int cameraX=128,cameraY=128;
    private long cameraEpoch;
    private void cameraMotion(float dx,float dy){
        cameraX=axis(Math.max(-1f,Math.min(1f,dx/35f)));
        cameraY=axis(Math.max(-1f,Math.min(1f,dy/35f)));
        final long epoch=++cameraEpoch;
        // A drag supplies camera motion, not a latched right stick. Stop after
        // the last motion event even while the finger remains on the screen.
        postDelayed(()->{if(cameraEpoch==epoch){cameraX=cameraY=128;publish();}},60);
    }
    private String layoutKey(String property,String name){return (mobile?"mobile_":"")+"overlay_"+property+"_"+name;}
    private void loadLayout(){
        float[][] defaults=mobile?MOBILE:DEFAULT;
        for(int i=0;i<NAMES.length;i++){
            String name=NAMES[i];
            sizes.put(name,Math.max(0.6f,Math.min(1.6f,preferences.getFloat(layoutKey("size",name),1f))));
            centers.put(name,new PointF(preferences.getFloat(layoutKey("x",name),defaults[i][0]),
                preferences.getFloat(layoutKey("y",name),defaults[i][1])));
        }
    }
    private final PadBridge pad;
    private final SharedPreferences preferences;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String,PointF> centers=new LinkedHashMap<>();
    private final SparseArray<String> controls=new SparseArray<>();
    private final SparseArray<PointF> positions=new SparseArray<>();
    private final Set<String> pressed=new LinkedHashSet<>();
    private boolean mouseDown,editing;
    private final boolean mouseEnabled;
    private final java.io.File guideMarker;
    private int editPointer=-1;
    private String dragged;
    private float pinchDistance,pinchSize;
    private final Map<String,Float> sizes=new LinkedHashMap<>();
    private float size(String name){Float value=sizes.get(name);return value==null?1f:value;}
    private Runnable onEditDone;
    private boolean swipepadEnabled;
    void applyPreferences(){
        release();swipepadEnabled=preferences.getBoolean("swipepad_enabled",false);
        boolean next=preferences.getBoolean("mobile_controls",true);
        if(mobile!=next){mobile=next;loadLayout();}invalidate();
    }

    TouchControllerOverlay(Context context,PadBridge pad,boolean mouseEnabled) {
        super(context); this.pad=pad; this.mouseEnabled=mouseEnabled;
        pad.onModeChanged(this::release);
        preferences=context.getSharedPreferences("iss",Context.MODE_PRIVATE);
        guideMarker=new java.io.File(context.getFilesDir(),"issport/android-pad.state.guide");
        swipepadEnabled=preferences.getBoolean("swipepad_enabled",false);
        mobile=preferences.getBoolean("mobile_controls",true);loadLayout();
        setContentDescription(AppLanguage.text("On-screen controller", "Controle na tela"));
    }
    private PointF center(String name) { return centers.get(name); }
    // The 1280x720 layout drawn at one scale in the centred 16:9 area, as the game (MainActivity):
    // round on any screen shape. Controls may be dragged into the bars around it.
    private float scale() { return Math.max(1e-3f,Math.min(getWidth()/1280f,getHeight()/720f)); }
    private float offsetX() { return (getWidth()-1280f*scale())/2f; }
    private float offsetY() { return (getHeight()-720f*scale())/2f; }
    private float layoutX(MotionEvent e,int index) { return (e.getX(index)-offsetX())/scale(); }
    private float layoutY(MotionEvent e,int index) { return (e.getY(index)-offsetY())/scale(); }
    private static float distance(float x,float y,PointF p) { return (float)Math.hypot(x-p.x,y-p.y); }
    private static boolean stick(String name) { return name.equals("leftstick") || name.equals("rightstick"); }
    private static boolean rectangle(String name) {
        return name.equals("l1") || name.equals("l2") || name.equals("r1") ||
               name.equals("r2") || name.equals("touchpad") || name.equals("options") ||
               name.equals("inputmode") || name.equals("swipepad");
    }
    private PointF dpadCenter() {
        PointF u=center("up"),l=center("left"),r=center("right"),d=center("down");
        return new PointF((u.x+l.x+r.x+d.x)/4f,(u.y+l.y+r.y+d.y)/4f);
    }
    private String hit(float x,float y) {
        PointF dpad=dpadCenter();
        if (!editing && distance(x,y,dpad)<(mobile?80:112) && distance(center("up").x,center("up").y,dpad)<115 &&
            distance(center("left").x,center("left").y,dpad)<115 &&
            distance(center("right").x,center("right").y,dpad)<115 &&
            distance(center("down").x,center("down").y,dpad)<115) return "dpad";
        String winner=null; float best=Float.MAX_VALUE;
        for (String name:NAMES) {
            if(mobile&&!editing&&name.equals("rightstick"))continue;
            if(name.equals("swipepad")&&(!swipepadEnabled||(!editing&&pad.isMenuMode()&&!pad.isAutomaticMode())))continue;
            PointF p=center(name);
            float dx=Math.abs(x-p.x)/size(name),dy=Math.abs(y-p.y)/size(name);
            boolean inside=rectangle(name) ? dx<(name.equals("swipepad")?120:name.equals("touchpad") ? 72 : 62) && dy<(name.equals("swipepad")?59:38)
                    : distance(x,y,p)/size(name)<(stick(name) ? 70 : name.equals("l3") || name.equals("r3") ? (mobile?30:39) : mobile&&(name.equals("up")||name.equals("down")||name.equals("left")||name.equals("right"))?30:48);
            if (inside && distance(x,y,p)<best) { best=distance(x,y,p); winner=name; }
        }
        return winner==null&&mobile&&!editing&&x>640&&y>170&&!guideMarker.isFile()?"rightstick":winner;
    }
    private static int axis(float v) { return Math.max(0,Math.min(255,Math.round((v+1f)*127.5f))); }

    private void publish() {
        pressed.clear();
        int lx=128,ly=128,rx=128,ry=128,l2=0,r2=0;
        boolean left=false,right=false,mouse=false;
        boolean finger=false;int tx=960,ty=471;
        for (int i=0;i<controls.size();i++) {
            String control=controls.valueAt(i);
            PointF p=positions.get(controls.keyAt(i));
            if (p==null) continue;
            if (control.equals("dpad")) {
                PointF c=dpadCenter();
                if (p.x<c.x-22) pressed.add("left");
                if (p.x>c.x+22) pressed.add("right");
                if (p.y<c.y-22) pressed.add("up");
                if (p.y>c.y+22) pressed.add("down");
            } else if (control.equals("leftstick")) {
                left=true; PointF c=center(control);
                lx=axis(Math.max(-1f,Math.min(1f,(p.x-c.x)/(56f*size(control)))));
                ly=axis(Math.max(-1f,Math.min(1f,(p.y-c.y)/(56f*size(control)))));
            } else if (control.equals("rightstick")) {
                right=true;PointF c=center(control);
                rx=mobile?cameraX:axis(Math.max(-1f,Math.min(1f,(p.x-c.x)/(56f*size(control)))));
                ry=mobile?cameraY:axis(Math.max(-1f,Math.min(1f,(p.y-c.y)/(56f*size(control)))));
            } else if(control.equals("swipepad")) {
                if(!finger&&(pad.isAutomaticMode()||!pad.isMenuMode())){
                    PointF c=center(control);float s=size(control);
                    tx=Math.round((p.x-(c.x-120*s))/(240*s)*1919);
                    ty=Math.round((p.y-(c.y-59*s))/(118*s)*942);finger=true;
                }
            } else if (control.equals("l2")) { l2=255; pressed.add(control); }
            else if (control.equals("r2")) { r2=255; pressed.add(control); }
            else if (control.equals("touchpad")) { pressed.add("touchpad_left"); pressed.add("touchpad_right"); } /* Both ends: lift Reggie's car. */
            else if (control.equals("mouse")) {
                if (!mouse) XInput.moveTo(Math.max(0,Math.min(1279,Math.round(p.x))),
                                          Math.max(0,Math.min(719,Math.round(p.y))));
                mouse=true;
            } else pressed.add(control);
        }
        if (mouse!=mouseDown) { XInput.button(1,mouse); mouseDown=mouse; }
        pad.touch(finger,tx,ty);
        pad.virtual(pressed,lx,ly,rx,ry,left,right,l2,r2);
        invalidate();
    }
    void release() { controls.clear(); positions.clear();cameraEpoch++;cameraX=cameraY=128;publish(); }
    void edit(Runnable done) { release(); editing=true; onEditDone=done; invalidate(); }
    private void finishEdit() {
        editPointer=-1; dragged=null; pinchDistance=0; editing=false; invalidate();
        if (onEditDone!=null) onEditDone.run();
        onEditDone=null;
    }
    void resetLayout() {
        SharedPreferences.Editor editor=preferences.edit();
        for (int i=0;i<NAMES.length;i++) {
            String name=NAMES[i]; float[][] defaults=mobile?MOBILE:DEFAULT;centers.get(name).set(defaults[i][0],defaults[i][1]);
            sizes.put(name,1f);editor.remove(layoutKey("size",name));
            editor.remove(layoutKey("x",name)).remove(layoutKey("y",name));
        }
        editor.apply(); release(); invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked(),at=e.getActionIndex(),id=e.getPointerId(at);
        if (editing) {
            if (action==MotionEvent.ACTION_CANCEL) { editPointer=-1; dragged=null; return true; }
            if (action==MotionEvent.ACTION_DOWN) {
                pinchDistance=0;
                float x=layoutX(e,at),y=layoutY(e,at);
                if (x>548 && x<732 && y<72) { finishEdit(); return true; }
                dragged=hit(x,y); editPointer=dragged==null ? -1 : id;
            } else if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()==2 && dragged!=null){
                pinchDistance=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));pinchSize=size(dragged);
            } else if (action==MotionEvent.ACTION_MOVE && editPointer>=0 && dragged!=null) {
                if(e.getPointerCount()==2 && pinchDistance>0){
                    float distance=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));sizes.put(dragged,Math.max(0.6f,Math.min(1.6f,pinchSize*distance/pinchDistance)));invalidate();return true;
                }
                int index=e.findPointerIndex(editPointer);
                float barX=offsetX()/scale(),barY=offsetY()/scale();
                if (index>=0) center(dragged).set(
                    Math.max(36-barX,Math.min(1244+barX,layoutX(e,index))),
                    Math.max(36-barY,Math.min(684+barY,layoutY(e,index))));
                invalidate();
            } else if(action==MotionEvent.ACTION_POINTER_UP && e.getPointerId(at)!=editPointer && dragged!=null){
                preferences.edit().putFloat(layoutKey("size",dragged),size(dragged)).apply();pinchDistance=0;
            } else if ((action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP)
                       && id==editPointer && dragged!=null) {
                PointF p=center(dragged);
                preferences.edit().putFloat(layoutKey("size",dragged),size(dragged)).putFloat(layoutKey("x",dragged),p.x)
                    .putFloat(layoutKey("y",dragged),p.y).apply();
                editPointer=-1; dragged=null;
            }
            return true;
        }
        if (action==MotionEvent.ACTION_CANCEL) { release(); return true; }
        if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            float x=layoutX(e,at),y=layoutY(e,at);
            String name=hit(x,y);
            if(("swipepad".equals(name)||stick(name==null?"":name)))for(int i=0;i<controls.size();i++)if(name.equals(controls.valueAt(i)))return true;
            if("inputmode".equals(name)){release();pad.cycleInputMode();invalidate();return true;}
            if("options".equals(name)){release();pad.gameMenuButton();}
            // Outside the virtual controls, touches reach the SDL guide only
            // while that modal is shown. Never emulate mouse clicks during
            // gameplay (SDL maps its left button to R2).
            if (name==null && !mouseEnabled && !guideMarker.isFile()) return true;
            controls.put(id,name==null ? "mouse" : name);
            positions.put(id,new PointF(x,y));
            if(mobile&&"rightstick".equals(name)){cameraEpoch++;cameraX=cameraY=128;}
        } else if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) {
            if(mobile&&"rightstick".equals(controls.get(id))){cameraEpoch++;cameraX=cameraY=128;}
            controls.remove(id);positions.remove(id);
        }
        if (action==MotionEvent.ACTION_MOVE) {
            for (int i=0;i<e.getPointerCount();i++) {
                PointF p=positions.get(e.getPointerId(i));
                if (p!=null){
                    float x=layoutX(e,i),y=layoutY(e,i);
                    if(mobile&&"rightstick".equals(controls.get(e.getPointerId(i))))cameraMotion(x-p.x,y-p.y);
                    p.set(x,y);
                }
            }
        }
        publish(); return true;
    }

    private void circle(Canvas c,String name,float r,String label,int accent) {
        PointF p=center(name);c.save();c.scale(size(name),size(name),p.x,p.y);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(name.equals(dragged) ? 0xaaff642d : pressed.contains(name) ? 0xbb8f201e : 0x77140d13);
        c.drawCircle(p.x,p.y,r,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(accent);
        c.drawCircle(p.x,p.y,r,paint);
        paint.setStyle(Paint.Style.FILL); paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(label.length()>2 ? 19 : 33); paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(label,p.x,p.y-(paint.ascent()+paint.descent())/2,paint);
        c.restore();
    }
    private void rectangle(Canvas c,String name,float width,String label) {
        PointF p=center(name);c.save();c.scale(size(name),size(name),p.x,p.y);
        // The touchpad button presses "touchpad_right" (right-side click, opens the pause menu),
        // not "touchpad", so highlight it from that signal too.
        boolean lit=pressed.contains(name) ||
            (name.equals("touchpad") && pressed.contains("touchpad_right"));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(name.equals(dragged) ? 0xaaff642d : lit ? 0xbb8f201e : 0x88140d13);
        c.drawRoundRect(p.x-width/2,p.y-25,p.x+width/2,p.y+25,7,7,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0xddff642d); paint.setStrokeWidth(2);
        c.drawRoundRect(p.x-width/2,p.y-25,p.x+width/2,p.y+25,7,7,paint);
        paint.setStrokeWidth(3);c.drawLine(p.x-width/2+10,p.y+20,p.x-width/2+32,p.y+20,paint);
        paint.setStyle(Paint.Style.FILL); paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(19); paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(label,p.x,p.y-(paint.ascent()+paint.descent())/2,paint);
        c.restore();
    }
    private void drawStick(Canvas c,String name) {
        PointF center=center(name);c.save();c.scale(size(name),size(name),center.x,center.y);
        paint.setStyle(Paint.Style.FILL); paint.setColor(name.equals(dragged) ? 0xaaff642d : 0x77140d13);
        c.drawCircle(center.x,center.y,57,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0xddff642d); paint.setStrokeWidth(3);
        c.drawCircle(center.x,center.y,57,paint);
        float knobX=center.x,knobY=center.y;
        for (int i=0;i<controls.size();i++) {
            if (name.equals(controls.valueAt(i))) {
                PointF p=positions.get(controls.keyAt(i));
                if (p!=null) {
                    knobX=center.x+Math.max(-27,Math.min(27,p.x-center.x));
                    knobY=center.y+Math.max(-27,Math.min(27,p.y-center.y));
                }
                break;
            }
        }
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xbbde4930);
        c.drawCircle(knobX,knobY,28,paint);
        paint.setColor(0xff15202c); paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(22);
        c.drawText(mobile?"":name.equals("leftstick") ? "L" : "R",knobX,
                   knobY-(paint.ascent()+paint.descent())/2,paint);
        c.restore();
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.save(); c.translate(offsetX(),offsetY()); c.scale(scale(),scale());
        if(mobile){
            action(c,"l2","◎",AppLanguage.text("Aim","Mirar"));
            action(c,"r2","⊕",AppLanguage.text("Fire","Atirar"));
            action(c,"l1","ϟ",AppLanguage.text("Power","Poder"));
            action(c,"r1","✦",AppLanguage.text("Special","Especial"));
            action(c,"cross","↥",AppLanguage.text("Jump","Pular"));
            action(c,"circle","»",AppLanguage.text("Dash","Esquivar"));
            action(c,"square","✊",AppLanguage.text("Melee","Corpo a corpo"));
            action(c,"triangle","♨",AppLanguage.text("Drain","Absorver"));
            rectangle(c,"options",88,"Ⅱ");rectangle(c,"touchpad",110,AppLanguage.text("Touch","Toque"));
            rectangle(c,"inputmode",100,pad.isAutomaticMode()?"AUTO":pad.isMenuMode()?"MENU":AppLanguage.text("GAME","JOGO"));
            circle(c,"up",26,"▲",0xaaffb59b);circle(c,"left",26,"◀",0xaaffb59b);
            circle(c,"right",26,"▶",0xaaffb59b);circle(c,"down",26,"▼",0xaaffb59b);
            drawStick(c,"leftstick");
            if(editing)drawStick(c,"rightstick");
            circle(c,"l3",26,"L3",0xaaffb59b);circle(c,"r3",26,"R3",0xaaffb59b);
            if(swipepadEnabled)drawSwipepad(c);
        }else{
        rectangle(c,"l2",108,"L2"); rectangle(c,"l1",108,"L1");
        rectangle(c,"r1",108,"R1"); rectangle(c,"r2",108,"R2");
        rectangle(c,"touchpad",126,"TOUCH"); rectangle(c,"options",108,"START");
        rectangle(c,"inputmode",112,pad.isAutomaticMode()?"AUTO":pad.isMenuMode()?"MENU":AppLanguage.text("GAME","JOGO"));
        if(swipepadEnabled)drawSwipepad(c);
        circle(c,"up",39,"▲",0xffefb19a); circle(c,"left",39,"◀",0xffefb19a);
        circle(c,"right",39,"▶",0xffefb19a); circle(c,"down",39,"▼",0xffefb19a);
        circle(c,"triangle",39,"△",0xff83cdaa); circle(c,"circle",39,"○",0xffe38288);
        circle(c,"cross",39,"×",0xff88aaff); circle(c,"square",39,"□",0xffd6a0ce);
        drawStick(c,"leftstick"); drawStick(c,"rightstick");
        circle(c,"l3",29,"L3",0xffa0aec0); circle(c,"r3",29,"R3",0xffa0aec0);
        }
        if (editing) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xcc15171d);
            c.drawRoundRect(548,9,732,62,13,13,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(0xffc6ad76);
            c.drawRoundRect(548,9,732,62,13,13,paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(AppLanguage.text("DONE", "CONCLUIR"),640,36-(paint.ascent()+paint.descent())/2,paint);
        }
        c.restore();
    }
    private void action(Canvas c,String name,String icon,String label){
        PointF p=center(name);c.save();c.scale(size(name),size(name),p.x,p.y);
        boolean down=pressed.contains(name);
        paint.setStyle(Paint.Style.FILL);paint.setColor(down?0xccb93526:0x66301920);
        c.drawCircle(p.x,p.y,42,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(down?0xffffc18a:0xbbff8054);
        c.drawCircle(p.x,p.y,42,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xfffce3d5);paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(30);c.drawText(icon,p.x,p.y+5,paint);
        paint.setTextSize(12);if(paint.measureText(label)>80)paint.setTextSize(12*80/paint.measureText(label));
        c.drawText(label,p.x,p.y+26,paint);c.restore();
    }
    private void drawSwipepad(Canvas c){
        PointF p=center("swipepad");float s=size("swipepad");c.save();c.scale(s,s,p.x,p.y);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0x88140d13);
        c.drawRoundRect(p.x-120,p.y-59,p.x+120,p.y+59,9,9,paint);
        boolean disabled=pad.isMenuMode()&&!pad.isAutomaticMode();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(disabled?0x778c7878:0xccff642d);
        c.drawRoundRect(p.x-120,p.y-59,p.x+120,p.y+59,9,9,paint);
        c.drawLine(p.x-18,p.y,p.x+18,p.y,paint);c.drawLine(p.x,p.y-18,p.x,p.y+18,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(16);paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(disabled?AppLanguage.text("SWIPE · GAME MODE","SWIPE · MODO JOGO"):"SWIPE",p.x,p.y-36,paint);
        for(int i=0;i<controls.size();i++)if("swipepad".equals(controls.valueAt(i))){PointF f=positions.get(controls.keyAt(i));if(f!=null)c.drawCircle(p.x+Math.max(-116,Math.min(116,(f.x-p.x)/s)),p.y+Math.max(-55,Math.min(55,(f.y-p.y)/s)),7,paint);break;}
        c.restore();
    }
}
