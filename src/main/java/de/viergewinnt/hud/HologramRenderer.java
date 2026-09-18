package de.viergewinnt.hud;

import de.viergewinnt.Game.Game;
import de.viergewinnt.renderer.ShaderProgram;
import de.viergewinnt.scene.Camera;
import de.viergewinnt.scene.Vec3;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL43.*;

/** World-anchored, depth-tested hologram. Text changes only when the game state changes. */
public final class HologramRenderer implements AutoCloseable {
    public static final Vec3 ANCHOR=new Vec3(0,7.65f,0);
    private static final int WIDTH=1024,HEIGHT=256;
    private final ShaderProgram shader=new ShaderProgram(new String[]{"/shaders/hologram.vert","/shaders/hologram.frag"},new int[]{GL_VERTEX_SHADER,GL_FRAGMENT_SHADER},"");
    private final int texture=glGenTextures(),vao=glGenVertexArrays();
    private HUD.Status uploaded;
    public void render(Game game,Camera camera,int depthTexture,int width,int height,float time,boolean paused) {
        if(width<=0||height<=0||depthTexture==0) return;
        HUD.Status status=HUD.status(game);
        if(status!=uploaded) { upload(status);uploaded=status; }
        Vec3 right=camera.right();right=new Vec3(right.x,0,right.z).normalized();
        glViewport(0,0,width,height);glDisable(GL_DEPTH_TEST);glDisable(GL_FRAMEBUFFER_SRGB);
        shader.use();shader.integer("label",0);shader.integer("normalDepth",1);
        shader.vector("anchor",ANCHOR);shader.vector("panelRight",right);
        shader.vector("cameraPosition",camera.position());shader.vector("cameraForward",camera.forward());
        shader.vector("cameraRight",camera.right());shader.vector("cameraUp",camera.up());
        shader.vector("tint",new Vec3(status.red,status.green,status.blue));
        shader.scalar("aspect",(float)width/height);shader.scalar("time",time);shader.scalar("opacity",paused?.30f:1);
        shader.vector("viewport",new Vec3(width,height,0));
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,depthTexture);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);
        glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,6);glBindVertexArray(0);glDisable(GL_BLEND);
    }
    private static void centered(Graphics2D g,String text,int baseline,int size,Color color,boolean bold) {
        g.setFont(new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,size));g.setColor(color);
        g.drawString(text,(WIDTH-g.getFontMetrics().stringWidth(text))/2,baseline);
    }
    private void upload(HUD.Status status) {
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(7,20,31,150));g.fillRoundRect(18,18,WIDTH-36,HEIGHT-36,32,32);
        Color accent=new Color(status.red,status.green,status.blue);
        g.setColor(accent);g.setStroke(new BasicStroke(3));g.drawRoundRect(18,18,WIDTH-36,HEIGHT-36,32,32);
        centered(g,"4 GEWINNT  /  LIVE",58,23,new Color(181,222,239),true);
        centered(g,status.title,145,66,accent,true);
        centered(g,status.detail,205,25,new Color(224,244,252),false);
        g.dispose();ByteBuffer data=MemoryUtil.memAlloc(WIDTH*HEIGHT*4);
        try {
            int[] pixels=image.getRGB(0,0,WIDTH,HEIGHT,null,0,WIDTH);
            for(int argb:pixels) data.put((byte)(argb>>16)).put((byte)(argb>>8)).put((byte)argb).put((byte)(argb>>24));
            data.flip();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,WIDTH,HEIGHT,0,GL_RGBA,GL_UNSIGNED_BYTE,data);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        } finally { MemoryUtil.memFree(data); }
    }
    @Override public void close() { glDeleteTextures(texture);glDeleteVertexArrays(vao);shader.close(); }
}
