package de.viergewinnt.renderer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public final class ScreenRenderer implements AutoCloseable {
    private final ShaderProgram shader=new ShaderProgram(new String[]{"/shaders/fullscreen.vert","/shaders/tonemap.frag"},new int[]{GL_VERTEX_SHADER,GL_FRAGMENT_SHADER},"");
    private final int vao=glGenVertexArrays();
    public void render(int texture,int normalDepth,int albedoGuide,int width,int height,boolean paused,int samples,RenderSettings settings) {
        glViewport(0,0,width,height);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_FRAMEBUFFER_SRGB);
        shader.use();shader.integer("sampleCount",samples);shader.integer("denoise",settings.denoiser()==RenderSettings.Denoiser.OWN?1:0);
        shader.scalar("denoiseStrength",settings.denoiseStrength());shader.scalar("exposure",settings.exposure());
        shader.integer("normalDepth",1);shader.integer("albedoGuide",2);
        glActiveTexture(GL_TEXTURE0+1);glBindTexture(GL_TEXTURE_2D,normalDepth);
        glActiveTexture(GL_TEXTURE0+2);glBindTexture(GL_TEXTURE_2D,albedoGuide);
        shader.integer("image",0);shader.integer("paused",paused?1:0);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);
        glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);glBindVertexArray(0);
    }
    @Override public void close() { glDeleteVertexArrays(vao);shader.close(); }
}
