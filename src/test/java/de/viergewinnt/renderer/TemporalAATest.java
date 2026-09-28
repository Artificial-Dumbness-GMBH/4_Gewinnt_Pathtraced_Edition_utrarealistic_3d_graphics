package de.viergewinnt.renderer;

import de.viergewinnt.scene.*;
import static org.lwjgl.opengl.GL43.*;

/** Real compute-shader tests, called inside RendererSmokeTest's EGL context. */
public final class TemporalAATest {
    private static final int W=32,H=32;
    private static void check(boolean v,String message) { if(!v) throw new AssertionError(message); }
    private static float[] pixels(int texture) {
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);float[] p=new float[W*H*4];
        glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,p);return p;
    }
    private static int texture() {
        int t=glGenTextures();glBindTexture(GL_TEXTURE_2D,t);glTexStorage2D(GL_TEXTURE_2D,1,GL_RGBA32F,W,H);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);return t;
    }
    private static void upload(int texture,float[] data) { glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,W,H,GL_RGBA,GL_FLOAT,data); }
    private static float[] guides(Camera c,float distance,float sign) {
        float[] data=new float[W*H*4];Vec3 n=c.forward().mul(-sign);
        for(int y=0;y<H;y++) for(int x=0;x<W;x++) {
            Vec3 ray=c.forward().add(c.right().mul(((x+.5f)/W*2-1)*.57735026919f)).add(c.up().mul(((y+.5f)/H*2-1)*.57735026919f)).normalized();
            int i=4*(y*W+x);data[i]=n.x;data[i+1]=n.y;data[i+2]=n.z;data[i+3]=distance/ray.dot(c.forward());
        }return data;
    }
    private static float halton(int n,int base) { float f=1,result=0;while(n>0){f/=base;result+=f*(n%base);n/=base;}return result; }
    private static float[] edge(float jx,float jy) {
        float[] data=new float[W*H*4];for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            float v=x+jx+y+jy<31.4f?1:0;int i=4*(y*W+x);data[i]=data[i+1]=data[i+2]=v;data[i+3]=1;
        }return data;
    }
    private static double error(float[] data) {
        double error=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            double coverage=0;for(int j=0;j<32;j++)for(int i=0;i<32;i++) if(x+(i+.5)/32+y+(j+.5)/32<31.4)coverage+=1.0/1024;
            error+=Math.pow(data[4*(y*W+x)]-coverage,2);
        }return error;
    }
    public static void run() throws Exception {
        Camera camera=new Camera();int color=texture(),guide=texture(),albedo=texture();
        float[] material=new float[W*H*4];upload(albedo,material);upload(guide,guides(camera,10,1));
        try(TemporalAA taa=new TemporalAA()) {
            double rawError=0,taaError=0;
            for(int frame=1;frame<=64;frame++) {
                float[] raw=edge(halton(frame,2),halton(frame,3));upload(color,raw);
                float[] filtered=pixels(taa.resolve(color,guide,albedo,W,H,camera,1));
                if(frame>16) { rawError+=error(raw);taaError+=error(filtered); }
            }
            check(taaError<rawError*.5,"TAA did not reduce subpixel edge error");
            System.out.printf(java.util.Locale.ROOT,"TAA subpixel edge error reduced by %.1f%% against 32x32 coverage reference.%n",100*(1-taaError/rawError));
            // One-pixel camera translation: reproject to the adjacent history texel.
            float[] history=new float[W*H*4],current=new float[history.length];
            for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
                int i=4*(y*W+x);for(int c=0;c<3;c++) { history[i+c]=(x%2==0)?.8f:.2f;current[i+c]=(x+y)%2; }
            }
            int center=4*(16*W+16);current[center]=current[center+1]=current[center+2]=.4f;
            taa.reset();upload(color,history);taa.resolve(color,guide,albedo,W,H,camera,1);
            Camera moved=new Camera();var position=Camera.class.getDeclaredField("position");position.setAccessible(true);
            position.set(moved,camera.position().add(camera.right().mul(2*.57735026919f*10/W)));
            upload(color,current);float[] shifted=pixels(taa.resolve(color,guide,albedo,W,H,moved,1));
            check(Math.abs(shifted[center]-(.4f*.15f+.2f*.85f))<.001,"camera reprojection is not a one-pixel shift: "+shifted[center]);
            // Invalid surface history must never bleed onto new geometry/materials.
            for(int scenario=0;scenario<4;scenario++) {
                taa.reset();upload(guide,guides(camera,10,1));upload(albedo,new float[material.length]);upload(color,history);
                taa.resolve(color,guide,albedo,W,H,camera,1);upload(color,current);
                if(scenario==0) upload(guide,guides(camera,12,1));
                if(scenario==1) upload(guide,guides(camera,10,-1));
                if(scenario==2) { for(int i=3;i<material.length;i+=4)material[i]=7;upload(albedo,material); }
                if(scenario==3) taa.reset();
                float[] rejected=pixels(taa.resolve(color,guide,albedo,W,H,camera,1));
                for(int i=0;i<rejected.length;i++) if(i%4!=3) check(Math.abs(rejected[i]-current[i])<1e-6,"stale history scenario "+scenario);
            }
            // Uniform current neighborhood removes stale highlights even on the same surface.
            upload(guide,guides(camera,10,1));upload(albedo,new float[material.length]);taa.reset();upload(color,history);
            taa.resolve(color,guide,albedo,W,H,camera,1);
            java.util.Arrays.fill(current,.1f);upload(color,current);
            float[] clamped=pixels(taa.resolve(color,guide,albedo,W,H,camera,1));
            for(int i=0;i<clamped.length;i++) if(i%4!=3) check(Math.abs(clamped[i]-.1f)<1e-6,"neighborhood clipping");
            check(glGetError()==GL_NO_ERROR,"TAA GL error");
            System.out.println("TAA GPU checks passed: camera reprojection, depth/normal/material rejection, reset and highlight clipping.");
        } finally { glDeleteTextures(color);glDeleteTextures(guide);glDeleteTextures(albedo);glActiveTexture(GL_TEXTURE0); }
    }
}
