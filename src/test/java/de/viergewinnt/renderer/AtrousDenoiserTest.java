package de.viergewinnt.renderer;

import de.viergewinnt.scene.*;
import java.util.Random;
import static org.lwjgl.opengl.GL43.*;

/** Runs inside RendererSmokeTest's real EGL context. */
public final class AtrousDenoiserTest {
    private static int texture(int w,int h,float[] data) {
        int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,w,h,0,GL_RGBA,GL_FLOAT,data);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        return id;
    }
    public static void run() {
        int w=65,h=33;float[] input=new float[w*h*4],guides=new float[input.length],albedo=new float[input.length];
        Camera camera=new Camera();Random random=new Random(81);Vec3 forward=camera.forward(),normal=forward.mul(-1);
        double before=0;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int i=4*(y*w+x);boolean left=x<w/2;
            float[] base=left?new float[]{.8f,.1f,.1f}:new float[]{.1f,.1f,.8f};
            float noise=1+(random.nextFloat()-.5f)*.8f;
            for(int j=0;j<3;j++) { input[i+j]=base[j]*noise;albedo[i+j]=base[j];before+=Math.pow(input[i+j]-base[j],2); }
            input[i+3]=1;albedo[i+3]=left?1:2;
            float px=((x+.5f)/w*2-1)*(float)w/h,py=(y+.5f)/h*2-1;
            Vec3 ray=forward.add(camera.right().mul(px*.57735026919f)).add(camera.up().mul(py*.57735026919f)).normalized();
            guides[i]=normal.x;guides[i+1]=normal.y;guides[i+2]=normal.z;guides[i+3]=8/ray.dot(forward);
        }
        glActiveTexture(GL_TEXTURE0);int c=texture(w,h,input),g=texture(w,h,guides),a=texture(w,h,albedo);
        try(AtrousDenoiser denoiser=new AtrousDenoiser()) {
            int output=denoiser.filter(c,g,a,w,h,camera,4,1);
            glMemoryBarrier(GL_TEXTURE_UPDATE_BARRIER_BIT);glBindTexture(GL_TEXTURE_2D,output);
            float[] pixels=new float[input.length];glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,pixels);double after=0;
            for(int i=0;i<pixels.length;i+=4) for(int j=0;j<3;j++) {
                if(!Float.isFinite(pixels[i+j])) throw new AssertionError("nonfinite atrous output");
                after+=Math.pow(pixels[i+j]-albedo[i+j],2);
            }
            if(after>=before*.5) throw new AssertionError("insufficient noise reduction: "+after/before);
            for(int y=0;y<h;y++) {
                int left=4*(y*w+w/2-1),right=left+4;
                if(pixels[left]-pixels[left+2]<.5||pixels[right+2]-pixels[right]<.5) throw new AssertionError("material boundary blurred");
            }
            System.out.printf(java.util.Locale.ROOT,"A-Trous synthetic noise MSE reduced by %.1f%%; material boundary preserved.%n",100*(1-after/before));
        } finally { glDeleteTextures(c);glDeleteTextures(g);glDeleteTextures(a);glActiveTexture(GL_TEXTURE0); }
    }
}
