package de.viergewinnt.renderer;

import de.viergewinnt.bvh.*;
import de.viergewinnt.scene.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Locale;

/** JNI owner for the Windows compute-pathtracing renderer. All calls use the window thread. */
public final class DirectX12Backend implements AutoCloseable {
    private long handle;
    private boolean attached;
    private final RenderSettings settings;
    private final float[] cameraData=new float[12];
    public DirectX12Backend() { this(RenderSettings.defaults().systemOverrides()); }
    public DirectX12Backend(RenderSettings settings) {
        this.settings=settings;
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))
            throw new UnsupportedOperationException("DirectX 12 benötigt Windows.");
        Native.load();handle=Native.create(Boolean.getBoolean("pt.dx12.warp"));
        if(handle==0) throw new IllegalStateException("DirectX 12 konnte nicht initialisiert werden.");
    }
    public static boolean available() {
        try(DirectX12Backend backend=new DirectX12Backend()) { return backend.handle!=0; }
        catch(UnsatisfiedLinkError|RuntimeException e) { return false; }
    }
    public String adapterName() { checkOpen();return Native.adapterName(handle); }
    public boolean raytracingSupported() { checkOpen();return Native.raytracingSupported(handle); }
    public String upscalerName() { checkOpen();return Native.upscalerName(handle); }
    public void attachWindow(long window,int width,int height) {
        checkOpen();
        if(attached||window==0||width<1||height<1) throw new IllegalArgumentException("Ungültiges oder bereits verbundenes DX12-Fenster.");
        String mode=System.getProperty("pt.upscaler","off").toLowerCase(Locale.ROOT);
        if(!mode.equals("off")&&!mode.equals("fsr41")&&!mode.equals("xess"))
            throw new IllegalArgumentException("pt.upscaler: off, fsr41 oder xess. INT8 lässt sich über die offizielle API nicht erzwingen.");
        String dll=System.getProperty("pt.upscaler.library","");
        if(!mode.equals("off")) {
            if(dll.isBlank()) throw new IllegalArgumentException("pt.upscaler.library muss auf die SDK-DLL zeigen.");
            dll=Path.of(dll).toAbsolutePath().normalize().toString();
        }
        Native.attach(handle,window,width,height,settings.maxWidth(),settings.maxHeight(),mode,dll);attached=true;
    }
    public void resize(int width,int height) {
        checkAttached();if(width>0&&height>0) Native.resize(handle,width,height,settings.maxWidth(),settings.maxHeight());
    }
    public void setScene(Scene scene) {
        checkAttached();BVHData bvh=BVHBuilder.build(scene.mesh);bvh.includeVerticalMotion(scene.movingVertexStart,scene.movingMaxLift);
        ByteBuffer[] data={buffer(scene.mesh.vertices.size(),16),buffer(bvh.triangles.length,16),buffer(scene.materials.size(),48),buffer(bvh.nodes.size(),48)};
        for(Vec3 v:scene.mesh.vertices) vector(data[0],v,0);
        for(Triangle t:bvh.triangles) data[1].putInt(t.a).putInt(t.b).putInt(t.c).putInt(t.material);
        for(Material m:scene.materials) {vector(data[2],m.baseColor,0);vector(data[2],m.emission,0);data[2].putFloat(m.roughness).putFloat(m.metallic).putFloat(m.texture).putFloat(m.textureScale);}
        for(BVHNode n:bvh.nodes) {vector(data[3],n.min,n.moving?1:0);vector(data[3],n.max,0);data[3].putInt(n.left).putInt(n.right).putInt(n.first).putInt(n.count);}
        for(ByteBuffer b:data) b.flip();
        Native.upload(handle,data,scene.movingVertexStart);
    }
    private static ByteBuffer buffer(int count,int stride) { return ByteBuffer.allocateDirect(Math.multiplyExact(count,stride)).order(ByteOrder.nativeOrder()); }
    private static void vector(ByteBuffer b,Vec3 v,float w) { b.putFloat(v.x).putFloat(v.y).putFloat(v.z).putFloat(w); }
    public void render(Camera camera,float lift,float dt,boolean reset) {
        checkAttached();Vec3[] vectors={camera.position(),camera.forward(),camera.right(),camera.up()};
        for(int i=0;i<4;i++) {cameraData[i*3]=vectors[i].x;cameraData[i*3+1]=vectors[i].y;cameraData[i*3+2]=vectors[i].z;}
        Native.render(handle,cameraData,lift,settings.samplesPerFrame(),settings.bounces(),settings.exposure(),settings.denoiseStrength(),
            settings.denoiser()!=RenderSettings.Denoiser.OFF,dt*1000,reset,!Boolean.getBoolean("pt.benchmark"));
    }
    public void validateFrame() {checkAttached();Native.validateFrame(handle);}
    private void checkOpen() { if(handle==0) throw new IllegalStateException("DirectX 12 Backend ist geschlossen."); }
    private void checkAttached() {checkOpen();if(!attached) throw new IllegalStateException("Kein DX12-Fenster verbunden.");}
    @Override public void close() {if(handle!=0) {Native.destroy(handle);handle=0;attached=false;}}
    private static final class Native {
        private static boolean loaded;
        private static synchronized void load() {
            if(!loaded) {String explicit=System.getProperty("pt.dx12.library","");
                if(explicit.isBlank()) System.loadLibrary("viergewinnt_dx12");else System.load(Path.of(explicit).toAbsolutePath().toString());loaded=true;}
        }
        private static native long create(boolean warp);
        private static native void validateFrame(long h);
        private static native void destroy(long h);
        private static native String adapterName(long h);
        private static native String upscalerName(long h);
        private static native boolean raytracingSupported(long h);
        private static native void attach(long h,long window,int w,int ht,int rw,int rh,String mode,String dll);
        private static native void resize(long h,int w,int ht,int rw,int rh);
        private static native void upload(long h,ByteBuffer[] data,int movingStart);
        private static native void render(long h,float[] camera,float lift,int samples,int bounces,float exposure,float strength,boolean denoise,float ms,boolean reset,boolean vsync);
    }
}
