package de.viergewinnt.renderer;

import de.viergewinnt.scene.Camera;
import static org.lwjgl.opengl.GL43.*;

/** Four-pass GPU adaptation of the BSD-licensed LWJGL À-Trous shader. */
public final class AtrousDenoiser implements AutoCloseable {
    private final ShaderProgram shader=new ShaderProgram(new String[]{"/shaders/atrous.comp"},new int[]{GL_COMPUTE_SHADER},"");
    private final int[] pingPong=new int[2];
    private int width,height;
    public int filter(int color,int normalDepth,int albedo,int w,int h,Camera camera,int samples,float strength) {
        if(w!=width||h!=height) {
            releaseTextures();width=w;height=h;
            glActiveTexture(GL_TEXTURE0);
            for(int i=0;i<2;i++) {
                pingPong[i]=glGenTextures();glBindTexture(GL_TEXTURE_2D,pingPong[i]);
                glTexStorage2D(GL_TEXTURE_2D,1,GL_RGBA32F,w,h);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            }
        }
        shader.use();shader.integer("colorMap",0);shader.integer("normalDepth",1);shader.integer("albedoGuide",2);
        shader.integer("sampleCount",samples);shader.scalar("strength",strength);
        shader.vector("cameraForward",camera.forward());shader.vector("cameraRight",camera.right());shader.vector("cameraUp",camera.up());
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,normalDepth);
        glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,albedo);
        int input=color;
        for(int pass=0;pass<4;pass++) {
            int output=pingPong[pass%2];
            shader.integer("stepwidth",1<<pass);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,input);
            glBindImageTexture(0,output,0,false,0,GL_WRITE_ONLY,GL_RGBA32F);
            glDispatchCompute((w+7)/8,(h+7)/8,1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);
            input=output;
        }
        return input;
    }
    private void releaseTextures() { for(int i=0;i<2;i++) if(pingPong[i]!=0) { glDeleteTextures(pingPong[i]);pingPong[i]=0; } }
    @Override public void close() { releaseTextures();shader.close(); }
}
