package de.viergewinnt.renderer;

import de.viergewinnt.Game.*;
import de.viergewinnt.scene.*;
import static org.lwjgl.opengl.GL43.*;

public final class DropRenderTest {
    public static void run() {
        Game game=new Game();DropAnimation drop=new DropAnimation();drop.start(game,3);
        Scene scene=Scene.fromBoard(game.getBoard(),drop);
        RenderSettings settings=RenderSettings.defaults().withSamples(1).withBounces(2).withDenoiser(RenderSettings.Denoiser.OFF);
        float[][][] images=new float[2][3][];
        for(int mode=0;mode<2;mode++) {
            System.setProperty("pt.bruteForce",Boolean.toString(mode==1));
            try(PathTracer pt=new PathTracer(scene,settings)) {
                for(int frame=0;frame<3;frame++) {
                    pt.setDropLift(scene.movingMaxLift*(1-frame*.5f));pt.render(new Camera(),48,32);
                    if(pt.samples()!=1) throw new AssertionError("moving geometry must clear accumulation");
                    images[mode][frame]=new float[48*32*4];glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,images[mode][frame]);
                }
                pt.render(new Camera(),48,32);if(pt.samples()!=2) throw new AssertionError("stopped geometry must accumulate");
            }
        }
        System.clearProperty("pt.bruteForce");
        double change=0;
        for(int frame=0;frame<3;frame++) for(int i=0;i<images[0][frame].length;i++) {
            if(!Float.isFinite(images[0][frame][i])||Math.abs(images[0][frame][i]-images[1][frame][i])>1e-4)
                throw new AssertionError("moving BVH/brute force mismatch");
            change+=Math.abs(images[0][frame][i]-images[0][0][i]);
        }
        if(change<.01||glGetError()!=GL_NO_ERROR) throw new AssertionError("coin did not move or GL error");
        System.out.println("Moving coin GPU checks passed: spawn/mid-flight/landing BVH matches brute force, image changes, accumulation resets/resumes.");
    }
}
