package de.viergewinnt.renderer;

import java.util.Locale;

/** Immutable settings; sampling changes are applied atomically at a frame boundary. */
public record RenderSettings(Denoiser denoiser,int bounces,int samplesPerFrame,
        int maxWidth,int maxHeight,float denoiseStrength,float exposure,boolean taa,GraphicsOptions graphics) {
    public enum Denoiser { OFF, OWN, ATROUS }
    public RenderSettings {
        if(graphics==null||denoiser==null||bounces<1||bounces>12||samplesPerFrame<1||samplesPerFrame>16
                ||maxWidth<64||maxWidth>3840||maxHeight<64||maxHeight>2160
                ||!Float.isFinite(denoiseStrength)||denoiseStrength<.25f||denoiseStrength>2
                ||!Float.isFinite(exposure)||exposure<.25f||exposure>3)
            throw new IllegalArgumentException("Invalid rendering settings");
    }
    public RenderSettings(Denoiser denoiser,int bounces,int samplesPerFrame,int maxWidth,int maxHeight,float strength,float exposure) {
        this(denoiser,bounces,samplesPerFrame,maxWidth,maxHeight,strength,exposure,true);
    }
    public RenderSettings(Denoiser denoiser,int bounces,int samplesPerFrame,int maxWidth,int maxHeight,float strength,float exposure,boolean taa) {
        this(denoiser,bounces,samplesPerFrame,maxWidth,maxHeight,strength,exposure,taa,GraphicsOptions.defaults());
    }
    public static RenderSettings defaults() { return new RenderSettings(Denoiser.ATROUS,3,4,960,540,1,1); }
    public RenderSettings systemOverrides() {
        Denoiser mode=Boolean.getBoolean("pt.noDenoise")?Denoiser.OFF:
            Denoiser.valueOf(System.getProperty("pt.denoiser",denoiser.name()).toUpperCase(Locale.ROOT));
        return new RenderSettings(mode,Integer.getInteger("pt.bounces",bounces),Integer.getInteger("pt.samplesPerFrame",samplesPerFrame),
            Integer.getInteger("pt.width",maxWidth),Integer.getInteger("pt.height",maxHeight),
            Float.parseFloat(System.getProperty("pt.denoiseStrength",Float.toString(denoiseStrength))),
            Float.parseFloat(System.getProperty("pt.exposure",Float.toString(exposure))),Boolean.parseBoolean(System.getProperty("pt.taa",Boolean.toString(taa))),graphics);
    }
    public boolean sameSampling(RenderSettings other) {
        return bounces==other.bounces&&samplesPerFrame==other.samplesPerFrame&&maxWidth==other.maxWidth&&maxHeight==other.maxHeight
            &&graphics.raytracing()==other.graphics.raytracing()&&graphics.upscaler()==other.graphics.upscaler()&&graphics.quality()==other.graphics.quality();
    }
    public RenderSettings withTaa(boolean v) { return new RenderSettings(denoiser,bounces,samplesPerFrame,maxWidth,maxHeight,denoiseStrength,exposure,v,graphics); }
    public RenderSettings withDenoiser(Denoiser v) { return new RenderSettings(v,bounces,samplesPerFrame,maxWidth,maxHeight,denoiseStrength,exposure,taa,graphics); }
    public RenderSettings withBounces(int v) { return new RenderSettings(denoiser,v,samplesPerFrame,maxWidth,maxHeight,denoiseStrength,exposure,taa,graphics); }
    public RenderSettings withSamples(int v) { return new RenderSettings(denoiser,bounces,v,maxWidth,maxHeight,denoiseStrength,exposure,taa,graphics); }
    public RenderSettings withResolution(int w,int h) { return new RenderSettings(denoiser,bounces,samplesPerFrame,w,h,denoiseStrength,exposure,taa,graphics); }
    public RenderSettings withStrength(float v) { return new RenderSettings(denoiser,bounces,samplesPerFrame,maxWidth,maxHeight,v,exposure,taa,graphics); }
    public RenderSettings withExposure(float v) { return new RenderSettings(denoiser,bounces,samplesPerFrame,maxWidth,maxHeight,denoiseStrength,v,taa,graphics); }
    public RenderSettings withGraphics(GraphicsOptions v) { return new RenderSettings(denoiser,bounces,samplesPerFrame,maxWidth,maxHeight,denoiseStrength,exposure,taa,v); }
}
