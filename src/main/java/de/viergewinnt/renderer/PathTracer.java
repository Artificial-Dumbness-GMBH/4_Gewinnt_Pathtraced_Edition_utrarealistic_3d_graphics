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
import de.viergewinnt.scene.Vec3;
import static org.lwjgl.opengl.GL15.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL11.GL_NEAREST;

public final class PathTracer implements AutoCloseable {
    private final ComputeShader shader;
    private final ScreenRenderer screen;
    private final AtrousDenoiser atrous;
    private final TemporalAA temporal;
    private int sampleSequence;
    private RenderSettings settings;
    private Camera lastCamera;
    private boolean displayDirty=true;
    private float dropLift;
    private int displayTexture;
    private GPUScene scene;
    private int texture,normalDepth,albedoGuide,width,height,frameIndex;
    private Vec3 lastPosition,lastForward;
    private final int groupX,groupY;
    private final boolean half,bruteForce;
    public PathTracer(Scene initialScene) { this(initialScene,RenderSettings.defaults().systemOverrides()); }
    public PathTracer(Scene initialScene,RenderSettings initialSettings) {
        settings=java.util.Objects.requireNonNull(initialSettings);
        groupX=option("pt.groupX",8,8,16);groupY=option("pt.groupY",8,8,16);
        if(groupX==8&&groupY==16) throw new IllegalArgumentException("Use 8x8, 16x8 or 16x16");

        half=Boolean.getBoolean("pt.half");bruteForce=Boolean.getBoolean("pt.bruteForce");
        shader=ComputeShader.create(groupX,groupY,half);
        ScreenRenderer createdScreen=null;AtrousDenoiser createdAtrous=null;TemporalAA createdTemporal=null;
        try { createdScreen=new ScreenRenderer();createdAtrous=new AtrousDenoiser();createdTemporal=new TemporalAA();scene=new GPUScene(initialScene); }
        catch(RuntimeException e) { if(createdTemporal!=null) createdTemporal.close();if(createdAtrous!=null) createdAtrous.close();if(createdScreen!=null) createdScreen.close();shader.close();throw e; }
        screen=createdScreen;atrous=createdAtrous;temporal=createdTemporal;
    }
    private static int option(String name,int fallback,int min,int max) {
        int n=Integer.parseInt(System.getProperty(name,Integer.toString(fallback)));
        if(n<min||n>max||(name.startsWith("pt.group")&&n!=8&&n!=16)) throw new IllegalArgumentException(name+" out of range");return n;
    }
    public void setScene(Scene next) { GPUScene replacement=new GPUScene(next);scene.close();scene=replacement;dropLift=0;reset(); }
    public void setDropLift(float lift) {
        if(!Float.isFinite(lift)||lift<0||lift>scene.movingMaxLift) throw new IllegalArgumentException("Drop outside BVH bounds");
        if(dropLift!=lift) { dropLift=lift;reset(); }
    }
    private void resetAccumulation() { frameIndex=0;displayDirty=true; }
    public void reset() { resetAccumulation();temporal.reset(); }
    public int depthGuideTexture() { return normalDepth; }
    public RenderSettings settings() { return settings; }
    public void applySettings(RenderSettings next) {
        java.util.Objects.requireNonNull(next);
        if(!settings.sameSampling(next)) reset();
        if(settings.denoiser()!=next.denoiser()||settings.denoiseStrength()!=next.denoiseStrength()||settings.taa()!=next.taa()) {
            displayDirty=true;temporal.reset();
        }
        settings=next;
    }
    public boolean usesFp16Arithmetic() { return shader.precision.fp16(); }
    public String precisionDescription() { return shader.precision.description()+"; accumulation "+(half?"FP16 (experimental)":"FP32"); }
    public int samples() { return frameIndex*settings.samplesPerFrame(); }
    public void render(Camera camera,int framebufferWidth,int framebufferHeight) {
        if(framebufferWidth<=0||framebufferHeight<=0) return;
        traceFrame(camera,framebufferWidth,framebufferHeight);
        present(framebufferWidth,framebufferHeight,false);
    }
    private void traceFrame(Camera camera,int framebufferWidth,int framebufferHeight) {
        lastCamera=camera;
        Vec3 position=camera.position(),forward=camera.forward();
        if(different(position,lastPosition)||different(forward,lastForward)) resetAccumulation();
        lastPosition=position;lastForward=forward;
        float scale=Math.min(1f,Math.min((float)settings.maxWidth()/framebufferWidth,(float)settings.maxHeight()/framebufferHeight));
        int w=Math.max(1,Math.round(framebufferWidth*scale)),h=Math.max(1,Math.round(framebufferHeight*scale));
        if(w!=width||h!=height) {
            deleteTextures();
            width=w;height=h;
            texture=createTexture(half?GL_RGBA16F:GL_RGBA32F,GL_LINEAR);
            normalDepth=createTexture(GL_RGBA32F,GL_NEAREST);
            albedoGuide=createTexture(GL_RGBA16F,GL_NEAREST);reset();
        }
        // Keep integer conversion exact in the float running mean; half is experimental.
        if(frameIndex>=16000000) reset();
        scene.bind();shader.use();
        shader.vector("lightPosition",Scene.LIGHT_POSITION);shader.vector("lightSize",Scene.LIGHT_SIZE);
        shader.vector("lightRadiance",Scene.LIGHT_RADIANCE);shader.integer("lightMaterial",Scene.LIGHT_MATERIAL);
        shader.integer("sampleSequence",sampleSequence);sampleSequence=(sampleSequence+1)&0x007fffff;
        shader.integer("frameIndex",frameIndex);shader.integer("samplesPerFrame",settings.samplesPerFrame());shader.integer("maxBounces",settings.bounces());
        shader.integer("movingVertexStart",scene.movingVertexStart);shader.vector("movingOffset",new Vec3(0,dropLift,0));
        shader.integer("triangleCount",scene.triangleCount);shader.integer("bruteForce",bruteForce?1:0);
        shader.integer("gradient",Boolean.getBoolean("pt.gradient")?1:0);
        shader.vector("cameraPosition",camera.position());shader.vector("cameraForward",camera.forward());
        shader.vector("cameraRight",camera.right());shader.vector("cameraUp",camera.up());
        glBindImageTexture(0,texture,0,false,0,GL_READ_WRITE,half?GL_RGBA16F:GL_RGBA32F);
        glBindImageTexture(1,normalDepth,0,false,0,GL_WRITE_ONLY,GL_RGBA32F);
        glBindImageTexture(2,albedoGuide,0,false,0,GL_WRITE_ONLY,GL_RGBA16F);
        glDispatchCompute((width+groupX-1)/groupX,(height+groupY-1)/groupY,1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);
        frameIndex++;displayDirty=true;
    }
    public void renderPauseOverlay(int framebufferWidth,int framebufferHeight) {
        if(texture!=0) present(framebufferWidth,framebufferHeight,true);
    }
    public void renderPaused(Camera camera,int w,int h) {
        if(w<=0||h<=0) return;
        float scale=Math.min(1f,Math.min((float)settings.maxWidth()/w,(float)settings.maxHeight()/h));
        // Settle the frozen view, then reuse it without spending GPU time on tracing/filtering.
        if(samples()<64||different(camera.position(),lastPosition)||different(camera.forward(),lastForward)
                ||Math.max(1,Math.round(w*scale))!=width||Math.max(1,Math.round(h*scale))!=height)
            traceFrame(camera,w,h);
        renderPauseOverlay(w,h);
    }
    private void present(int w,int h,boolean paused) {
        if(displayDirty) {
            displayTexture=settings.denoiser()==RenderSettings.Denoiser.ATROUS
                ?atrous.filter(texture,normalDepth,albedoGuide,width,height,lastCamera,samples(),settings.denoiseStrength()):texture;
            if(settings.taa()) displayTexture=temporal.resolve(displayTexture,normalDepth,albedoGuide,width,height,lastCamera,frameIndex);
            displayDirty=false;
        }
        screen.render(displayTexture,normalDepth,albedoGuide,w,h,paused,samples(),settings);
    }
    private static boolean different(Vec3 a,Vec3 b) { return b==null||a.x!=b.x||a.y!=b.y||a.z!=b.z; }
    private int createTexture(int format,int filter) {
        int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);
        glTexStorage2D(GL_TEXTURE_2D,1,format,width,height);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,filter);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,filter);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        return id;
    }
    private void deleteTextures() {
        if(texture!=0) glDeleteTextures(texture);if(normalDepth!=0) glDeleteTextures(normalDepth);if(albedoGuide!=0) glDeleteTextures(albedoGuide);
        texture=normalDepth=albedoGuide=0;
    }
    @Override public void close() { deleteTextures();scene.close();temporal.close();atrous.close();screen.close();shader.close(); }
}
