package de.viergewinnt.renderer;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Arithmetic precision is independent of RGBA16F/32F texture storage. */
public final class ShaderPrecision {
    public static final String AMD_HALF="GL_AMD_gpu_shader_half_float",NV_HALF="GL_NV_gpu_shader5";
    public enum Mode { AUTO, FP32, FP16;
        public static Mode parse(String value) {
            try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
            catch(IllegalArgumentException e) { throw new IllegalArgumentException("pt.precision must be auto, fp32 or fp16",e); }
        }
    }
    public record Choice(String extension,String reason) {
        public boolean fp16() { return !extension.isEmpty(); }
        public String description() { return (fp16()?"Mixed FP16/FP32 via "+extension:"FP32")+" — "+reason; }
    }
    private ShaderPrecision() {}
    public static Choice select(Mode mode,String vendor,String renderer,Set<String> extensions) {
        if(mode==Mode.FP32) return new Choice("","explicit selection");
        String device=(vendor+" "+renderer).toLowerCase(Locale.ROOT);
        boolean amd=device.contains("advanced micro devices")||device.contains("ati technologies")||device.contains("radeon")||device.matches(".*\\bamd\\b.*");
        if(mode==Mode.AUTO&&!amd) return new Choice("","non-AMD default");
        String extension=extensions.contains(AMD_HALF)?AMD_HALF:extensions.contains(NV_HALF)?NV_HALF:"";
        return new Choice(extension,extension.isEmpty()?"FP16 arithmetic extension unavailable; safe fallback":mode==Mode.AUTO?"AMD automatic selection":"explicit FP16 selection");
    }
    /** Retry the baseline only for an optional FP16 shader compile/link failure. */
    public static <T> T compile(Choice choice,Function<Choice,T> compiler,Consumer<String> warning) {
        try { return compiler.apply(choice); }
        catch(IllegalStateException e) {
            if(!choice.fp16()) throw e;
            warning.accept("FP16 shader unavailable; retrying FP32: "+e.getMessage());
            return compiler.apply(new Choice("","FP16 compile/link failed; safe fallback"));
        }
    }
}
