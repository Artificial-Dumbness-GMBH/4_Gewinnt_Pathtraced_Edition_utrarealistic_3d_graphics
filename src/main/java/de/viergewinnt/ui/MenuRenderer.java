package de.viergewinnt.ui;

import de.viergewinnt.renderer.ShaderProgram;
import de.viergewinnt.scene.Vec3;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL43.*;

/** Antialiased system-font UI, uploaded only after state/hover/scale changes. */
public final class MenuRenderer implements AutoCloseable {
    private final ShaderProgram shader=new ShaderProgram(new String[]{"/shaders/fullscreen.vert","/shaders/menu.frag"},new int[]{GL_VERTEX_SHADER,GL_FRAGMENT_SHADER},"");
    private final int vao=glGenVertexArrays(),texture=glGenTextures();
    private int density;
    public void render(PauseMenu menu,int w,int h) {
        if(w<=24||h<=24) return;
        int nextDensity=PauseMenu.scale(w,h)>1.2f?2:1;
        boolean dirty=menu.takeDirty();
        if(dirty||density!=nextDensity) {
            density=nextDensity;BufferedImage image=menu.image(density);
            ByteBuffer data=MemoryUtil.memAlloc(image.getWidth()*image.getHeight()*4);
            try {
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int argb=image.getRGB(x,y);data.put((byte)(argb>>16)).put((byte)(argb>>8)).put((byte)argb).put((byte)(argb>>24));
                }
                data.flip();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,image.getWidth(),image.getHeight(),0,GL_RGBA,GL_UNSIGNED_BYTE,data);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            } finally { MemoryUtil.memFree(data); }
        }
        glViewport(0,0,w,h);glDisable(GL_DEPTH_TEST);glDisable(GL_FRAMEBUFFER_SRGB);
        glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        shader.use();shader.integer("menuTexture",0);shader.vector("viewport",new Vec3(w,h,0));shader.scalar("panelScale",PauseMenu.scale(w,h));
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);
        glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);glBindVertexArray(0);glDisable(GL_BLEND);
    }
    @Override public void close() { glDeleteTextures(texture);glDeleteVertexArrays(vao);shader.close(); }
}
