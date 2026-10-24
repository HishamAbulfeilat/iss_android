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
        "leftstick","l3","r3","rightstick","inputmode","swipepad","utilities","graffiti","spray","shake"
    };
    private static final float[][] DEFAULT={
        {87,105},{213,105},{640,105},{830,40},{1067,105},{1193,105},
        {160,395},{90,465},{230,465},{160,535},
        {1120,405},{1190,475},{1120,545},{1050,475},
        {320,612},{420,614},{860,614},{960,612},{450,40},{640,200},{75,60},{400,430},{358,137},{986,151}
    };
    // Approved Direto: aim, spray and power left; camera drag and combat right.
    private static final float[][] MOBILE={
        {154,216},{358,281},{640,60},{995,72},{909,317},{1126,324},
        {135,340},{85,390},{185,390},{135,440},
        {845,482},{922,619},{1126,619},{1190,490},
        {230,585},{280,350},{280,430},{810,460},{330,60},{640,200},
        {90,72},{358,430},{358,137},{986,151}
    };
    private static final float PAD_HALF_W=140,PAD_HALF_H=102,PAD_CORNER=54;
    private boolean mobile,utilitiesOpen;
    private long cameraMotionAt;
    private PointF moveOrigin,swipeOrigin;
    private int swipePointer=-1;
    private boolean swipeMoved,swipeClick,swipePulse;
    private long swipeEpoch;
    private static boolean direction(String name){return name.equals("up")||name.equals("down")||name.equals("left")||name.equals("right");}
    private boolean visible(String name){
        if(name.equals("touchpad")&&(mobile||swipepadEnabled))return false;
        if(name.equals("swipepad"))return swipepadEnabled;
        boolean extra=name.equals("utilities")||name.equals("graffiti")||name.equals("spray")||name.equals("shake");
        if(!mobile)return !extra;
        if(name.equals("rightstick")||name.equals("l3")||name.equals("r3"))return false;
        if(name.equals("shake"))return pad.isSensorMode();
        if(name.equals("cross")&&pad.isSensorMode())return false;
        if(direction(name)||name.equals("l3")||name.equals("r3")||name.equals("inputmode"))return editing||utilitiesOpen;
        return true;
    }
    private float radius(String name){
        if(stick(name))return 70;
        if(!mobile)return name.equals("l3")||name.equals("r3")?39:48;
        if(name.equals("r2"))return 60;
        if(name.equals("cross"))return 42;
        if(direction(name))return 28;
        if(name.equals("utilities")||name.equals("graffiti"))return 30;
        return 35;
    }
    private void startSwipe(int id,float x,float y){
        swipePointer=id;swipeOrigin=new PointF(x,y);swipeMoved=swipeClick=swipePulse=false;
        final long epoch=++swipeEpoch;
        postDelayed(()->{if(epoch==swipeEpoch&&swipePointer>=0&&!swipeMoved){swipeClick=true;publish();}},180);
    }
    private void endSwipe(boolean cancelled){
        boolean tap=!cancelled&&swipePointer>=0&&!swipeMoved&&!swipeClick;
        swipePointer=-1;swipeOrigin=null;swipeClick=false;swipePulse=tap;
        final long epoch=++swipeEpoch;
        if(tap)postDelayed(()->{if(epoch==swipeEpoch){swipePulse=false;publish();}},90);
    }
    private void hideAuxiliary(){
        for(int i=controls.size()-1;i>=0;i--){
            String name=controls.valueAt(i);
            boolean remove=direction(name)||name.equals("dpad")||name.equals("l3")||name.equals("r3")||name.equals("graffiti");
            if(remove){int id=controls.keyAt(i);controls.remove(id);positions.remove(id);}
        }
    }
    private int cameraX=128,cameraY=128;
    private long cameraEpoch;
    private void cameraMotion(float dx,float dy){
        long now=System.nanoTime();
        float dt=cameraMotionAt==0?1f/60f:Math.max(1f/240f,Math.min(1f/20f,(now-cameraMotionAt)/1e9f));
        cameraMotionAt=now;
        // Velocity in reference pixels/second, independent of touch report frequency.
        float sensitivity=Math.max(.25f,Math.min(3f,preferences.getFloat(pad.isSensorMode()?"spray_sensitivity":"camera_sensitivity",1f)));
        float divisor=(pad.isSensorMode()?320f:425f)*dt/sensitivity;
        cameraX=axis(Math.max(-1f,Math.min(1f,dx/divisor)));
        cameraY=axis(Math.max(-1f,Math.min(1f,dy/divisor)));
        final long epoch=++cameraEpoch;
        // A drag supplies camera motion, not a latched right stick. Stop after
        // the last motion event even while the finger remains on the screen.
        postDelayed(()->{if(cameraEpoch==epoch){cameraX=cameraY=128;publish();}},60);
    }
    private String layoutKey(String property,String name){return (mobile?"mobile_direct_v1_":"")+(name.equals("swipepad")?"swipepad_top_"+property:"overlay_"+property+"_"+name);}
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
    private boolean swipepadEnabled,showLabels=true;
    void applyPreferences(){
        release();
        showLabels=preferences.getBoolean("controller_labels",true);
        boolean next=preferences.getBoolean("mobile_controls",true);
        swipepadEnabled=preferences.getBoolean(next?"mobile_swipepad_enabled":"swipepad_enabled",next);
        if(mobile!=next){mobile=next;loadLayout();}pad.setMobileControls(mobile);invalidate();
    }

    TouchControllerOverlay(Context context,PadBridge pad,boolean mouseEnabled) {
        super(context); this.pad=pad; this.mouseEnabled=mouseEnabled;
        pad.onModeChanged(()->{release();utilitiesOpen=mobile&&pad.isMenuMode();invalidate();});
        preferences=context.getSharedPreferences("iss",Context.MODE_PRIVATE);
        guideMarker=new java.io.File(context.getFilesDir(),"issport/android-pad.state.guide");
        mobile=preferences.getBoolean("mobile_controls",true);
        swipepadEnabled=preferences.getBoolean(mobile?"mobile_swipepad_enabled":"swipepad_enabled",mobile);
        pad.setMobileControls(mobile);showLabels=preferences.getBoolean("controller_labels",true);loadLayout();
        setContentDescription(AppLanguage.text("On-screen controller", "Controle na tela"));
    }
    private PointF center(String name) { return mobile&&name.equals("leftstick")&&moveOrigin!=null?moveOrigin:centers.get(name); }
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
        if (visible("up") && !editing && distance(x,y,dpad)<(mobile?80:112) && distance(center("up").x,center("up").y,dpad)<115 &&
            distance(center("left").x,center("left").y,dpad)<115 &&
            distance(center("right").x,center("right").y,dpad)<115 &&
            distance(center("down").x,center("down").y,dpad)<115) return "dpad";
        String winner=null; float best=Float.MAX_VALUE;
        for (String name:NAMES) {
            if(!visible(name)||(mobile&&!editing&&name.equals("leftstick")))continue;
            PointF p=center(name);
            float dx=Math.abs(x-p.x)/size(name),dy=Math.abs(y-p.y)/size(name);
            boolean inside;
            if(name.equals("swipepad")){
                // Match the rounded hit surface to the visual; corners remain free.
                float cx=Math.max(0,dx-(PAD_HALF_W-PAD_CORNER));
                float cy=Math.max(0,dy-(PAD_HALF_H-PAD_CORNER));
                inside=dx<PAD_HALF_W&&dy<PAD_HALF_H&&cx*cx+cy*cy<PAD_CORNER*PAD_CORNER;
            }else if(rectangle(name)&&(!mobile||name.equals("options")||name.equals("inputmode")||name.equals("touchpad"))){
                inside=dx<(name.equals("touchpad")?72:62)&&dy<38;
            }else inside=distance(x,y,p)/size(name)<(mobile&&!stick(name)?Math.max(44,radius(name)+9):radius(name));
            if (inside && distance(x,y,p)<best) { best=distance(x,y,p); winner=name; }
        }
        if(winner!=null||editing||!mobile||guideMarker.isFile())return winner;
        if(x<470&&y>350)return "leftstick";
        return x>640&&y>170?"rightstick":null;
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
                if(!swipeClick&&!finger&&(pad.isAutomaticMode()||!pad.isMenuMode())){
                    PointF c=center(control);float s=size(control);
                    tx=Math.round((p.x-(c.x-PAD_HALF_W*s))/(2*PAD_HALF_W*s)*1919);
                    ty=Math.round((p.y-(c.y-PAD_HALF_H*s))/(2*PAD_HALF_H*s)*942);finger=true;
                }
            } else if (control.equals("l2")) { l2=255; pressed.add(control); }
            else if (control.equals("r2")) { r2=255; pressed.add(control);if(mobile&&!right){rx=cameraX;ry=cameraY;right=true;} }
            else if(control.equals("spray")){r2=255;pressed.add("spray");}
            else if(control.equals("shake")){pressed.add("cross");}
            else if (control.equals("touchpad")) { pressed.add("touchpad_left"); pressed.add("touchpad_right"); } /* Both ends: lift Reggie's car. */
            else if (control.equals("mouse")) {
                if (!mouse) XInput.moveTo(Math.max(0,Math.min(1279,Math.round(p.x))),
                                          Math.max(0,Math.min(719,Math.round(p.y))));
                mouse=true;
            } else pressed.add(control);
        }
        if(swipeClick||swipePulse){
            // Same native two-end click as TOUCH, retaining the car interaction.
            finger=false;pressed.add("touchpad_left");pressed.add("touchpad_right");
        }
        if (mouse!=mouseDown) { XInput.button(1,mouse); mouseDown=mouse; }
        pad.touch(finger,tx,ty);
        pad.virtual(pressed,lx,ly,rx,ry,left,right,l2,r2);
        invalidate();
    }
    void release() {
        controls.clear();positions.clear();moveOrigin=null;cameraMotionAt=0;cameraEpoch++;cameraX=cameraY=128;
        endSwipe(true);utilitiesOpen=false;publish();
    }
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
            if("graffiti".equals(name)){release();pad.toggleSensorMode();invalidate();return true;}
            if("utilities".equals(name)){utilitiesOpen=!utilitiesOpen;if(!utilitiesOpen)hideAuxiliary();publish();return true;}
            if(("swipepad".equals(name)||stick(name==null?"":name)))for(int i=0;i<controls.size();i++)if(name.equals(controls.valueAt(i)))return true;
            if("inputmode".equals(name)){release();pad.cycleInputMode();invalidate();return true;}
            if("options".equals(name)){release();pad.gameMenuButton();utilitiesOpen=mobile&&pad.isMenuMode();}
            // Outside the virtual controls, touches reach the SDL guide only
            // while that modal is shown. Never emulate mouse clicks during
            // gameplay (SDL maps its left button to R2).
            if (name==null && !mouseEnabled && !guideMarker.isFile()) return true;
            controls.put(id,name==null ? "mouse" : name);
            positions.put(id,new PointF(x,y));
            if("swipepad".equals(name))startSwipe(id,x,y);
            if(mobile&&"leftstick".equals(name))moveOrigin=new PointF(x,y);
            if(mobile&&"rightstick".equals(name)){cameraMotionAt=0;cameraEpoch++;cameraX=cameraY=128;}
        } else if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) {
            String old=controls.get(id);
            if(mobile&&("rightstick".equals(old)||"r2".equals(old))){cameraEpoch++;cameraX=cameraY=128;}
            if("swipepad".equals(old))endSwipe(false);
            if("leftstick".equals(old))moveOrigin=null;
            controls.remove(id);positions.remove(id);
        }
        if (action==MotionEvent.ACTION_MOVE) {
            for (int i=0;i<e.getPointerCount();i++) {
                PointF p=positions.get(e.getPointerId(i));
                if (p!=null){
                    float x=layoutX(e,i),y=layoutY(e,i);
                    String control=controls.get(e.getPointerId(i));
                    float dx=x-p.x,dy=y-p.y;
                    if(mobile&&("rightstick".equals(control)||"r2".equals(control))&&(dx!=0||dy!=0)){
                        if(x<=640||p.x<=640){
                            // A camera finger crossing onto the left cannot steer there.
                            // Re-entry establishes a new origin, avoiding a large return delta.
                            cameraMotionAt=0;cameraEpoch++;cameraX=cameraY=128;
                        }else cameraMotion(dx,dy);
                    }
                    if("swipepad".equals(control)&&swipeOrigin!=null&&distance(x,y,swipeOrigin)>10*size("swipepad")){
                        if(!swipeMoved){swipeEpoch++;swipeMoved=true;swipeClick=swipePulse=false;}
                    }
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
            action(c,"l1","ϟ",AppLanguage.text("Power","Poder"));
            action(c,"r1","✦",AppLanguage.text("Special","Especial"));
            action(c,"triangle","♨",AppLanguage.text("Drain","Absorver"));
            action(c,"l2","◎",AppLanguage.text("Aim","Mirar"));
            action(c,"r2","⊕",AppLanguage.text("Fire","Atirar"));
            action(c,"cross","↥",AppLanguage.text("Jump","Pular"));
            action(c,"circle","»",AppLanguage.text("Dash","Esquivar"));
            action(c,"square","✊",AppLanguage.text("Melee","Corpo a corpo"));
            action(c,"utilities","☰",AppLanguage.text("Controls","Controles"));
            action(c,"spray","▥","Spray");
            action(c,"graffiti","↻",pad.isSensorMode()?"ON":"OFF");
            action(c,"shake","↔","×");
            action(c,"options","Ⅱ","OPTIONS");
            if(utilitiesOpen||editing){
                rectangle(c,"inputmode",100,pad.isAutomaticMode()?"AUTO":pad.isMenuMode()?"MENU":AppLanguage.text("GAME","JOGO"));
                circle(c,"up",26,"▲",0xaaffb59b);circle(c,"left",26,"◀",0xaaffb59b);
                circle(c,"right",26,"▶",0xaaffb59b);circle(c,"down",26,"▼",0xaaffb59b);

            }
            if(moveOrigin!=null||editing)drawStick(c,"leftstick");
            else drawMovementHint(c);
            if(swipepadEnabled)drawSwipepad(c);
        }else{
        rectangle(c,"l2",108,"L2"); rectangle(c,"l1",108,"L1");
        rectangle(c,"r1",108,"R1"); rectangle(c,"r2",108,"R2");
        if(!swipepadEnabled)rectangle(c,"touchpad",126,"TOUCH"); rectangle(c,"options",108,"START");
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
    private void drawMovementHint(Canvas c){
        PointF p=centers.get("leftstick");paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);
        paint.setColor(0x55ffb896);c.drawCircle(p.x,p.y,33,paint);c.drawCircle(p.x,p.y,9,paint);
    }
    private void action(Canvas c,String name,String icon,String label){
        if(!visible(name))return;
        PointF p=center(name);float r=radius(name);c.save();c.scale(size(name),size(name),p.x,p.y);
        boolean down=pressed.contains(name)||name.equals("utilities")&&utilitiesOpen||name.equals("graffiti")&&pad.isSensorMode();
        paint.setStyle(Paint.Style.FILL);paint.setColor(down?0xaab93526:0x44150d10);c.drawCircle(p.x,p.y,r,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(name.equals("r2")?3:1.5f);paint.setColor(down?0xffffc18a:0xaaffb08a);
        c.drawCircle(p.x,p.y,r,paint);
        if(name.equals("r2")){paint.setColor(0x77ff642d);c.drawCircle(p.x,p.y,r-7,paint);}
        c.save();c.translate(p.x,p.y);paint.setColor(0xfff6d9c7);paint.setStrokeWidth(2.5f);
        if(name.equals("r2")||name.equals("l2")){
            c.drawCircle(0,0,name.equals("r2")?16:11,paint);
            c.drawLine(-23,0,-8,0,paint);c.drawLine(8,0,23,0,paint);c.drawLine(0,-23,0,-8,paint);c.drawLine(0,8,0,23,paint);
        }else if(name.equals("cross")){
            c.drawLine(0,17,0,-17,paint);c.drawLine(0,-17,-13,-3,paint);c.drawLine(0,-17,13,-3,paint);
            c.drawLine(-14,20,14,20,paint);
        }else if(name.equals("circle")){
            c.drawLine(-16,-13,-3,0,paint);c.drawLine(-3,0,-16,13,paint);
            c.drawLine(-2,-13,11,0,paint);c.drawLine(11,0,-2,13,paint);c.drawLine(-24,0,-15,0,paint);
        }else if(name.equals("square")){
            c.drawRoundRect(-16,-10,14,9,5,5,paint);c.drawLine(-16,-10,-11,-18,paint);
            c.drawLine(-11,-18,12,-18,paint);c.drawLine(12,-18,14,-10,paint);c.drawLine(-10,9,-7,20,paint);c.drawLine(10,9,7,20,paint);
            c.drawLine(-6,-17,-6,-6,paint);c.drawLine(3,-17,3,-6,paint);
        }else if(name.equals("l1")){
            c.drawLine(4,-21,-14,3,paint);c.drawLine(-14,3,2,3,paint);
            c.drawLine(2,3,-4,21,paint);c.drawLine(-4,21,15,-5,paint);
            c.drawLine(15,-5,0,-5,paint);c.drawLine(0,-5,4,-21,paint);
        }else if(name.equals("r1")){
            c.drawLine(0,-22,-5,-5,paint);c.drawLine(-5,-5,-13,-12,paint);
            c.drawLine(-13,-12,-17,7,paint);c.drawLine(-17,7,-8,18,paint);
            c.drawLine(-8,18,8,18,paint);c.drawLine(8,18,17,7,paint);
            c.drawLine(17,7,12,-8,paint);c.drawLine(12,-8,6,0,paint);c.drawLine(6,0,0,-22,paint);
        }else if(name.equals("triangle")){
            c.drawCircle(0,0,6,paint);
            for(int sign:new int[]{-1,1}){
                c.drawLine(sign*23,0,sign*12,0,paint);c.drawLine(sign*12,0,sign*17,-5,paint);c.drawLine(sign*12,0,sign*17,5,paint);
                c.drawLine(0,sign*23,0,sign*12,paint);c.drawLine(0,sign*12,-5,sign*17,paint);c.drawLine(0,sign*12,5,sign*17,paint);
            }
        }else if(name.equals("utilities")){
            for(int y=-10;y<=10;y+=10)c.drawLine(-12,y,12,y,paint);
        }else if(name.equals("shake")){
            c.drawRoundRect(-6,-11,6,12,2,2,paint);
            c.drawLine(-3,-11,-3,-16,paint);c.drawLine(-3,-16,4,-16,paint);
            c.drawLine(-13,-8,-18,-3,paint);c.drawLine(-18,-3,-13,2,paint);
            c.drawLine(13,-2,18,3,paint);c.drawLine(18,3,13,8,paint);
        }else if(name.equals("graffiti")){
            c.drawCircle(0,0,12,paint);c.drawLine(-17,-6,-12,-13,paint);c.drawLine(-12,-13,-5,-9,paint);
            c.drawLine(17,6,12,13,paint);c.drawLine(12,13,5,9,paint);
        }else if(name.equals("spray")){
            c.drawRoundRect(-8,-7,8,15,3,3,paint);c.drawLine(-4,-7,-4,-13,paint);c.drawLine(-4,-13,5,-13,paint);
            c.drawLine(10,-13,18,-17,paint);c.drawLine(11,-9,21,-9,paint);
        }else{
            paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(30);paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(icon,0,10,paint);
        }
        c.restore();
        if(showLabels){
            switch(name){
                case "l1":label="L1";break;case "l2":label="L2";break;
                case "r1":label="R1";break;case "r2":case "spray":label="R2";break;
                case "triangle":label="△";break;case "square":label="□";break;
                case "cross":case "shake":label="×";break;case "circle":label="○";break;
            }
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xccf6d9c7);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(14);
            if(paint.measureText(label)>100)paint.setTextSize(14*100/paint.measureText(label));
            c.drawText(label,p.x,p.y+r+17,paint);
        }
        c.restore();
    }
    private void drawSwipepad(Canvas c){
        PointF p=center("swipepad");float s=size("swipepad");c.save();c.scale(s,s,p.x,p.y);
        boolean lit=swipePointer>=0||swipePulse;
        paint.setStyle(Paint.Style.FILL);paint.setColor(lit?0x773c2020:0x44140d13);
        c.drawRoundRect(p.x-PAD_HALF_W,p.y-PAD_HALF_H,p.x+PAD_HALF_W,p.y+PAD_HALF_H,PAD_CORNER,PAD_CORNER,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(lit?0xffffa471:0x88d69676);
        c.drawRoundRect(p.x-PAD_HALF_W,p.y-PAD_HALF_H,p.x+PAD_HALF_W,p.y+PAD_HALF_H,PAD_CORNER,PAD_CORNER,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(11);paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0x66f5d7c6);
        for(int i=0;i<controls.size();i++)if("swipepad".equals(controls.valueAt(i))){
            PointF f=positions.get(controls.keyAt(i));
            if(f!=null){paint.setColor(0xaaff9c65);c.drawCircle(p.x+Math.max(-PAD_HALF_W+8,Math.min(PAD_HALF_W-8,(f.x-p.x)/s)),p.y+Math.max(-PAD_HALF_H+8,Math.min(PAD_HALF_H-8,(f.y-p.y)/s)),7,paint);}
            break;
        }
        c.restore();
    }
}
