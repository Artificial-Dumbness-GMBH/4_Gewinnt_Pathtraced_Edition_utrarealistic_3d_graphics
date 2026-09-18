package de.viergewinnt.renderer;

import de.viergewinnt.Game.*;
import de.viergewinnt.scene.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;
public class RendererSmokeTest {
 public static void main(String[] args) {
  GLFWErrorCallback.createPrint(System.err).set();
  glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_NULL);
  if(!glfwInit()) throw new AssertionError("init");
  glfwWindowHint(GLFW_CONTEXT_CREATION_API,GLFW_EGL_CONTEXT_API);
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
  glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
  long window=glfwCreateWindow(96,54,"smoke",0,0);
  if(window==0) throw new AssertionError("context");
  glfwMakeContextCurrent(window);GL.createCapabilities();
  Board board=new Board();board.dropPiece(0,Player.Red);
  try(PathTracer pt=new PathTracer(Scene.fromBoard(board))) {
   Camera c=new Camera();pt.render(c,96,54);pt.render(c,96,54);
   if(pt.samples()!=2*Integer.getInteger("pt.samplesPerFrame",4)) throw new AssertionError("accumulation");
   pt.reset();pt.render(c,96,54);if(pt.samples()!=Integer.getInteger("pt.samplesPerFrame",4)) throw new AssertionError("reset");
   pt.render(c,80,50);if(pt.samples()!=Integer.getInteger("pt.samplesPerFrame",4)) throw new AssertionError("resize");
   board.dropPiece(1,Player.Blue);pt.setScene(Scene.fromBoard(board));pt.render(c,80,50);
   if(pt.samples()!=Integer.getInteger("pt.samplesPerFrame",4)) throw new AssertionError("scene reset");
   pt.renderPauseOverlay(80,50);
   pt.render(c,80,50);
   glFinish();int error=glGetError();if(error!=0) throw new AssertionError("GL error "+error);
   float[] pixels=new float[80*50*4];glReadPixels(0,0,80,50,GL_RGBA,GL_FLOAT,pixels);
   float min=1,max=0;
   for(int i=0;i<pixels.length;i+=4) {float v=pixels[i];if(!Float.isFinite(v)) throw new AssertionError("nonfinite");min=Math.min(min,v);max=Math.max(max,v);}
   if(max-min<.01) throw new AssertionError("blank image");
   glActiveTexture(GL_TEXTURE1);
   float[] guides=new float[80*50*4];glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,guides);
   for(int i=0;i<guides.length;i+=4) {
    for(int j=0;j<4;j++) if(!Float.isFinite(guides[i+j])) throw new AssertionError("nonfinite guide");
    if(guides[i+3]>0) {
     float length=guides[i]*guides[i]+guides[i+1]*guides[i+1]+guides[i+2]*guides[i+2];
     if(Math.abs(length-1)>1e-4) throw new AssertionError("guide normal is not normalized");
    }
   }
   glActiveTexture(GL_TEXTURE0);
   RenderSettings original=pt.settings();int accumulated=pt.samples();
   for(RenderSettings.Denoiser mode:RenderSettings.Denoiser.values()) {
    pt.applySettings(original.withDenoiser(mode));pt.renderPauseOverlay(80,50);
    if(pt.samples()!=accumulated) throw new AssertionError("denoiser switch reset raw accumulation");
    glFinish();if(glGetError()!=GL_NO_ERROR) throw new AssertionError("denoiser switch GL error");
   }
   pt.applySettings(original.withExposure(.5f));pt.renderPauseOverlay(80,50);
   float[] dark=new float[80*50*4];glReadPixels(0,0,80,50,GL_RGBA,GL_FLOAT,dark);
   pt.applySettings(original.withExposure(2));pt.renderPauseOverlay(80,50);
   float[] bright=new float[dark.length];glReadPixels(0,0,80,50,GL_RGBA,GL_FLOAT,bright);
   double gain=0;for(int i=0;i<bright.length;i+=4) gain+=bright[i]-dark[i];
   if(gain<10||pt.samples()!=accumulated) throw new AssertionError("live exposure");
   pt.applySettings(original.withBounces(original.bounces()==8?7:original.bounces()+1));
   if(pt.samples()!=0) throw new AssertionError("bounce change must reset");
   pt.render(c,80,50);
   pt.applySettings(original.withSamples(8));if(pt.samples()!=0) throw new AssertionError("sample setting must reset");
   pt.render(c,80,50);if(pt.samples()!=8) throw new AssertionError("live sample count");
   pt.applySettings(original.withResolution(64,64));pt.render(c,96,54);
   if(glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH)!=64) throw new AssertionError("live resolution");
   for(int i=0;i<64;i++) pt.renderPaused(c,96,54);
   int settled=pt.samples();pt.renderPaused(c,96,54);
   if(settled<64||pt.samples()!=settled) throw new AssertionError("pause did not settle/freeze");
   System.out.println("GPU smoke passed: "+glGetString(GL_RENDERER)+", output range "+min+".."+max);
  }
  float[][] reference=new float[2][];
  for(int mode=0;mode<2;mode++) {
   System.setProperty("pt.bruteForce",Boolean.toString(mode==1));
   try(PathTracer pt=new PathTracer(Scene.fromBoard(board))) {
    pt.render(new Camera(),96,54);
    reference[mode]=new float[96*54*4];
    glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,reference[mode]);
   }
  }
  for(int i=0;i<reference[0].length;i++) {
   if(!Float.isFinite(reference[0][i])||Math.abs(reference[0][i]-reference[1][i])>1e-4f)
    throw new AssertionError("BVH/brute-force shader mismatch at "+i);
  }
  System.out.println("GPU BVH/brute-force image comparison passed");
  System.clearProperty("pt.bruteForce");
  for(boolean half:new boolean[]{false,true}) for(int[] group:new int[][]{{8,8},{16,8},{16,16}}) {
   System.setProperty("pt.half",Boolean.toString(half));
   System.setProperty("pt.groupX",Integer.toString(group[0]));System.setProperty("pt.groupY",Integer.toString(group[1]));
   try(PathTracer pt=new PathTracer(Scene.fromBoard(board))) {
    pt.render(new Camera(),83,47);glFinish();
    if(glGetError()!=GL_NO_ERROR) throw new AssertionError("format/workgroup variant");
   }
  }
  System.out.println("Six format/workgroup variants passed");
  System.clearProperty("pt.half");
  DropRenderTest.run();
  AtrousDenoiserTest.run();
  de.viergewinnt.hud.HologramSmokeTest.run();
  glfwDestroyWindow(window);glfwTerminate();
 }
}
