package de.viergewinnt.ui;

import de.viergewinnt.renderer.RenderSettings;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared layout for drawing and hit testing, independent of GLFW/OpenGL. */
public final class PauseMenu {
    public static final int WIDTH=960,HEIGHT=660;
    public enum Action { NONE, RESUME, RESTART, QUIT, SETTINGS }
    public record Control(String id,String label,int x,int y,int width,int height,boolean enabled) {
        boolean contains(double px,double py) { return px>=x&&px<x+width&&py>=y&&py<y+height; }
    }
    private static final int[][] RESOLUTIONS={{640,360},{960,540},{1280,720},{1920,1080},{3840,2160}};
    private static final int[] SAMPLES={1,2,4,8,16};
    private RenderSettings settings;
    private boolean graphics,dirty=true;
    private String focus="",status="",saveStatus="Änderungen werden automatisch gespeichert";
    public PauseMenu(RenderSettings settings) { this.settings=settings; }
    public RenderSettings settings() { return settings; }
    public boolean isGraphics() { return graphics; }
    public void open(String gameStatus) { graphics=false;focus="resume";status=gameStatus;dirty=true; }
    public boolean back() { if(!graphics) return false;graphics=false;focus="graphics";dirty=true;return true; }
    public void saved(boolean success) { saveStatus=success?"Gespeichert · Änderungen wirken sofort":"Nur für diese Sitzung · Speichern fehlgeschlagen";dirty=true; }
    public boolean takeDirty() { boolean result=dirty;dirty=false;return result; }
    public static float scale(int framebufferWidth,int framebufferHeight) {
        return Math.min((framebufferWidth-24f)/WIDTH,(framebufferHeight-24f)/HEIGHT);
    }
    public static double[] panelPoint(double cursorX,double cursorY,int windowWidth,int windowHeight,int framebufferWidth,int framebufferHeight) {
        if(windowWidth<=0||windowHeight<=0||framebufferWidth<=24||framebufferHeight<=24) return new double[]{-1,-1};
        float scale=scale(framebufferWidth,framebufferHeight);
        return new double[]{(cursorX/windowWidth*framebufferWidth-(framebufferWidth-WIDTH*scale)/2)/scale,
            (cursorY/windowHeight*framebufferHeight-(framebufferHeight-HEIGHT*scale)/2)/scale};
    }
    public List<Control> controls() {
        List<Control> c=new ArrayList<>();
        c.add(new Control("home","Spiel",24,190,190,48,true));
        c.add(new Control("graphics","Grafik",24,248,190,48,true));
        if(!graphics) {
            c.add(new Control("resume","Weiterspielen",276,222,640,68,true));
            c.add(new Control("restart","Neues Spiel",276,308,640,58,true));
            c.add(new Control("quit","Spiel beenden",276,382,640,58,true));
        } else {
            c.add(new Control("denoiser",denoiserLabel(),580,174,336,44,true));
            c.add(new Control("taa",settings.taa()?"TAA · An":"TAA · Aus",580,230,336,44,true));
            pair(c,"strength",286,settings.denoiseStrength()>.25f&&settings.denoiser()!=RenderSettings.Denoiser.OFF,
                settings.denoiseStrength()<2&&settings.denoiser()!=RenderSettings.Denoiser.OFF);
            pair(c,"bounces",342,settings.bounces()>1,settings.bounces()<8);
            pair(c,"samples",398,settings.samplesPerFrame()>1,settings.samplesPerFrame()<16);
            pair(c,"resolution",454,settings.maxWidth()>640,settings.maxWidth()<3840);
            pair(c,"exposure",510,settings.exposure()>.25f,settings.exposure()<3);
            c.add(new Control("defaults","Standardwerte",276,590,200,42,true));
            c.add(new Control("resume","Weiterspielen",716,590,200,42,true));
        }
        return c;
    }
    private static void pair(List<Control> c,String id,int y,boolean minus,boolean plus) {
        c.add(new Control(id+"-","−",658,y,44,44,minus));c.add(new Control(id+"+","+",872,y,44,44,plus));
    }
    public void hover(double x,double y) {
        String next="";for(Control c:controls()) if(c.enabled&&c.contains(x,y)) { next=c.id;break; }
        if(!next.equals(focus)) { focus=next;dirty=true; }
    }
    public void focusNext(int direction) {
        List<Control> c=controls().stream().filter(Control::enabled).toList();
        int i=-1;for(int n=0;n<c.size();n++) if(c.get(n).id.equals(focus)) i=n;
        if(i<0&&direction<0) i=0;
        focus=c.get(Math.floorMod(i+direction,c.size())).id;dirty=true;
    }
    public Action click(double x,double y) {
        for(Control c:controls()) if(c.enabled&&c.contains(x,y)) return activate(c.id);
        return Action.NONE;
    }
    public Action activateFocused() { return activate(focus); }
    private Action activate(String id) {
        if(id.equals("resume")) return Action.RESUME;
        if(id.equals("restart")) return Action.RESTART;
        if(id.equals("quit")) return Action.QUIT;
        if(id.equals("home")||id.equals("graphics")) { graphics=id.equals("graphics");focus="";dirty=true;return Action.NONE; }
        RenderSettings before=settings;
        int sign=id.endsWith("-")?-1:1;
        if(id.equals("denoiser")) settings=settings.withDenoiser(RenderSettings.Denoiser.values()[(settings.denoiser().ordinal()+1)%3]);
        else if(id.equals("taa")) settings=settings.withTaa(!settings.taa());
        else if(id.startsWith("strength")) settings=settings.withStrength(clamp(settings.denoiseStrength()+sign*.25f,.25f,2));
        else if(id.startsWith("bounces")) settings=settings.withBounces(Math.max(1,Math.min(8,settings.bounces()+sign)));
        else if(id.startsWith("samples")) settings=settings.withSamples(nextValue(SAMPLES,settings.samplesPerFrame(),sign));
        else if(id.startsWith("resolution")) {
            int[] widths={640,960,1280,1920,3840};int width=nextValue(widths,settings.maxWidth(),sign);
            for(int[] r:RESOLUTIONS) if(r[0]==width) settings=settings.withResolution(r[0],r[1]);
        } else if(id.startsWith("exposure")) settings=settings.withExposure(clamp(settings.exposure()+sign*.25f,.25f,3));
        else if(id.equals("defaults")) settings=RenderSettings.defaults();
        if(!settings.equals(before)) { dirty=true;return Action.SETTINGS; }
        return Action.NONE;
    }
    private static float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
    private static int nextValue(int[] values,int current,int direction) {
        if(direction>0) { for(int v:values) if(v>current) return v;return values[values.length-1]; }
        for(int i=values.length-1;i>=0;i--) if(values[i]<current) return values[i];return values[0];
    }
    private String denoiserLabel() {
        return switch(settings.denoiser()) { case OFF -> "Aus";case OWN -> "Eigener · Bilateral";case ATROUS -> "À-Trous · LWJGL"; };
    }
    private static final Color TEXT=new Color(234,239,245),MUTED=new Color(149,164,180),ACCENT=new Color(233,185,99);
    private static void text(Graphics2D g,String s,int x,int baseline,int size,Color color,boolean bold) {
        g.setFont(new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,size));g.setColor(color);g.drawString(s,x,baseline);
    }
    private static void rounded(Graphics2D g,int x,int y,int w,int h,int radius,Color color) {
        g.setColor(color);g.fillRoundRect(x,y,w,h,radius,radius);
    }
    public BufferedImage image(int density) {
        BufferedImage image=new BufferedImage(WIDTH*density,HEIGHT*density,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.scale(density,density);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        rounded(g,3,5,954,652,30,new Color(5,10,17,220));
        g.setPaint(new GradientPaint(0,0,new Color(23,34,47),960,660,new Color(12,20,30)));
        g.fillRoundRect(4,4,952,648,28,28);
        g.setColor(new Color(45,58,71));g.drawLine(238,30,238,626);
        rounded(g,26,32,56,56,18,ACCENT);text(g,"4",43,72,34,new Color(25,30,38),true);
        text(g,"GEWINNT",94,59,19,TEXT,true);text(g,"PATHTRACED",94,80,11,MUTED,true);
        text(g,"PAUSE",28,155,13,ACCENT,true);
        text(g,"ESC",28,592,13,TEXT,true);text(g,graphics?"Zurück":"Weiterspielen",70,592,13,MUTED,false);
        text(g,"TAB + ENTER",28,617,12,MUTED,false);
        text(g,graphics?"Grafik & Pathtracing":"Spiel pausiert",276,78,32,TEXT,true);
        text(g,graphics?"Dein Bild. Deine Balance aus Qualität und Tempo.":"Nimm dir Zeit. Dein Spiel bleibt erhalten.",276,111,17,MUTED,false);
        rounded(g,276,137,640,2,0,new Color(51,65,80));
        if(graphics) {
            String[] labels={"Denoiser","Kantenglättung","Filterstärke","Path-Bounces","Samples / Frame","Render-Auflösung","Belichtung"};
            String[] descriptions={"Klicken, um den Filter zu wechseln","Temporale Glättung · kein Upscaler","Mehr Glättung oder mehr Details","Maximale Lichtpfadlänge","Mehr Samples reduzieren das Rauschen","Obergrenze · Fensterformat bleibt erhalten","Helligkeit nach dem Pathtracing"};
            for(int i=0;i<7;i++) {
                int y=174+i*56;
                text(g,labels[i],276,y+18,17,TEXT,true);text(g,descriptions[i],276,y+40,12,MUTED,false);
            }
            String[] values={String.format(Locale.ROOT,"%.0f %%",settings.denoiseStrength()*100),Integer.toString(settings.bounces()),
                Integer.toString(settings.samplesPerFrame()),settings.maxWidth()+" × "+settings.maxHeight(),String.format(Locale.ROOT,"%.2f×",settings.exposure())};
            for(int i=0;i<5;i++) {
                int y=286+i*56;rounded(g,710,y,154,44,12,new Color(17,27,39));
                g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,17));int tw=g.getFontMetrics().stringWidth(values[i]);
                text(g,values[i],787-tw/2,y+28,17,TEXT,true);
            }
            text(g,saveStatus,276,567,12,MUTED,false);
        } else {
            text(g,status,276,184,18,ACCENT,true);
            text(g,"AM SPIELTISCH",276,500,12,ACCENT,true);
            text(g,"WASD",276,535,18,TEXT,true);text(g,"Bewegen",350,535,15,MUTED,false);
            text(g,"MAUS",556,535,18,TEXT,true);text(g,"Umsehen",634,535,15,MUTED,false);
            text(g,"1 – 7",276,571,18,TEXT,true);text(g,"Stein einwerfen",350,571,15,MUTED,false);
            text(g,"Grafikoptionen findest du links unter Grafik.",276,620,14,MUTED,false);
        }
        for(Control c:controls()) {
            boolean nav=c.id.equals("home")||c.id.equals("graphics");
            boolean selected=nav&&(graphics==c.id.equals("graphics"));
            boolean hover=c.id.equals(focus),primary=c.id.equals("resume");
            Color background=primary?ACCENT:selected?new Color(49,64,79):hover?new Color(52,68,85):new Color(30,43,58);
            if(!c.enabled) background=new Color(23,31,40);
            rounded(g,c.x,c.y,c.width,c.height,14,background);
            if(hover&&c.enabled) { g.setColor(new Color(241,207,148));g.setStroke(new BasicStroke(2));g.drawRoundRect(c.x+1,c.y+1,c.width-2,c.height-2,14,14); }
            int fontSize=c.label.length()==1?24:17;
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,fontSize));int tw=g.getFontMetrics().stringWidth(c.label);
            text(g,c.label,nav?c.x+18:c.x+(c.width-tw)/2,c.y+(c.height+fontSize)/2-3,fontSize,
                !c.enabled?new Color(76,89,103):primary?new Color(24,30,39):TEXT,true);
        }
        g.dispose();return image;
    }
}
