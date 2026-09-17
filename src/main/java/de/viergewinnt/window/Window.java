package de.viergewinnt.window;

import de.viergewinnt.Game.Board;
import de.viergewinnt.Game.Player;
import de.viergewinnt.scene.Camera;
import de.viergewinnt.scene.Scene;
import de.viergewinnt.renderer.PathTracer;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;

public class Window {
    private long window;
    /** Retains the existing entry point. */
    public void create() { create(new Board()); }
    public void create(Board board) {
        GLFWErrorCallback error=GLFWErrorCallback.createPrint(System.err);
        glfwSetErrorCallback(error);
        try {
            if(!glfwInit()) throw new IllegalStateException("GLFW konnte nicht initialisiert werden.");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,6);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);glfwWindowHint(GLFW_RESIZABLE,GLFW_TRUE);
            window=glfwCreateWindow(1280,720,"4 Gewinnt - Pathtraced Edition",0,0);
            // Compute/SSBOs only require 4.3; useful on older Intel drivers.
            if(window==0) { glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);window=glfwCreateWindow(1280,720,"4 Gewinnt - Pathtraced Edition",0,0); }
            if(window==0) throw new IllegalStateException("OpenGL 4.3+ erforderlich; bitte Grafiktreiber prüfen.");
            glfwMakeContextCurrent(window);glfwSwapInterval(Boolean.getBoolean("pt.benchmark")?0:1);
            if(!GL.createCapabilities().OpenGL43) throw new IllegalStateException("Compute Shader und SSBOs benötigen OpenGL 4.3+.");
            System.out.println("GPU: "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
            Camera camera=new Camera();boolean[] keys=new boolean[Board.COLUMNS];Player turn=Player.Red;
            int[] width=new int[1],height=new int[1];double last=glfwGetTime(),titleTime=last;int frames=0;
            int smokeFrames=Integer.getInteger("pt.smokeFrames",0),totalFrames=0;
            try(PathTracer tracer=new PathTracer(Scene.fromBoard(board))) {
                while(!glfwWindowShouldClose(window)) {
                    glfwPollEvents();double now=glfwGetTime();float dt=(float)(now-last);last=now;
                    if(glfwGetKey(window,GLFW_KEY_ESCAPE)==GLFW_PRESS) glfwSetWindowShouldClose(window,true);
                    if(camera.update(window,dt)) tracer.reset();
                    for(int c=0;c<Board.COLUMNS;c++) {
                        boolean pressed=glfwGetKey(window,GLFW_KEY_1+c)==GLFW_PRESS;
                        if(pressed&&!keys[c]&&board.dropPiece(c,turn)) {
                            turn=turn==Player.Red?Player.Blue:Player.Red;tracer.setScene(Scene.fromBoard(board));
                        }
                        keys[c]=pressed;
                    }
                    glfwGetFramebufferSize(window,width,height);
                    if(width[0]<=0||height[0]<=0) { glfwWaitEventsTimeout(.05);continue; }
                    tracer.render(camera,width[0],height[0]);glfwSwapBuffers(window);frames++;totalFrames++;
                    if(now-titleTime>=1) {
                        String title=String.format(java.util.Locale.ROOT,"4 Gewinnt | %.1f FPS | %d spp | WASD + rechte Maus | Spalte 1-7",frames/(now-titleTime),tracer.samples());
                        glfwSetWindowTitle(window,title);
                        if(Boolean.getBoolean("pt.benchmark")) System.out.println(title);
                        frames=0;titleTime=now;
                    }
                    if(smokeFrames>0) {
                        int glError=glGetError();if(glError!=GL_NO_ERROR) throw new IllegalStateException("OpenGL error: "+glError);
                        if(totalFrames>=smokeFrames) glfwSetWindowShouldClose(window,true);
                    }
                }
            }
        } finally {
            if(window!=0) { Callbacks.glfwFreeCallbacks(window);glfwDestroyWindow(window);window=0; }
            GL.setCapabilities(null);glfwTerminate();glfwSetErrorCallback(null);error.free();
        }
    }
}
