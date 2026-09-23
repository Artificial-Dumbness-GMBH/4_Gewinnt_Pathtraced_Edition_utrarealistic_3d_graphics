package de.viergewinnt.hud;

import de.viergewinnt.Game.Game;
import de.viergewinnt.scene.Camera;
import static org.lwjgl.opengl.GL43.*;

/** Tests the actual world-space shader and scene-depth occlusion in an existing EGL context. */
public final class HologramSmokeTest {
    private static int texture(int w,int h,int format,float[] pixels) {
        int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);
        glTexImage2D(GL_TEXTURE_2D,0,format,w,h,0,GL_RGBA,GL_FLOAT,pixels);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);return id;
    }
    private static float[] read(int w,int h) {
        float[] pixels=new float[w*h*4];glReadPixels(0,0,w,h,GL_RGBA,GL_FLOAT,pixels);return pixels;
    }
    public static void run() {
        int w=512,h=384;glActiveTexture(GL_TEXTURE0);
        int target=texture(w,h,GL_RGBA8,new float[w*h*4]),guide=texture(64,48,GL_RGBA32F,new float[64*48*4]),fbo=glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("HUD framebuffer");
        try(HologramRenderer hologram=new HologramRenderer()) {
            Game game=new Game();Camera camera=new Camera();glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
            hologram.render(game,camera,guide,w,h,0,false);float[] red=read(w,h);double energy=0;
            for(int i=0;i<red.length;i+=4) energy+=red[i]+red[i+1]+red[i+2];
            if(energy<100) throw new AssertionError("hologram not visible above board");
            game.play(0);glClear(GL_COLOR_BUFFER_BIT);hologram.render(game,camera,guide,w,h,0,false);
            float[] blue=read(w,h);double change=0;
            for(int i=0;i<red.length;i+=4) change+=Math.abs(red[i]-blue[i])+Math.abs(red[i+2]-blue[i+2]);
            if(change<100) throw new AssertionError("turn change did not refresh hologram");
            float[] wall=new float[64*48*4];for(int i=3;i<wall.length;i+=4) wall[i]=.1f;
            glBindTexture(GL_TEXTURE_2D,guide);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,64,48,GL_RGBA,GL_FLOAT,wall);
            glClear(GL_COLOR_BUFFER_BIT);hologram.render(game,camera,guide,w,h,0,false);
            float[] hidden=read(w,h);for(float value:hidden) if(value!=0) throw new AssertionError("hologram visible through foreground wall");
            if(glGetError()!=GL_NO_ERROR) throw new AssertionError("HUD GL error");
            System.out.println("Hologram GPU checks passed: world projection, live color/text change and foreground occlusion.");
        } finally { glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(fbo);glDeleteTextures(target);glDeleteTextures(guide); }
    }
}
