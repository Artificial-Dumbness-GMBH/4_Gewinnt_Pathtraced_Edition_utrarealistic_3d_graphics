package de.viergewinnt.renderer;

import static org.lwjgl.opengl.GL43.*;

public final class ScreenRenderer implements AutoCloseable {
    private final ShaderProgram shader=new ShaderProgram(new String[]{"/shaders/fullscreen.vert","/shaders/tonemap.frag"},new int[]{GL_VERTEX_SHADER,GL_FRAGMENT_SHADER},"");
    private final int vao=glGenVertexArrays();
    public void render(int texture,int width,int height) {
        glViewport(0,0,width,height);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_FRAMEBUFFER_SRGB);
        shader.use();shader.integer("image",0);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);
        glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);glBindVertexArray(0);
    }
    @Override public void close() { glDeleteVertexArrays(vao);shader.close(); }
}
