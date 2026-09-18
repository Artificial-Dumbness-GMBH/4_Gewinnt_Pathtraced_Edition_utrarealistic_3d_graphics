package de.viergewinnt.renderer;

import de.viergewinnt.Game.*;
import de.viergewinnt.scene.*;
import java.util.*;
import static org.lwjgl.opengl.GL43.*;

public final class PrecisionRenderTest {
    public static void run() {
        String previous=System.getProperty("pt.precision");
        Set<String> extensions=new HashSet<>();for(int i=0;i<glGetInteger(GL_NUM_EXTENSIONS);i++) extensions.add(glGetStringi(GL_EXTENSIONS,i));
        boolean supported=extensions.contains(ShaderPrecision.AMD_HALF)||extensions.contains(ShaderPrecision.NV_HALF);
        Board board=new Board();for(int i=0;i<12;i++)board.dropPiece(i%7,i%2==0?Player.Red:Player.Blue);
        Scene scene=Scene.fromBoard(board);Camera camera=new Camera();
        RenderSettings settings=RenderSettings.defaults().withTaa(false).withDenoiser(RenderSettings.Denoiser.OFF).withBounces(4).withSamples(2);
        float[][] output=new float[2][],guides=new float[2][];
        try {
            for(int mode=0;mode<2;mode++) {
                System.setProperty("pt.precision",mode==0?"fp32":"fp16");
                try(PathTracer pt=new PathTracer(scene,settings)) {
                    if(pt.usesFp16Arithmetic()!=(mode==1&&supported)) throw new AssertionError("advertised arithmetic path failed to compile or wrong selection");
                    for(int frame=0;frame<8;frame++)pt.render(camera,48,32);
                    output[mode]=new float[48*32*4];glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,output[mode]);
                    guides[mode]=new float[48*32*4];glBindTexture(GL_TEXTURE_2D,pt.depthGuideTexture());glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,guides[mode]);
                    System.out.println("Precision render: "+pt.precisionDescription());
                }
            }
            double error=0,energy=0;
            for(int i=0;i<output[0].length;i++) {
                if(!Float.isFinite(output[0][i])||!Float.isFinite(output[1][i])) throw new AssertionError("nonfinite precision output");
                if(guides[0][i]!=guides[1][i])throw new AssertionError("geometry guide changed with shading precision");
                if(i%4!=3) { error+=Math.pow(output[0][i]-output[1][i],2);energy+=Math.pow(output[0][i],2); }
            }
            double relative=Math.sqrt(error/Math.max(energy,1e-20));
            if(relative>.015)throw new AssertionError("FP16 image error exceeds 1.5% relative RMSE: "+relative);
            if(!supported&&error!=0)throw new AssertionError("fallback must match FP32 exactly");
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("precision GL error");
            System.out.printf(Locale.ROOT,"Precision GPU comparison passed: relative HDR RMSE %.6f%%, identical depth/normals. Native FP16 arithmetic %s.%n",relative*100,supported?"tested":"unavailable; fallback tested");
        } finally { if(previous==null)System.clearProperty("pt.precision");else System.setProperty("pt.precision",previous); }
    }
}
