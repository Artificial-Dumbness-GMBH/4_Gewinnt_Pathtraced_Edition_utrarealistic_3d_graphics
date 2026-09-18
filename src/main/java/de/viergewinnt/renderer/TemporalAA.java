package de.viergewinnt.renderer;

import de.viergewinnt.scene.Camera;
import de.viergewinnt.scene.Vec3;
import static org.lwjgl.opengl.GL43.*;

/** Native-resolution HDR temporal resolve, before tone mapping and UI. */
public final class TemporalAA implements AutoCloseable {
    private final ShaderProgram shader=new ShaderProgram(new String[]{"/shaders/taa.comp"},new int[]{GL_COMPUTE_SHADER},"");
    private final int[] colors=new int[2],guides=new int[2];
    private int width,height,index;
    private boolean valid;
    private Vec3 position,forward,right,up;
    public void reset() { valid=false; }
    public int resolve(int current,int normalDepth,int albedo,int w,int h,Camera camera,int progressiveFrames) {
        if(w!=width||h!=height) {
            release();width=w;height=h;
            for(int i=0;i<2;i++) { colors[i]=texture(w,h);guides[i]=texture(w,h); }
        }
        int output=1-index;
        shader.use();shader.integer("currentColor",0);shader.integer("currentGuide",1);shader.integer("currentAlbedo",2);
        shader.integer("historyColor",3);shader.integer("historyGuide",4);shader.integer("historyValid",valid?1:0);
        shader.scalar("historyWeight",.85f/(1+.15f*Math.max(0,progressiveFrames-1)));
        shader.vector("cameraPosition",camera.position());shader.vector("cameraForward",camera.forward());
        shader.vector("cameraRight",camera.right());shader.vector("cameraUp",camera.up());
        shader.vector("previousPosition",valid?position:camera.position());shader.vector("previousForward",valid?forward:camera.forward());
        shader.vector("previousRight",valid?right:camera.right());shader.vector("previousUp",valid?up:camera.up());
        int[] textures={current,normalDepth,albedo,colors[index],guides[index]};
        for(int i=0;i<textures.length;i++) { glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,textures[i]); }
        glBindImageTexture(0,colors[output],0,false,0,GL_WRITE_ONLY,GL_RGBA32F);
        glBindImageTexture(1,guides[output],0,false,0,GL_WRITE_ONLY,GL_RGBA32F);
        glDispatchCompute((w+7)/8,(h+7)/8,1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);
        position=camera.position();forward=camera.forward();right=camera.right();up=camera.up();
        index=output;valid=true;glActiveTexture(GL_TEXTURE0);return colors[output];
    }
    private static int texture(int w,int h) {
        int texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);glTexStorage2D(GL_TEXTURE_2D,1,GL_RGBA32F,w,h);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        return texture;
    }
    private void release() {
        for(int i=0;i<2;i++) { if(colors[i]!=0) glDeleteTextures(colors[i]);if(guides[i]!=0) glDeleteTextures(guides[i]);colors[i]=guides[i]=0; }
        valid=false;index=0;
    }
    @Override public void close() { release();shader.close(); }
}
