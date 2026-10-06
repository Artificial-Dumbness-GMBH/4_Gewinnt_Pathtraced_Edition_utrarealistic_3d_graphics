package de.viergewinnt.renderer;

/** Validated pipeline/display choices; capabilities are checked by each backend. */
public record GraphicsOptions(Raytracing raytracing,Upscaler upscaler,Quality quality,
        int denoisePasses,float sharpness,boolean vsync,int frameGeneration,int frameLimit,
        float renderScale,Reconstruction reconstruction,boolean radianceCache) {
    public enum Raytracing { AUTO, SOFTWARE, HARDWARE }
    public enum Upscaler { OFF, FSR, XESS, DLSS }
    public enum Reconstruction { OFF, DLSS_RR, FSR_RR }
    public enum Quality { NATIVE, QUALITY, BALANCED, PERFORMANCE }
    public GraphicsOptions {
        if(reconstruction==null||!Float.isFinite(renderScale)||(renderScale!=0&&(renderScale<.333f||renderScale>1))
                ||raytracing==null||upscaler==null||quality==null||denoisePasses<1||denoisePasses>5
                ||!Float.isFinite(sharpness)||sharpness<0||sharpness>1
                ||frameGeneration<1||frameGeneration>4||frameLimit<0||frameLimit>360)
            throw new IllegalArgumentException("Invalid graphics options");
    }
    public GraphicsOptions(Raytracing r,Upscaler u,Quality q,int passes,float sharpness,boolean vsync,int fg,int limit) {
        this(r,u,q,passes,sharpness,vsync,fg,limit,0,Reconstruction.OFF,false);
    }
    public static GraphicsOptions defaults() { return new GraphicsOptions(Raytracing.AUTO,Upscaler.OFF,Quality.QUALITY,4,.2f,true,1,0); }
    public GraphicsOptions withRaytracing(Raytracing v) { return new GraphicsOptions(v,upscaler,quality,denoisePasses,sharpness,vsync,frameGeneration,frameLimit,renderScale,reconstruction,radianceCache); }
    public GraphicsOptions withUpscaler(Upscaler v) { return new GraphicsOptions(raytracing,v,quality,denoisePasses,sharpness,vsync,frameGeneration,frameLimit,renderScale,reconstruction,radianceCache); }
    public GraphicsOptions withQuality(Quality v) { return new GraphicsOptions(raytracing,upscaler,v,denoisePasses,sharpness,vsync,frameGeneration,frameLimit,0,reconstruction,radianceCache); }
    public GraphicsOptions withPasses(int v) { return new GraphicsOptions(raytracing,upscaler,quality,v,sharpness,vsync,frameGeneration,frameLimit,renderScale,reconstruction,radianceCache); }
    public GraphicsOptions withSharpness(float v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,v,vsync,frameGeneration,frameLimit,renderScale,reconstruction,radianceCache); }
    public GraphicsOptions withVsync(boolean v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,sharpness,v,frameGeneration,frameLimit,renderScale,reconstruction,radianceCache); }
    public GraphicsOptions withFrameGeneration(int v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,sharpness,vsync,v,frameLimit,renderScale,reconstruction,radianceCache); }
    public GraphicsOptions withFrameLimit(int v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,sharpness,vsync,frameGeneration,v,renderScale,reconstruction,radianceCache); }
    public GraphicsOptions withRenderScale(float v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,sharpness,vsync,frameGeneration,frameLimit,v,reconstruction,radianceCache); }
    public GraphicsOptions withReconstruction(Reconstruction v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,sharpness,vsync,frameGeneration,frameLimit,renderScale,v,radianceCache); }
    public GraphicsOptions withRadianceCache(boolean v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,sharpness,vsync,frameGeneration,frameLimit,renderScale,reconstruction,v); }
}

