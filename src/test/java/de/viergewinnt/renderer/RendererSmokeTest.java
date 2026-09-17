package de.viergewinnt.renderer;

import de.viergewinnt.Game.*;
import de.viergewinnt.scene.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;
public class RendererSmokeTest {
 public static void main(String[] args) {
  GLFWErrorCallback errorCallback=GLFWErrorCallback.createPrint(System.err).set();
  String previousBruteForce=System.getProperty("pt.bruteForce");
  String previousGradient=System.getProperty("pt.gradient");
  long window=0;
  try {
  System.setProperty("pt.gradient","false");
  glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_NULL);
  if(!glfwInit()) throw new AssertionError("init");
  glfwWindowHint(GLFW_CONTEXT_CREATION_API,GLFW_EGL_CONTEXT_API);
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
  glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
  window=glfwCreateWindow(96,54,"smoke",0,0);
  if(window==0) throw new AssertionError("context");
  glfwMakeContextCurrent(window);GL.createCapabilities();
  Board board=new Board();board.dropPiece(0,Player.Red);
  try(PathTracer pt=new PathTracer(Scene.fromBoard(board))) {
   Camera c=new Camera();pt.render(c,96,54);pt.render(c,96,54);
   if(pt.samples()!=2) throw new AssertionError("accumulation");
   pt.reset();pt.render(c,96,54);if(pt.samples()!=1) throw new AssertionError("reset");
   pt.render(c,80,50);if(pt.samples()!=1) throw new AssertionError("resize");
   board.dropPiece(1,Player.Blue);pt.setScene(Scene.fromBoard(board));pt.render(c,80,50);
   if(pt.samples()!=1) throw new AssertionError("scene reset");
   glFinish();int error=glGetError();if(error!=0) throw new AssertionError("GL error "+error);
   float[] pixels=new float[80*50*4];glReadPixels(0,0,80,50,GL_RGBA,GL_FLOAT,pixels);
   float min=1,max=0;
   for(int i=0;i<pixels.length;i+=4) {float v=pixels[i];if(!Float.isFinite(v)) throw new AssertionError("nonfinite");min=Math.min(min,v);max=Math.max(max,v);}
   if(max-min<.01) throw new AssertionError("blank image");
   System.out.println("GPU smoke passed: "+glGetString(GL_RENDERER)+", output range "+min+".."+max);
  }
  float[][] reference=new float[2][];
  for(int mode=0;mode<2;mode++) {
   System.setProperty("pt.bruteForce",Boolean.toString(mode==1));
   try(PathTracer pt=new PathTracer(Scene.fromBoard(board))) {
    pt.render(new Camera(),96,54);
    int textureWidth=glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH);
    int textureHeight=glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT);
    reference[mode]=new float[textureWidth*textureHeight*4];
    glMemoryBarrier(GL_TEXTURE_UPDATE_BARRIER_BIT);
    glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,reference[mode]);
   }
  }
  for(int i=0;i<reference[0].length;i++) {
   if(!Float.isFinite(reference[0][i])||!Float.isFinite(reference[1][i])||Math.abs(reference[0][i]-reference[1][i])>1e-4f)
    throw new AssertionError("BVH/brute-force shader mismatch at "+i);
  }
  System.out.println("GPU BVH/brute-force image comparison passed");
  if(glGetError()!=GL_NO_ERROR) throw new AssertionError("GL error during reference comparison");
  } finally {
   if(previousBruteForce==null) System.clearProperty("pt.bruteForce");else System.setProperty("pt.bruteForce",previousBruteForce);
   if(previousGradient==null) System.clearProperty("pt.gradient");else System.setProperty("pt.gradient",previousGradient);
   if(window!=0) glfwDestroyWindow(window);
   GL.setCapabilities(null);glfwTerminate();glfwSetErrorCallback(null);errorCallback.free();
  }
 }
}
