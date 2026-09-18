package de.viergewinnt.renderer;

import de.viergewinnt.Game.*;
import de.viergewinnt.scene.*;
import java.nio.*;
import java.nio.file.*;
import java.util.Arrays;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;

/** Fixed native-resolution comparison; intentionally excludes the new HUD and denoisers. */
public final class RendererBenchmark {
    public static void main(String[] args) throws Exception {
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_NULL);if(!glfwInit()) throw new AssertionError("GLFW");
        glfwWindowHint(GLFW_CONTEXT_CREATION_API,GLFW_EGL_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        int w=320,h=180;long window=glfwCreateWindow(w,h,"benchmark",0,0);
        if(window==0) throw new AssertionError("EGL");glfwMakeContextCurrent(window);GL.createCapabilities();
        Board board=new Board();int[] moves={3,2,3,4,2,4,1,5,3,2,4,5,0,6};
        for(int i=0;i<moves.length;i++) board.dropPiece(moves[i],i%2==0?Player.Red:Player.Blue);
        RenderSettings settings=RenderSettings.defaults().withDenoiser(RenderSettings.Denoiser.OFF).withResolution(w,h).withSamples(2);
        try(PathTracer tracer=new PathTracer(Scene.fromBoard(board),settings)) {
            Camera camera=new Camera();for(int i=0;i<4;i++) tracer.render(camera,w,h);glFinish();
            double[] times=new double[5];
            for(int batch=0;batch<times.length;batch++) {
                long start=System.nanoTime();for(int i=0;i<4;i++) tracer.render(camera,w,h);glFinish();
                times[batch]=(System.nanoTime()-start)/4e6;
            }
            Arrays.sort(times);
            System.out.printf(java.util.Locale.ROOT,"Median %.3f ms/frame | 320x180 native, 2 spp/frame, 3 bounces, %d spp total | %s%n",times[2],tracer.samples(),glGetString(GL_RENDERER));
            if(args.length>0) {
                glMemoryBarrier(GL_TEXTURE_UPDATE_BARRIER_BIT);float[] pixels=new float[w*h*4];glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,pixels);
                ByteBuffer bytes=ByteBuffer.allocate(pixels.length*4).order(ByteOrder.LITTLE_ENDIAN);bytes.asFloatBuffer().put(pixels);Files.write(Path.of(args[0]),bytes.array());
            }
            if(glGetError()!=GL_NO_ERROR) throw new AssertionError("GL error");
        } finally { glfwDestroyWindow(window);glfwTerminate(); }
    }
}
