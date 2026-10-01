package com.bungeoppang.king;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.*;
import android.media.*;
import java.util.*;

public class MainActivity extends Activity {
    MeatView game;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        game = new MeatView(this);
        setContentView(game);
    }

    static class TableState {
        int phase; // 0 empty, 1 order, 2 cooking/eating, 3 payment
        boolean selfCook;
        int meat; // 0 pork belly, 1 neck
        int doneness;
        boolean flipped, cut, sideOrdered;
        int side = -1;
        int subtotal;
        long started;
        String guest = "";
        String request = "";
    }

    public class MeatView extends View {
        final int VW=432, VH=960;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random rnd = new Random();
        final ArrayList<Hot> hot = new ArrayList<>();
        final TableState[] tables = new TableState[4];
        final String[] guests = {"퇴근길 직장인","동네 단골 부부","친구 모임","가족 손님","데이트 손님","회식팀"};
        final String[] meats = {"삼겹살","목살"};
        final String[] sides = {"된장찌개","김치찌개","물냉면","비빔냉면","공깃밥"};
        float scale=1, offX=0, offY=0;
        int selected=0, money=32000, sales=0, rep=8, pork=18, neck=12, rice=12, day=1;
        String note="어서오세요. 오늘도 고기입니다!";
        long noteAt=System.currentTimeMillis();

        class Hot {
            RectF r; Runnable action;
            Hot(float x,float y,float w,float h,Runnable a){r=new RectF(x,y,x+w,y+h);action=a;}
        }

        MeatView(Context c){
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            for(int i=0;i<4;i++){tables[i]=new TableState();}
            seat(0); seat(1);
        }

        int c(String s){return Color.parseColor(s);}
        void fill(Canvas x,int color,float l,float t,float r,float b){p.setStyle(Paint.Style.FILL);p.setColor(color);x.drawRect(l,t,r,b,p);}
        void rr(Canvas x,int color,float l,float t,float r,float b,float rad){p.setStyle(Paint.Style.FILL);p.setColor(color);x.drawRoundRect(new RectF(l,t,r,b),rad,rad,p);}
        void line(Canvas x,int color,float sw,float x1,float y1,float x2,float y2){p.setColor(color);p.setStrokeWidth(sw);p.setStyle(Paint.Style.STROKE);x.drawLine(x1,y1,x2,y2,p);p.setStyle(Paint.Style.FILL);}
        void txt(Canvas x,String s,float px,float py,float size,int color,Paint.Align a,boolean bold){
            p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
            p.setTextSize(size); p.setColor(color); p.setTextAlign(a); p.setStyle(Paint.Style.FILL); x.drawText(s,px,py,p);
        }
        void say(String s){note=s;noteAt=System.currentTimeMillis();invalidate();}

        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            scale=Math.min(w/(float)VW,h/(float)VH); offX=(w-VW*scale)/2f; offY=(h-VH*scale)/2f;
        }

        @Override protected void onDraw(Canvas real){
            super.onDraw(real);
            real.save(); real.translate(offX,offY); real.scale(scale,scale);
            tick(); drawScene(real); real.restore();
            postInvalidateDelayed(160);
        }

        void drawScene(Canvas x){
            hot.clear();
            drawBackground(x);
            drawHeader(x);
            float[] xs={16,220,16,220}, ys={228,228,476,476};
            for(int i=0;i<4;i++) drawTable(x,i,xs[i],ys[i]);
            drawControls(x);
            if(System.currentTimeMillis()-noteAt<3000){
                rr(x,Color.argb(235,24,17,13),26,700,406,735,17);
                txt(x,note,216,723,10,Color.WHITE,Paint.Align.CENTER,true);
            }
        }

        void drawBackground(Canvas x){
            fill(x,c("#231711"),0,0,VW,VH);
            fill(x,c("#DFC18F"),0,0,VW,325);
            for(int i=0;i<54;i++){
                p.setColor(Color.argb(18,70,45,30)); p.setStrokeWidth(1);
                float ax=(i*79)%VW, ay=(i*41)%310;
                x.drawLine(ax,ay,ax+9+(i%17),ay+(i%5)-2,p);
            }
            rr(x,c("#4A2B1C"),10,72,134,128,5);
            rr(x,c("#F0D3A3"),16,78,128,122,3);
            txt(x,"삼겹살 13,000",72,96,12,c("#5A2A1D"),Paint.Align.CENTER,true);
            txt(x,"목살    13,000",72,114,11,c("#5A2A1D"),Paint.Align.CENTER,false);
            rr(x,c("#571F18"),298,72,422,128,5);
            txt(x,"오늘도 고기",360,101,17,c("#F4D88B"),Paint.Align.CENTER,true);
            txt(x,"불판은 뜨겁게 · 마음은 정겹게",360,118,8,c("#F7E8C8"),Paint.Align.CENTER,false);
            fill(x,c("#35515A"),145,78,287,164);
            fill(x,c("#A7C4C9"),151,84,281,158);
            for(int i=0;i<4;i++){
                float ex=56+i*106;
                fill(x,c("#74716B"),ex,152,ex+12,214);
                p.setColor(c("#98958F")); x.drawOval(new RectF(ex-18,205,ex+30,226),p);
            }
            fill(x,c("#684830"),0,325,VW,VH);
            for(int yy=325;yy<850;yy+=48) line(x,c("#7D583C"),1,0,yy,VW,yy);
            for(int xx=0;xx<VW;xx+=72) line(x,c("#573A28"),1,xx,325,xx,850);
        }

        void drawHeader(Canvas x){
            rr(x,Color.argb(235,28,19,15),8,8,424,62,14);
            txt(x,"오늘도 고기",22,33,20,c("#FFE7B0"),Paint.Align.LEFT,true);
            txt(x,"DAY "+day,22,51,10,c("#D8BA8A"),Paint.Align.LEFT,false);
            stat(x,"매출",won(sales),190); stat(x,"보유",won(money),286); stat(x,"평판","★ "+rep,378);
        }
        void stat(Canvas x,String k,String v,float px){
            txt(x,k,px,25,8,c("#BFA47E"),Paint.Align.CENTER,false);
            txt(x,v,px,45,11,c("#FFF2D5"),Paint.Align.CENTER,true);
        }
        String won(int n){return String.format(Locale.KOREA,"%,d원",n);}

        void drawTable(Canvas x,int idx,float tx,float ty){
            TableState t=tables[idx];
            boolean sel=idx==selected;
            rr(x,c(sel?"#D4A85E":"#9B734C"),tx,ty,tx+194,ty+224,18);
            rr(x,c("#3D291D"),tx+6,ty+7,tx+188,ty+217,15);
            rr(x,c("#B88958"),tx+11,ty+12,tx+183,ty+212,13);
            txt(x,(idx+1)+"번",tx+20,ty+31,11,c("#3A2218"),Paint.Align.LEFT,true);
            txt(x,t.phase==0?"빈 자리":t.guest,tx+174,ty+31,9,c("#4D3022"),Paint.Align.RIGHT,false);
            drawGrill(x,tx+35,ty+47,t);
            if(t.phase==0){
                txt(x,"빈 테이블",tx+97,ty+130,13,c("#694833"),Paint.Align.CENTER,true);
            } else {
                txt(x,t.selfCook?"손님 셀프구이":"직원 구이",tx+97,ty+155,10,c("#42281C"),Paint.Align.CENTER,true);
                txt(x,t.request,tx+97,ty+174,9,c("#5C3A29"),Paint.Align.CENTER,false);
                if(t.side>=0) drawSide(x,tx+136,ty+108,t.side);
                String status=t.phase==1?"주문 대기":(t.phase==2?"식사 중":"계산 대기");
                txt(x,status,tx+97,ty+196,9,c("#4E2E20"),Paint.Align.CENTER,true);
            }
            hot.add(new Hot(tx,ty,194,224,()->{selected=idx;say((idx+1)+"번 테이블을 선택했습니다");}));
        }

        void drawGrill(Canvas x,float gx,float gy,TableState t){
            RadialGradient metal=new RadialGradient(gx+62,gy+52,68,new int[]{c("#777A77"),c("#3F4240"),c("#181918")},null,Shader.TileMode.CLAMP);
            p.setShader(metal); x.drawOval(new RectF(gx,gy,gx+124,gy+104),p); p.setShader(null);
            p.setColor(c("#161716")); x.drawOval(new RectF(gx+8,gy+8,gx+116,gy+96),p);
            for(int i=0;i<8;i++) line(x,Color.argb(90,170,170,160),1,gx+18,gy+18+i*9,gx+106,gy+18+i*9);
            if(t.phase>0){
                int d=t.doneness;
                drawMeat(x,gx+27,gy+30,t.meat,d,0);
                drawMeat(x,gx+63,gy+46,t.meat,d,1);
                if(d>0){
                    p.setColor(Color.argb(65,255,205,125)); x.drawOval(new RectF(gx+41,gy+67,gx+86,gy+79),p);
                }
                if(d>=1){
                    for(int k=0;k<3;k++){
                        p.setColor(Color.argb(38-k*7,235,235,230));
                        x.drawOval(new RectF(gx+30+k*23,gy+5-k*3,gx+49+k*23,gy+34-k*7),p);
                    }
                }
            }
        }

        void drawMeat(Canvas x,float mx,float my,int type,int d,int seed){
            float w=39,h=21;
            int base;
            if(d==0) base= type==0?c("#E38E84"):c("#C76662");
            else if(d==1) base=c("#C77857");
            else if(d==2) base=c("#A85F40");
            else if(d==3) base=c("#75432F");
            else base=c("#2D211B");
            Path path=new Path();
            path.moveTo(mx,my+h*.25f);
            path.cubicTo(mx+w*.2f,my-2,mx+w*.78f,my+1,mx+w,my+h*.22f);
            path.cubicTo(mx+w-1,my+h*.82f,mx+w*.7f,my+h+2,mx+w*.12f,my+h*.9f);
            path.close();
            p.setColor(base); p.setShadowLayer(2,0,1,Color.argb(110,0,0,0)); x.drawPath(path,p); p.clearShadowLayer();
            p.setColor(d<3?c("#F1D2B4"):c("#AD865F")); p.setStrokeWidth(type==0?2.2f:1.2f);
            x.drawLine(mx+6,my+8,mx+31,my+10,p);
            if(type==0)x.drawLine(mx+5,my+15,mx+29,my+16,p);
            if(d>=2){
                p.setColor(d>=4?c("#100D0B"):c("#4A261A")); p.setStrokeWidth(1.2f);
                x.drawLine(mx+11,my+2,mx+7,my+19,p); x.drawLine(mx+23,my+2,mx+19,my+20,p);
            }
            if(d==1||d==2){p.setColor(Color.argb(95,255,225,155));x.drawOval(new RectF(mx+23,my+4,mx+34,my+10),p);}
        }

        void drawSide(Canvas x,float sx,float sy,int side){
            if(side<=1){
                p.setColor(c("#29201A"));x.drawOval(new RectF(sx,sy,sx+40,sy+29),p);
                p.setColor(side==0?c("#8C6942"):c("#B33B2E"));x.drawOval(new RectF(sx+4,sy+4,sx+36,sy+25),p);
                p.setColor(c("#E9D9B8"));x.drawRect(sx+9,sy+9,sx+16,sy+16,p);x.drawRect(sx+24,sy+13,sx+31,sy+20,p);
                p.setColor(c("#76A15B"));x.drawOval(new RectF(sx+18,sy+8,sx+24,sy+13),p);
            } else if(side<=3){
                p.setColor(c("#BFC2BF"));x.drawOval(new RectF(sx,sy,sx+41,sy+29),p);
                p.setColor(side==2?c("#D4C49E"):c("#A93830"));x.drawOval(new RectF(sx+5,sy+5,sx+36,sy+25),p);
                p.setColor(c("#719C55"));x.drawRect(sx+18,sy+6,sx+22,sy+14,p);
                p.setColor(c("#F0DFB2"));x.drawOval(new RectF(sx+25,sy+8,sx+32,sy+15),p);
            } else {
                p.setColor(c("#D0D0CD"));x.drawOval(new RectF(sx+4,sy+5,sx+35,sy+27),p);
                p.setColor(c("#F8F3E5"));x.drawOval(new RectF(sx+9,sy+8,sx+30,sy+23),p);
            }
        }

        void drawControls(Canvas x){
            rr(x,Color.argb(242,28,19,15),8,744,424,948,16);
            TableState t=tables[selected];
            txt(x,(selected+1)+"번 테이블",22,771,14,c("#FFE3A3"),Paint.Align.LEFT,true);
            txt(x,"삼겹살 "+pork+" · 목살 "+neck+" · 밥 "+rice,22,793,10,c("#E8CBA5"),Paint.Align.LEFT,false);
            btn(x,"고기 내기",18,810,92,44,()->serve());
            btn(x,"뒤집기/자르기",119,810,104,44,()->cook());
            btn(x,"추가 주문",232,810,86,44,()->side());
            btn(x,"계산",327,810,87,44,()->checkout());
            btn(x,"삼겹 +6",18,866,92,42,()->restock(0));
            btn(x,"목살 +6",119,866,92,42,()->restock(1));
            btn(x,"밥 +8",220,866,92,42,()->restock(2));
            btn(x,"새 손님",321,866,93,42,()->{if(tables[selected].phase==0)seat(selected);else say("빈 테이블에서 사용할 수 있습니다");});
            txt(x,"셀프 테이블은 자동으로 굽고, 직원구이는 타이밍에 맞춰 뒤집고 잘라주세요.",216,929,8,c("#C9AD89"),Paint.Align.CENTER,false);
        }

        void btn(Canvas x,String label,float bx,float by,float bw,float bh,Runnable action){
            rr(x,c("#422A1D"),bx,by,bx+bw,by+bh,8);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.3f);p.setColor(c("#D4A657"));x.drawRoundRect(new RectF(bx,by,bx+bw,by+bh),8,8,p);p.setStyle(Paint.Style.FILL);
            txt(x,label,bx+bw/2,by+bh/2+4,10,c("#FFF0D0"),Paint.Align.CENTER,true);
            hot.add(new Hot(bx,by,bw,bh,action));
        }

        void seat(int i){
            TableState t=new TableState();
            t.phase=1; t.selfCook=rnd.nextInt(100)<60; t.meat=rnd.nextInt(2); t.guest=guests[rnd.nextInt(guests.length)];
            t.request=t.selfCook?"저희가 구워 먹을게요":"고기 좀 구워주세요"; t.started=System.currentTimeMillis();
            tables[i]=t; fx(4);
        }

        void serve(){
            TableState t=tables[selected];
            if(t.phase==0){say("빈 테이블입니다");return;}
            if(t.phase!=1){say("이미 고기가 나간 테이블입니다");return;}
            int qty=2;
            if(t.meat==0){if(pork<qty){say("삼겹살 재고가 부족합니다");return;}pork-=qty;}
            else {if(neck<qty){say("목살 재고가 부족합니다");return;}neck-=qty;}
            t.phase=2;t.started=System.currentTimeMillis();t.subtotal+=26000;t.doneness=0;
            say(meats[t.meat]+" 2인분을 불판에 올렸습니다");fx(0);
        }

        void cook(){
            TableState t=tables[selected];
            if(t.phase!=2){say("굽고 있는 고기가 없습니다");return;}
            if(t.selfCook){say("이 테이블은 손님이 직접 굽고 있습니다");return;}
            if(!t.flipped){t.flipped=true;t.doneness=Math.max(1,t.doneness);say("집게로 고기를 뒤집었습니다");fx(1);}
            else if(!t.cut && t.doneness>=2){t.cut=true;t.doneness=2;t.request="먹기 좋게 잘라드렸습니다";rep++;say("가위로 먹기 좋게 잘랐습니다");fx(2);}
            else say("조금 더 익는 중입니다");
        }

        void side(){
            TableState t=tables[selected];
            if(t.phase!=2){say("식사 중인 테이블에서 주문할 수 있습니다");return;}
            if(t.sideOrdered){say("이미 식사 메뉴가 나갔습니다");return;}
            t.side=rnd.nextInt(sides.length);t.sideOrdered=true;
            int[] price={4500,5000,6000,6500,1000};
            if(t.side==4){if(rice<=0){say("공깃밥 재고가 없습니다");t.side=-1;t.sideOrdered=false;return;}rice--;}
            t.subtotal+=price[t.side];t.request=sides[t.side]+" 추가요!";
            say(sides[t.side]+"을 준비했습니다");fx(t.side<=1?5:(t.side<=3?3:6));
            postDelayed(()->{if(t.phase==2)t.phase=3;},2600);
        }

        void checkout(){
            TableState t=tables[selected];
            if(t.phase==0){say("빈 테이블입니다");return;}
            if(t.phase==2)t.phase=3;
            if(t.phase!=3){say("아직 식사 중입니다");return;}
            int tip=(!t.selfCook && t.cut)?1000:0;
            int earn=t.subtotal+tip;money+=earn;sales+=earn;fx(7);
            say(won(earn)+" 결제 완료"+(tip>0?" · 팁 1,000원":""));
            tables[selected]=new TableState();
            final int idx=selected;postDelayed(()->{if(tables[idx].phase==0)seat(idx);},1100);
        }

        void restock(int k){
            int cost=k==2?4000:9000;if(money<cost){say("돈이 부족합니다");return;}money-=cost;
            if(k==0)pork+=6;else if(k==1)neck+=6;else rice+=8;
            say("재고를 보충했습니다");
        }

        void tick(){
            long now=System.currentTimeMillis();
            for(TableState t:tables){
                if(t.phase!=2)continue;
                long e=now-t.started;
                int d=(int)(e/3500);
                t.doneness=Math.min(4,Math.max(t.doneness,d));
                if(t.selfCook && e>8500 && !t.sideOrdered){
                    t.flipped=true;t.cut=true;t.doneness=2;t.request="잘 익혀 먹는 중";
                    if(e>11500){t.phase=3;}
                }
                if(!t.selfCook && t.doneness>=4){rep=Math.max(0,rep-1);t.request="고기가 타고 있어요!";}
            }
        }

        void fx(int kind){
            new Thread(()->{
                try{
                    int sr=22050;double sec=(kind==0?0.55:(kind==3?0.7:0.28));int n=(int)(sr*sec);
                    short[] data=new short[n];Random r=new Random();
                    for(int i=0;i<n;i++){
                        double t=i/(double)sr, env=Math.max(0,1-t/sec);
                        double v;
                        if(kind==0) v=(r.nextDouble()*2-1)*0.8 + Math.sin(2*Math.PI*85*t)*0.08;
                        else if(kind==1) v=Math.sin(2*Math.PI*(1300-700*t/sec)*t)*0.45*env+(r.nextDouble()*2-1)*0.18;
                        else if(kind==2) v=(Math.sin(2*Math.PI*950*t)+Math.sin(2*Math.PI*1450*t))*0.22*env;
                        else if(kind==3) v=Math.sin(2*Math.PI*(280-130*t/sec)*t)*0.22+(r.nextDouble()*2-1)*0.12;
                        else if(kind==5) v=(r.nextDouble()*2-1)*0.18 + Math.sin(2*Math.PI*90*t)*0.08;
                        else if(kind==7) v=Math.sin(2*Math.PI*880*t)*0.16*env;
                        else v=(r.nextDouble()*2-1)*0.14*env;
                        data[i]=(short)(Math.max(-1,Math.min(1,v*env))*32767);
                    }
                    AudioTrack at=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,data.length*2,AudioTrack.MODE_STATIC);
                    at.write(data,0,data.length);at.setVolume(0.28f);at.play();
                    Thread.sleep((long)(sec*1000)+80);at.release();
                }catch(Exception ignored){}
            }).start();
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=(e.getX()-offX)/scale,y=(e.getY()-offY)/scale;
            for(int i=hot.size()-1;i>=0;i--){Hot h=hot.get(i);if(h.r.contains(x,y)){h.action.run();invalidate();return true;}}
            return true;
        }
    }
}
