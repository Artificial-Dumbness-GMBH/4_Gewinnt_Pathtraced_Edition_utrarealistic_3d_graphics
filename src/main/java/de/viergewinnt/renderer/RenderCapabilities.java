package de.viergewinnt.renderer;

/** Runtime-tested support, never inferred from a saved user preference. */
public record RenderCapabilities(boolean dx12,boolean hardwareRaytracing,boolean fsr,boolean xess,int maxFrameGeneration,boolean dlss,boolean rayReconstruction,boolean rayRegeneration) {
    public RenderCapabilities {
        if(maxFrameGeneration<1||maxFrameGeneration>4) throw new IllegalArgumentException("Frame generation limit");
    }
    public RenderCapabilities(boolean dx12,boolean rt,boolean fsr,boolean xess,int fg) { this(dx12,rt,fsr,xess,fg,false,false,false); }
    public static RenderCapabilities openGL() { return new RenderCapabilities(false,false,false,false,1); }
}

