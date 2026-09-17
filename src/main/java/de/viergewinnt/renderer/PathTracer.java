package de.viergewinnt.renderer;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL15.GL_READ_WRITE;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.GL_TEXTURE_FETCH_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glBindImageTexture;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL42.glTexStorage2D;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import de.viergewinnt.gpu.GPUScene;
import de.viergewinnt.scene.Camera;
import de.viergewinnt.scene.Scene;

public final class PathTracer implements AutoCloseable {
    private final ComputeShader shader;
    private final ScreenRenderer screen;
    private GPUScene scene;
    private int texture,width,height,frameIndex;
    private final int groupX,groupY,bounces,maxWidth,maxHeight,samplesPerFrame;
    private final boolean half,bruteForce;
    public PathTracer(Scene initialScene) {
        groupX=option("pt.groupX",8,8,16);groupY=option("pt.groupY",8,8,16);
        if(groupX==8&&groupY==16) throw new IllegalArgumentException("Use 8x8, 16x8 or 16x16");
        bounces=option("pt.bounces",1,1,8);samplesPerFrame=option("pt.samplesPerFrame",8,1,16);maxWidth=option("pt.width",960,64,3840);maxHeight=option("pt.height",540,64,2160);
        half=Boolean.getBoolean("pt.half");bruteForce=Boolean.getBoolean("pt.bruteForce");
        shader=new ComputeShader(groupX,groupY,half);
        ScreenRenderer createdScreen=null;
        try { createdScreen=new ScreenRenderer();scene=new GPUScene(initialScene); }
        catch(RuntimeException e) { if(createdScreen!=null) createdScreen.close();shader.close();throw e; }
        screen=createdScreen;
    }
    private static int option(String name,int fallback,int min,int max) {
        int n=Integer.parseInt(System.getProperty(name,Integer.toString(fallback)));
        if(n<min||n>max||(name.startsWith("pt.group")&&n!=8&&n!=16)) throw new IllegalArgumentException(name+" out of range");return n;
    }
    public void setScene(Scene next) { GPUScene replacement=new GPUScene(next);scene.close();scene=replacement;reset(); }
    public void reset() { frameIndex=0; }
    public int samples() { return frameIndex; }
    public void render(Camera camera,int framebufferWidth,int framebufferHeight) {
        if(framebufferWidth<=0||framebufferHeight<=0) return;
        float scale=Math.min(1f,Math.min((float)maxWidth/framebufferWidth,(float)maxHeight/framebufferHeight));
        int w=Math.max(1,Math.round(framebufferWidth*scale)),h=Math.max(1,Math.round(framebufferHeight*scale));
        if(w!=width||h!=height) {
            if(texture!=0) glDeleteTextures(texture);
            width=w;height=h;texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);
            glTexStorage2D(GL_TEXTURE_2D,1,half?GL_RGBA16F:GL_RGBA32F,width,height);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);reset();
        }
        // Keep integer conversion exact in the float running mean; half is experimental.
        if(frameIndex>=16000000) reset();
        scene.bind();shader.use();shader.integer("frameIndex",frameIndex);shader.integer("samplesPerFrame",samplesPerFrame);shader.integer("maxBounces",bounces);
        shader.integer("triangleCount",scene.triangleCount);shader.integer("bruteForce",bruteForce?1:0);
        shader.integer("gradient",Boolean.getBoolean("pt.gradient")?1:0);
        shader.vector("cameraPosition",camera.position());shader.vector("cameraForward",camera.forward());
        shader.vector("cameraRight",camera.right());shader.vector("cameraUp",camera.up());
        glBindImageTexture(0,texture,0,false,0,GL_READ_WRITE,half?GL_RGBA16F:GL_RGBA32F);
        glDispatchCompute((width+groupX-1)/groupX,(height+groupY-1)/groupY,1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);
        screen.render(texture,framebufferWidth,framebufferHeight);frameIndex++;
    }
    @Override public void close() { if(texture!=0) glDeleteTextures(texture);scene.close();screen.close();shader.close(); }
}
