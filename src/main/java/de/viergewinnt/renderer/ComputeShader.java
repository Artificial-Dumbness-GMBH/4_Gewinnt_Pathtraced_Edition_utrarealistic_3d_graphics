package de.viergewinnt.renderer;

import java.util.HashSet;
import java.util.Set;
import static org.lwjgl.opengl.GL43.*;

public final class ComputeShader extends ShaderProgram {
    public final ShaderPrecision.Choice precision;
    /** Baseline constructor retained for deterministic reference tests. */
    public ComputeShader(int x,int y,boolean half) { this(x,y,half,new ShaderPrecision.Choice("","FP32 reference")); }
    private ComputeShader(int x,int y,boolean half,ShaderPrecision.Choice precision) {
        super(new String[]{"/shaders/pathtrace.comp"},new int[]{GL_COMPUTE_SHADER},defines(x,y,half,precision));
        this.precision=precision;
    }
    @SuppressWarnings("resource") // Ownership of the compiled program transfers to PathTracer.
    public static ComputeShader create(int x,int y,boolean half) {
        Set<String> extensions=new HashSet<>();
        for(int i=0;i<glGetInteger(GL_NUM_EXTENSIONS);i++) extensions.add(glGetStringi(GL_EXTENSIONS,i));
        ShaderPrecision.Choice choice=ShaderPrecision.select(ShaderPrecision.Mode.parse(System.getProperty("pt.precision","auto")),
            glGetString(GL_VENDOR),glGetString(GL_RENDERER),extensions);
        return ShaderPrecision.compile(choice,c->new ComputeShader(x,y,half,c),System.err::println);
    }
    private static String defines(int x,int y,boolean half,ShaderPrecision.Choice precision) {
        return (precision.fp16()?"#extension "+precision.extension()+" : require\n#define PACKED_FP16 1\n":"")
            +(precision.extension().equals(ShaderPrecision.AMD_HALF)?"#define FP16_FMA 1\n":"")
            +"#define GROUP_X "+x+"\n#define GROUP_Y "+y+"\n#define IMAGE_FORMAT "+(half?"rgba16f":"rgba32f")+"\n";
    }
}
