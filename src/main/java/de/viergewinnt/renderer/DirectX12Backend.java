package de.viergewinnt.renderer;

import de.viergewinnt.scene.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/** Optional Windows backend. SDK absence never prevents software DX12 tracing. */
public final class DirectX12Backend implements AutoCloseable {
    private long handle;
    private float lift;
    private RenderSettings settings;
    private final ByteBuffer overlay=ByteBuffer.allocateDirect(960*660*4);
    private final float[] cameraData=new float[16];
    public DirectX12Backend() {
        Native.load();handle=Native.create(Boolean.getBoolean("pt.dx12.debug"));
        if(handle==0) throw new IllegalStateException("DirectX 12 konnte nicht initialisiert werden.");
    }
    public static boolean available() {
        try(DirectX12Backend backend=new DirectX12Backend()) { return backend.handle!=0; }
        catch(UnsatisfiedLinkError|RuntimeException e) { return false; }
    }
    public String adapterName() { checkOpen();return Native.adapterName(handle); }
    public String status() { checkOpen();return Native.status(handle); }
    public boolean raytracingSupported() { return capabilities().hardwareRaytracing(); }
    public RenderCapabilities capabilities() {
        checkOpen();int bits=Native.capabilities(handle);
        return new RenderCapabilities(true,(bits&1)!=0,(bits&2)!=0,(bits&4)!=0,Math.max(1,Math.min(4,bits>>>8)));
    }
    public void attachWindow(long hwnd,int width,int height) { checkOpen();Native.attach(handle,hwnd,width,height); }
    public void resize(int width,int height) { checkOpen();Native.resize(handle,width,height);if(settings!=null) applySettings(settings); }
    public void applySettings(RenderSettings value) {
        checkOpen();GraphicsOptions g=value.graphics();
        Native.configure(handle,new int[]{g.raytracing().ordinal(),g.upscaler().ordinal(),g.quality().ordinal(),value.denoiser().ordinal(),
            value.bounces(),value.samplesPerFrame(),value.maxWidth(),value.maxHeight(),g.denoisePasses(),value.taa()?1:0,
            !Boolean.getBoolean("pt.benchmark")&&g.vsync()?1:0,g.frameGeneration()},new float[]{value.exposure(),value.denoiseStrength(),g.sharpness()});
        settings=value;
    }
    public void setScene(Scene scene) {
        checkOpen();NativeScene data=NativeScene.from(scene);Native.setScene(handle,data.buffers(),data.movingVertexStart());lift=0;
    }
    public void setDropLift(float value) { lift=value; }
    public void overlay(BufferedImage image) {
        if(image.getWidth()!=960||image.getHeight()!=660) throw new IllegalArgumentException("Overlay must be 960x660");
        overlay.clear();
        int[] pixels=image.getRGB(0,0,960,660,null,0,960);
        for(int argb:pixels) overlay.put((byte)(argb>>16)).put((byte)(argb>>8)).put((byte)argb).put((byte)(argb>>24));
        overlay.flip();
    }
    public void render(Camera camera,float dt,int overlayMode) {
        checkOpen();put(0,camera.position());put(4,camera.forward());put(8,camera.right());put(12,camera.up());
        Native.render(handle,cameraData,lift,dt*1000,overlay,overlayMode);
    }
    private void put(int offset,Vec3 v) { cameraData[offset]=v.x;cameraData[offset+1]=v.y;cameraData[offset+2]=v.z; }
    private void checkOpen() { if(handle==0) throw new IllegalStateException("DirectX 12 Backend ist geschlossen."); }
    @Override public void close() { if(handle!=0) { Native.destroy(handle);handle=0; } }
    private static final class Native {
        private static boolean loaded;
        private static synchronized void load() {
            if(!loaded) {
                String explicit=System.getProperty("pt.dx12.library","");
                if(explicit.isBlank()) System.loadLibrary("viergewinnt_dx12");else System.load(java.nio.file.Path.of(explicit).toAbsolutePath().toString());
                loaded=true;
            }
        }
        private static native long create(boolean debug);
        private static native String adapterName(long handle);
        private static native int capabilities(long handle);
        private static native String status(long handle);
        private static native void attach(long handle,long hwnd,int width,int height);
        private static native void resize(long handle,int width,int height);
        private static native void configure(long handle,int[] settings,float[] display);
        private static native void setScene(long handle,ByteBuffer[] buffers,int movingStart);
        private static native void render(long handle,float[] camera,float lift,float ms,ByteBuffer ui,int mode);
        private static native void destroy(long handle);
    }
}
