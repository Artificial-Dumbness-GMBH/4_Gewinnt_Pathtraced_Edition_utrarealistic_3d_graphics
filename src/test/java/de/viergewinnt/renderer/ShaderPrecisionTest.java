package de.viergewinnt.renderer;

import java.util.*;
public final class ShaderPrecisionTest {
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Set<String> amd=Set.of(ShaderPrecision.AMD_HALF),nv=Set.of(ShaderPrecision.NV_HALF);
        for(String vendor:new String[]{"ATI Technologies Inc.","Advanced Micro Devices, Inc.","AMD"}) {
            check(ShaderPrecision.select(ShaderPrecision.Mode.AUTO,vendor,"GPU",amd).fp16(),"AMD auto");
            check(!ShaderPrecision.select(ShaderPrecision.Mode.AUTO,vendor,"GPU",Set.of()).fp16(),"AMD missing extension");
        }
        check(ShaderPrecision.select(ShaderPrecision.Mode.AUTO,"Mesa","AMD Radeon RX",amd).fp16(),"Mesa AMD detection");
        for(String vendor:new String[]{"Intel","NVIDIA Corporation","Mesa/X.org"}) {
            check(!ShaderPrecision.select(ShaderPrecision.Mode.AUTO,vendor,"GPU",amd).fp16(),"extension name is not GPU vendor");
            check(ShaderPrecision.select(ShaderPrecision.Mode.FP16,vendor,"GPU",amd).fp16(),"manual extension support");
        }
        check(ShaderPrecision.select(ShaderPrecision.Mode.FP16,"NVIDIA","GPU",nv).extension().equals(ShaderPrecision.NV_HALF),"NV half extension");
        check(!ShaderPrecision.select(ShaderPrecision.Mode.FP16,"Intel","GPU",Set.of()).fp16(),"manual unsupported fallback");
        check(!ShaderPrecision.select(ShaderPrecision.Mode.FP32,"AMD","Radeon",amd).fp16(),"forced FP32");
        check(ShaderPrecision.Mode.parse("Fp16")==ShaderPrecision.Mode.FP16,"case independent mode");
        try { ShaderPrecision.Mode.parse("fp8");throw new AssertionError("invalid mode"); } catch(IllegalArgumentException expected) { }
        var half=ShaderPrecision.select(ShaderPrecision.Mode.AUTO,"AMD","GPU",amd);
        List<String> warnings=new ArrayList<>();int[] calls={0};
        ShaderPrecision.Choice fallback=ShaderPrecision.compile(half,c->{ calls[0]++;if(c.fp16()) throw new IllegalStateException("simulated driver compiler rejection");return c; },warnings::add);
        check(calls[0]==2&&warnings.size()==1&&!fallback.fp16(),"compile fallback");
        calls[0]=0;
        try { ShaderPrecision.compile(new ShaderPrecision.Choice("","baseline"),c->{calls[0]++;throw new IllegalStateException("baseline error");},warnings::add);throw new AssertionError("baseline failure swallowed"); }
        catch(IllegalStateException expected) { check(calls[0]==1,"baseline retried"); }
        System.out.println("Precision policy passed: AMD/non-AMD, extensions, overrides, compiler fallback and baseline failure.");
    }
}
