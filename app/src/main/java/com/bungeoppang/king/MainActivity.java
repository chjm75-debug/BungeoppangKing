package com.bungeoppang.king;

import android.app.Activity;
import android.graphics.*;
import android.media.*;
import android.os.Bundle;
import android.view.*;
import java.util.*;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new GameView());
    }

    private class GameView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random rnd = new Random();

        int cash = 5000, day = 1, dough = 0, display = 0, queue = 0;
        boolean open = false, mixing = false, fermenting = false, manage = false;
        int mixCount = 0, doughCapacity = 12, caseCapacity = 8, moldCount = 4;
        long fermentStart = 0, lastCustomer = 0;
        String order = "";
        int orderQty = 0;
        boolean customer = false;

        int[] stage = new int[4]; // 0 empty,1 batter,2 cook1,3 flip,4 cook2,5 out,6 burnt
        long[] timer = new long[4];

        RectF doughRect = new RectF(), grillRect = new RectF(), caseRect = new RectF();
        RectF openRect = new RectF(), manageRect = new RectF(), shapeRect = new RectF(), sellRect = new RectF();
        RectF mixButton = new RectF(), closeMix = new RectF();

        GameView() {
            super(MainActivity.this);
            setBackgroundColor(Color.rgb(33,24,20));
            text.setTypeface(Typeface.create("sans", Typeface.BOLD));
            p.setStrokeWidth(3f);
        }

        int C(String hex){ return Color.parseColor(hex); }
        void fill(Canvas c, int color, RectF r, float rad){
            p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawRoundRect(r,rad,rad,p);
        }
        void stroke(Canvas c,int color,RectF r,float rad,float sw){
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sw); p.setColor(color); c.drawRoundRect(r,rad,rad,p); p.setStyle(Paint.Style.FILL);
        }
        void txt(Canvas c,String s,float x,float y,float size,int color,Paint.Align align){
            text.setTextSize(size); text.setColor(color); text.setTextAlign(align); c.drawText(s,x,y,text);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            updateTimers();
            float w=getWidth(), h=getHeight();

            // winter sky + ground
            p.setShader(new LinearGradient(0,0,0,h*0.35f,C("#36536A"),C("#1F2A34"),Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h*0.35f,p); p.setShader(null);
            fill(c,C("#1F1713"),new RectF(0,h*0.30f,w,h),0);

            // top status
            RectF top=new RectF(18,24,w-18,155);
            fill(c,C("#3A271E"),top,28); stroke(c,C("#72533F"),top,28,3);
            txt(c,"🐟  붕어빵 장사왕",40,76,38,Color.WHITE,Paint.Align.LEFT);
            txt(c,"겨울 길거리 포장마차",40,117,22,C("#F4D59A"),Paint.Align.LEFT);
            txt(c,"DAY "+day,w-250,72,25,Color.WHITE,Paint.Align.CENTER);
            txt(c,fmt(cash)+"원",w-130,72,25,C("#FFD063"),Paint.Align.CENTER);
            txt(c,open?"영업중":"영업전",w-190,119,22,open?C("#83D36A"):C("#D0B39C"),Paint.Align.CENTER);

            // awning
            float stallTop=175;
            for(int i=0;i<8;i++){p.setColor(i%2==0?C("#B94A37"):C("#EED6AF"));c.drawRect(20+i*(w-40)/8,stallTop,20+(i+1)*(w-40)/8,stallTop+48,p);}
            p.setColor(C("#432A20"));c.drawRect(20,stallTop+48,w-20,stallTop+54,p);

            // customer strip
            RectF cust=new RectF(20,stallTop+54,w-20,stallTop+230);
            fill(c,C("#EED9B7"),cust,0);
            txt(c,"손님 주문",40,stallTop+92,23,C("#5A3423"),Paint.Align.LEFT);
            if(customer){
                txt(c,"🧑  "+order+" 붕어빵 "+orderQty+"개",50,stallTop+148,30,C("#3C241A"),Paint.Align.LEFT);
                sellRect.set(w-210,stallTop+112,w-48,stallTop+177);
                fill(c,C("#558E49"),sellRect,18);
                txt(c,"판매",sellRect.centerX(),sellRect.centerY()+10,26,Color.WHITE,Paint.Align.CENTER);
            } else {
                txt(c,open?"손님을 기다리는 중…":"영업 시작을 눌러주세요",50,stallTop+150,26,C("#76523D"),Paint.Align.LEFT);
                sellRect.setEmpty();
            }

            // workbench
            float workTop=stallTop+245, gap=12;
            float usable=w-40-gap*2;
            float dw=usable*0.23f, gw=usable*0.50f, cw=usable*0.27f;
            doughRect.set(20,workTop,20+dw,h-210);
            grillRect.set(20+dw+gap,workTop,20+dw+gap+gw,h-210);
            caseRect.set(grillRect.right+gap,workTop,w-20,h-210);

            drawDough(c,doughRect);
            drawGrill(c,grillRect);
            drawCase(c,caseRect);

            // bottom bar
            openRect.set(20,h-185,w*0.34f,h-105);
            shapeRect.set(w*0.35f,h-185,w*0.68f,h-105);
            manageRect.set(w*0.69f,h-185,w-20,h-105);
            fill(c,open?C("#7A3B37"):C("#4F8746"),openRect,20);
            fill(c,C("#8A6543"),shapeRect,20);
            fill(c,C("#586879"),manageRect,20);
            txt(c,open?"장사 마감":"영업 시작",openRect.centerX(),openRect.centerY()+10,24,Color.WHITE,Paint.Align.CENTER);
            txt(c,"모양잡기 "+queue+"개",shapeRect.centerX(),shapeRect.centerY()+10,22,Color.WHITE,Paint.Align.CENTER);
            txt(c,"가게 관리",manageRect.centerX(),manageRect.centerY()+10,22,Color.WHITE,Paint.Align.CENTER);

            if(mixing) drawMixOverlay(c,w,h);
            if(manage) drawManageOverlay(c,w,h);

            postInvalidateDelayed(120);
        }

        void drawDough(Canvas c, RectF r){
            fill(c,C("#35251E"),r,24); stroke(c,C("#6E4F3D"),r,24,3);
            txt(c,"🥣 반죽",r.centerX(),r.top+48,27,C("#FFE2A7"),Paint.Align.CENTER);
            // steel bowl
            RectF bowl=new RectF(r.left+22,r.top+105,r.right-22,r.top+235);
            p.setShader(new LinearGradient(0,bowl.top,0,bowl.bottom,C("#D8D5D0"),C("#918C86"),Shader.TileMode.CLAMP));
            c.drawOval(bowl,p); p.setShader(null);
            RectF batter=new RectF(bowl.left+12,bowl.top+30,bowl.right-12,bowl.bottom-22);
            fill(c,C("#EBCB8A"),batter,50);
            txt(c,fermenting?"숙성 중":dough>0?dough+"개 분량":"반죽 없음",r.centerX(),r.top+290,21,Color.WHITE,Paint.Align.CENTER);
            RectF b=new RectF(r.left+14,r.bottom-120,r.right-14,r.bottom-50);
            fill(c,C("#CC673D"),b,17); txt(c,"반죽 만들기",b.centerX(),b.centerY()+8,19,Color.WHITE,Paint.Align.CENTER);
        }

        void drawGrill(Canvas c,RectF r){
            fill(c,C("#342F2B"),r,24); stroke(c,C("#716A63"),r,24,5);
            txt(c,"🔥 붕어빵틀",r.centerX(),r.top+48,27,C("#FFE2A7"),Paint.Align.CENTER);
            float pad=22, top=r.top+78;
            float cellW=(r.width()-pad*2-12)/2, cellH=(r.height()-125-12)/2;
            for(int i=0;i<4;i++){
                int row=i/2,col=i%2;
                RectF m=new RectF(r.left+pad+col*(cellW+12),top+row*(cellH+12),r.left+pad+col*(cellW+12)+cellW,top+row*(cellH+12)+cellH);
                drawMold(c,m,i);
            }
        }

        void drawMold(Canvas c,RectF r,int i){
            fill(c,C("#24211F"),r,22); stroke(c,stage[i]==3||stage[i]==5?C("#FFD46E"):C("#615C57"),r,22,stage[i]==3||stage[i]==5?6:3);
            Path fish=fishPath(r.left+12,r.top+18,r.width()-24,r.height()-48);
            int col=C("#2B2825");
            if(stage[i]==1) col=C("#EFD5A5");
            if(stage[i]==2) col=C("#D79A4E");
            if(stage[i]==3||stage[i]==4||stage[i]==5) col=C("#C87531");
            if(stage[i]==6) col=C("#3A2118");
            p.setColor(col);p.setStyle(Paint.Style.FILL);c.drawPath(fish,p);
            if(stage[i]>0 && stage[i]<6){
                p.setStyle(Paint.Style.STROKE);p.setColor(C("#805123"));p.setStrokeWidth(2);
                for(int k=0;k<4;k++) c.drawArc(new RectF(r.left+25+k*12,r.top+45,r.left+50+k*12,r.top+70),200,110,false,p);
                p.setStyle(Paint.Style.FILL);
            }
            String s=stage[i]==0?"빈 틀":stage[i]==1?"고명":stage[i]==2?"굽는 중":stage[i]==3?"뒤집기!":stage[i]==4?"굽는 중":stage[i]==5?"꺼내기!":"탐";
            txt(c,s,r.centerX(),r.bottom-12,18,Color.WHITE,Paint.Align.CENTER);
        }

        Path fishPath(float x,float y,float w,float h){
            Path q=new Path();
            q.moveTo(x+w*0.05f,y+h*0.52f);
            q.cubicTo(x+w*0.18f,y+h*0.12f,x+w*0.58f,y+h*0.10f,x+w*0.72f,y+h*0.35f);
            q.lineTo(x+w*0.92f,y+h*0.18f);q.lineTo(x+w*0.84f,y+h*0.48f);q.lineTo(x+w*0.94f,y+h*0.78f);q.lineTo(x+w*0.72f,y+h*0.64f);
            q.cubicTo(x+w*0.55f,y+h*0.91f,x+w*0.18f,y+h*0.88f,x+w*0.05f,y+h*0.52f);q.close();return q;
        }

        void drawCase(Canvas c,RectF r){
            fill(c,C("#2F2925"),r,24); stroke(c,C("#6E4F3D"),r,24,3);
            txt(c,"🪟 판매대",r.centerX(),r.top+48,27,C("#FFE2A7"),Paint.Align.CENTER);
            RectF glass=new RectF(r.left+15,r.top+80,r.right-15,r.bottom-120);
            fill(c,C("#314A52"),glass,18); stroke(c,C("#B6D7DF"),glass,18,3);
            int cols=2;
            for(int i=0;i<display;i++){
                int rr=i/cols, cc=i%cols;
                float iw=(glass.width()-24)/2, ih=55;
                RectF item=new RectF(glass.left+8+cc*(iw+8),glass.top+16+rr*(ih+8),glass.left+8+cc*(iw+8)+iw,glass.top+16+rr*(ih+8)+ih);
                drawMiniFish(c,item);
            }
            txt(c,display+" / "+caseCapacity,r.centerX(),r.bottom-82,20,C("#E7D3BE"),Paint.Align.CENTER);
            txt(c,"완성품",r.centerX(),r.bottom-48,18,C("#BFD8DF"),Paint.Align.CENTER);
        }

        void drawMiniFish(Canvas c,RectF r){
            Path f=fishPath(r.left,r.top,r.width(),r.height());
            p.setColor(C("#D58A3C"));c.drawPath(f,p);
        }

        void drawMixOverlay(Canvas c,float w,float h){
            p.setColor(0xCC000000);c.drawRect(0,0,w,h,p);
            RectF card=new RectF(w*0.12f,h*0.24f,w*0.88f,h*0.72f);
            fill(c,C("#3A2921"),card,30);stroke(c,C("#8A654A"),card,30,3);
            txt(c,"🥣 반죽 만들기",card.centerX(),card.top+65,34,Color.WHITE,Paint.Align.CENTER);
            RectF bowl=new RectF(card.left+90,card.top+120,card.right-90,card.top+330);
            fill(c,C("#B7B2AC"),bowl,100);RectF bat=new RectF(bowl.left+18,bowl.top+45,bowl.right-18,bowl.bottom-30);fill(c,C("#EBCB8A"),bat,70);
            txt(c,"거품기로 섞기  "+mixCount+"/5",card.centerX(),card.top+390,26,C("#FFE2A7"),Paint.Align.CENTER);
            mixButton.set(card.left+45,card.bottom-150,card.right-45,card.bottom-82);fill(c,C("#CC673D"),mixButton,18);txt(c,"반죽 섞기",mixButton.centerX(),mixButton.centerY()+9,24,Color.WHITE,Paint.Align.CENTER);
            closeMix.set(card.left+45,card.bottom-70,card.right-45,card.bottom-20);fill(c,C("#596A78"),closeMix,16);txt(c,"닫기",closeMix.centerX(),closeMix.centerY()+8,21,Color.WHITE,Paint.Align.CENTER);
        }

        void drawManageOverlay(Canvas c,float w,float h){
            p.setColor(0xCC000000);c.drawRect(0,0,w,h,p);
            RectF card=new RectF(w*0.08f,h*0.18f,w*0.92f,h*0.82f);
            fill(c,C("#382820"),card,30);stroke(c,C("#7A5842"),card,30,3);
            txt(c,"🔧 가게 관리",card.centerX(),card.top+65,34,Color.WHITE,Paint.Align.CENTER);
            String[] lines={"반죽량  "+doughCapacity+"개","진열장  "+caseCapacity+"개","붕어빵틀  "+moldCount+"구","현재 자금  "+fmt(cash)+"원"};
            for(int i=0;i<lines.length;i++) txt(c,lines[i],card.left+55,card.top+140+i*70,26,C("#F4D6A2"),Paint.Align.LEFT);
            RectF up1=new RectF(card.left+45,card.bottom-210,card.right-45,card.bottom-140);
            RectF up2=new RectF(card.left+45,card.bottom-125,card.right-45,card.bottom-55);
            fill(c,C("#8B6038"),up1,18);fill(c,C("#596A78"),up2,18);
            txt(c,"반죽량 업그레이드 · 6,000원",up1.centerX(),up1.centerY()+8,21,Color.WHITE,Paint.Align.CENTER);
            txt(c,"닫기",up2.centerX(),up2.centerY()+8,23,Color.WHITE,Paint.Align.CENTER);
        }

        void updateTimers(){
            long now=System.currentTimeMillis();
            if(fermenting && now-fermentStart>6000){fermenting=false;dough=doughCapacity;}
            for(int i=0;i<4;i++){
                if(stage[i]==2 && now-timer[i]>3500){stage[i]=3;timer[i]=now;}
                else if(stage[i]==3 && now-timer[i]>5200){stage[i]=6;}
                else if(stage[i]==4 && now-timer[i]>2800){stage[i]=5;timer[i]=now;}
                else if(stage[i]==5 && now-timer[i]>5200){stage[i]=6;}
            }
            if(open && !customer && now-lastCustomer>2500){customer=true;order="팥";orderQty=1+rnd.nextInt(2);lastCustomer=now;}
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;
            float x=e.getX(), y=e.getY();
            if(mixing){
                if(mixButton.contains(x,y)){mixCount++;playClick();if(mixCount>=5){mixing=false;fermenting=true;fermentStart=System.currentTimeMillis();}}
                else if(closeMix.contains(x,y)) mixing=false;
                invalidate();return true;
            }
            if(manage){
                float w=getWidth(),h=getHeight();RectF card=new RectF(w*0.08f,h*0.18f,w*0.92f,h*0.82f);
                RectF up1=new RectF(card.left+45,card.bottom-210,card.right-45,card.bottom-140);
                RectF up2=new RectF(card.left+45,card.bottom-125,card.right-45,card.bottom-55);
                if(up1.contains(x,y)&&cash>=6000){cash-=6000;doughCapacity=Math.min(40,doughCapacity+6);}
                if(up2.contains(x,y))manage=false;invalidate();return true;
            }
            if(doughRect.contains(x,y)){if(!fermenting && dough==0){if(cash>=500){cash-=500;mixing=true;mixCount=0;}}invalidate();return true;}
            if(grillRect.contains(x,y)){handleMoldTap(x,y);invalidate();return true;}
            if(sellRect.contains(x,y)){if(customer && display>=orderQty){display-=orderQty;cash+=700*orderQty;customer=false;lastCustomer=System.currentTimeMillis();playCoin();}invalidate();return true;}
            if(openRect.contains(x,y)){open=!open;if(!open)customer=false;lastCustomer=0;invalidate();return true;}
            if(shapeRect.contains(x,y)){int moved=Math.min(queue,caseCapacity-display);queue-=moved;display+=moved;invalidate();return true;}
            if(manageRect.contains(x,y)){manage=true;invalidate();return true;}
            return true;
        }

        void handleMoldTap(float x,float y){
            float pad=22, top=grillRect.top+78;
            float cellW=(grillRect.width()-pad*2-12)/2, cellH=(grillRect.height()-125-12)/2;
            for(int i=0;i<4;i++){
                int row=i/2,col=i%2;
                RectF m=new RectF(grillRect.left+pad+col*(cellW+12),top+row*(cellH+12),grillRect.left+pad+col*(cellW+12)+cellW,top+row*(cellH+12)+cellH);
                if(m.contains(x,y)){
                    long now=System.currentTimeMillis();
                    if(stage[i]==0){if(dough>0){dough--;stage[i]=1;}}
                    else if(stage[i]==1){stage[i]=2;timer[i]=now;}
                    else if(stage[i]==3){stage[i]=4;timer[i]=now;playMetal();}
                    else if(stage[i]==5){stage[i]=0;queue++;playClick();}
                    else if(stage[i]==6){stage[i]=0;playClick();}
                    return;
                }
            }
        }

        void playClick(){ try { new ToneGenerator(AudioManager.STREAM_MUSIC,55).startTone(ToneGenerator.TONE_PROP_BEEP,45); } catch(Exception ignored){} }
        void playCoin(){ try { new ToneGenerator(AudioManager.STREAM_MUSIC,65).startTone(ToneGenerator.TONE_PROP_ACK,70); } catch(Exception ignored){} }
        void playMetal(){
            new Thread(() -> {
                try{
                    int sr=22050,n=(int)(sr*0.16);short[] data=new short[n];
                    Random r=new Random();
                    for(int i=0;i<n;i++){
                        double t=i/(double)sr;
                        double env=Math.exp(-t*28.0);
                        double s=Math.sin(2*Math.PI*1700*t)*0.45+Math.sin(2*Math.PI*920*t)*0.28+(r.nextDouble()*2-1)*0.22;
                        if(t>0.065){double tt=t-0.065;s+=Math.sin(2*Math.PI*1250*tt)*0.42*Math.exp(-tt*35);}
                        data[i]=(short)(Math.max(-1,Math.min(1,s*env))*22000);
                    }
                    AudioTrack at=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,n*2,AudioTrack.MODE_STATIC);
                    at.write(data,0,n);at.play();Thread.sleep(220);at.release();
                }catch(Exception ignored){}
            }).start();
        }
        String fmt(int n){return String.format(Locale.KOREA,"%,d",n);}
    }
}
