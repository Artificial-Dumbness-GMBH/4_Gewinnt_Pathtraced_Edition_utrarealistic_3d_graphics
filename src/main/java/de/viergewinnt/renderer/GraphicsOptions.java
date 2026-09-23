package de.viergewinnt.renderer;

/** Validated pipeline/display choices; capabilities are checked by each backend. */
public record GraphicsOptions(Raytracing raytracing,Upscaler upscaler,Quality quality,
        int denoisePasses,float sharpness,boolean vsync,int frameGeneration,int frameLimit) {
    public enum Raytracing { AUTO, SOFTWARE, HARDWARE }
    public enum Upscaler { OFF, FSR, XESS }
    public enum Quality { NATIVE, QUALITY, BALANCED, PERFORMANCE }
    public GraphicsOptions {
        if(raytracing==null||upscaler==null||quality==null||denoisePasses<1||denoisePasses>5
                ||!Float.isFinite(sharpness)||sharpness<0||sharpness>1
                ||frameGeneration<1||frameGeneration>4||frameLimit<0||frameLimit>360)
            throw new IllegalArgumentException("Invalid graphics options");
    }
    public static GraphicsOptions defaults() { return new GraphicsOptions(Raytracing.AUTO,Upscaler.OFF,Quality.QUALITY,4,.2f,true,1,0); }
    public GraphicsOptions withRaytracing(Raytracing v) { return new GraphicsOptions(v,upscaler,quality,denoisePasses,sharpness,vsync,frameGeneration,frameLimit); }
    public GraphicsOptions withUpscaler(Upscaler v) { return new GraphicsOptions(raytracing,v,quality,denoisePasses,sharpness,vsync,frameGeneration,frameLimit); }
    public GraphicsOptions withQuality(Quality v) { return new GraphicsOptions(raytracing,upscaler,v,denoisePasses,sharpness,vsync,frameGeneration,frameLimit); }
    public GraphicsOptions withPasses(int v) { return new GraphicsOptions(raytracing,upscaler,quality,v,sharpness,vsync,frameGeneration,frameLimit); }
    public GraphicsOptions withSharpness(float v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,v,vsync,frameGeneration,frameLimit); }
    public GraphicsOptions withVsync(boolean v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,sharpness,v,frameGeneration,frameLimit); }
    public GraphicsOptions withFrameGeneration(int v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,sharpness,vsync,v,frameLimit); }
    public GraphicsOptions withFrameLimit(int v) { return new GraphicsOptions(raytracing,upscaler,quality,denoisePasses,sharpness,vsync,frameGeneration,v); }
}
