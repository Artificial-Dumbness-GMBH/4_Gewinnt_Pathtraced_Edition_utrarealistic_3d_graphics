package de.viergewinnt.renderer;

/** Minimal Java entry point for the optional native DirectX 12 backend. */
public final class DirectX12Backend implements AutoCloseable {
    private long handle;

    public DirectX12Backend() {
        Native.load();
        handle=Native.create();
        if(handle==0) throw new IllegalStateException("DirectX 12 konnte nicht initialisiert werden.");
    }

    public static boolean available() {
        try {
            Native.load();
            return Native.available();
        } catch(UnsatisfiedLinkError|RuntimeException e) {
            return false;
        }
    }

    public String adapterName() {
        checkOpen();
        return Native.adapterName(handle);
    }

    public boolean raytracingSupported() {
        checkOpen();
        return Native.raytracingSupported(handle);
    }

    public void attachWindow(long windowHandle,int width,int height) {
        checkOpen();
        if(windowHandle==0||width<=0||height<=0) throw new IllegalArgumentException("Ungültiges DX12-Fenster.");
        if(!Native.createSwapChain(handle,windowHandle,width,height))
            throw new IllegalStateException("DX12-Swapchain konnte nicht erstellt werden.");
    }

    public void resize(int width,int height) {
        checkOpen();
        if(width<=0||height<=0) return;
        if(!Native.resizeSwapChain(handle,width,height))
            throw new IllegalStateException("DX12-Swapchain konnte nicht angepasst werden.");
    }

    public void present() {
        checkOpen();
        if(!Native.present(handle)) throw new IllegalStateException("DX12-Present fehlgeschlagen.");
    }

    public void clear(float red,float green,float blue,float alpha) {
        checkOpen();
        if(!Native.clear(handle,red,green,blue,alpha))
            throw new IllegalStateException("DX12-Clear-Pass fehlgeschlagen.");
    }

    private void checkOpen() {
        if(handle==0) throw new IllegalStateException("DirectX 12 Backend ist geschlossen.");
    }

    @Override public void close() {
        if(handle!=0) { Native.destroy(handle);handle=0; }
    }

    private static final class Native {
        private static boolean loaded;

        private static synchronized void load() {
            if(!loaded) {
                String explicit=System.getProperty("pt.dx12.library","");
                if(explicit.isBlank()) System.loadLibrary("viergewinnt_dx12");
                else System.load(java.nio.file.Path.of(explicit).toAbsolutePath().toString());
                loaded=true;
            }
        }

        private static native boolean available();
        private static native long create();
        private static native String adapterName(long handle);
        private static native boolean raytracingSupported(long handle);
        private static native boolean createSwapChain(long handle,long windowHandle,int width,int height);
        private static native boolean resizeSwapChain(long handle,int width,int height);
        private static native boolean clear(long handle,float red,float green,float blue,float alpha);
        private static native boolean present(long handle);
        private static native void destroy(long handle);
    }
}