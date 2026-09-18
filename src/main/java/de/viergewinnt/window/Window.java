package de.viergewinnt.window;

import org.lwjgl.glfw.Callbacks;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_DEBUG_CONTEXT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetKey;
import static org.lwjgl.glfw.GLFW.glfwGetMouseButton;
import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWaitEventsTimeout;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.GL_VENDOR;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL11.glGetString;

import de.viergewinnt.Game.Board;
import de.viergewinnt.Game.Game;
import de.viergewinnt.hud.HUD;
import de.viergewinnt.input.Input;
import de.viergewinnt.renderer.PathTracer;
import de.viergewinnt.scene.Camera;
import de.viergewinnt.scene.Scene;

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
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT,GLFW_TRUE);
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT,GLFW_TRUE);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);glfwWindowHint(GLFW_RESIZABLE,GLFW_TRUE);
            window=glfwCreateWindow(1280,720,"4 Gewinnt - Pathtraced Edition",0,0);
            if(window==0) { glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);window=glfwCreateWindow(1280,720,"4 Gewinnt - Pathtraced Edition",0,0); }
            if(window==0) throw new IllegalStateException("OpenGL 4.3+ erforderlich; bitte Grafiktreiber prüfen.");
            glfwMakeContextCurrent(window);glfwSwapInterval(Boolean.getBoolean("pt.benchmark")?0:1);
            if(!GL.createCapabilities().OpenGL43) throw new IllegalStateException("Compute Shader und SSBOs benötigen OpenGL 4.3+.");
            String vendor = glGetString(GL_VENDOR);
            String renderer = glGetString(GL_RENDERER);
            String version = glGetString(GL_VERSION);
            String hardwareSummary = (vendor == null ? "" : vendor) + " " + (renderer == null ? "" : renderer);
            String hardwareLower = hardwareSummary.toLowerCase(java.util.Locale.ROOT);
            if(hardwareLower.contains("microsoft basic render") || hardwareLower.contains("gdi generic") || hardwareLower.contains("llvmpipe") || hardwareLower.contains("software")) {
                throw new IllegalStateException("Es läuft kein echter Grafiktreiber, sondern ein Software-/Fallback-Renderer. Bitte die dedizierte GPU (NVIDIA/AMD/Intel) im Windows-Treiber aktivieren und das Projekt neu starten.");
            }
            System.out.println("GPU: "+renderer+" | Hersteller: "+vendor+" | OpenGL: "+version);
            glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_DISABLED);
            Camera camera=new Camera();
            boolean[] dropKeys=new boolean[Board.COLUMNS];
            Game game = new Game();
            int[] width=new int[1],height=new int[1];double last=glfwGetTime(),titleTime=last;int frames=0;
            int smokeFrames=Integer.getInteger("pt.smokeFrames",0),totalFrames=0;
            boolean paused=false;
            boolean escapeHeld=false;
            boolean mouseHeld=false;
            try(PathTracer tracer=new PathTracer(Scene.fromBoard(game.getBoard()))) {
                while(!glfwWindowShouldClose(window)) {
                    glfwPollEvents();double now=glfwGetTime();float dt=(float)(now-last);last=now;
                    boolean escapePressed=glfwGetKey(window,GLFW_KEY_ESCAPE)==GLFW_PRESS;
                    if(escapePressed && !escapeHeld) {
                        paused = !paused;
                        if(paused) {
                            glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_NORMAL);
                        } else {
                            glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_DISABLED);
                            glfwSetCursorPos(window, width[0] / 2.0, height[0] / 2.0);
                            camera.resetMouseCursor(width[0] / 2.0, height[0] / 2.0);
                        }
                    }
                    escapeHeld=escapePressed;
                    boolean cameraChanged = false;
                    if(!paused) {
                        cameraChanged = camera.update(window,dt);
                        if(cameraChanged) {
                            tracer.reset();
                        }
                    }

                    if(!paused && !game.isGameOver()) {
                        int column = Input.getTriggeredColumn(window, dropKeys);
                        if(column >= 0 && game.play(column)) {
                            tracer.setScene(Scene.fromBoard(game.getBoard()));
                        }
                    }

                    glfwGetFramebufferSize(window,width,height);
                    if(width[0]<=0||height[0]<=0) { glfwWaitEventsTimeout(.05);continue; }
                    boolean mousePressed=glfwGetMouseButton(window,GLFW_MOUSE_BUTTON_LEFT)==GLFW_PRESS;
                    if(paused&&mousePressed&&!mouseHeld) {
                        double[] cursorX=new double[1],cursorY=new double[1];
                        glfwGetCursorPos(window,cursorX,cursorY);
                        double normalizedX=cursorX[0]/width[0],normalizedY=1.0-cursorY[0]/height[0];
                        double menuX=(normalizedX-.5)*1.7778,menuY=normalizedY-.5;
                        if(menuX>=-.28&&menuX<=.28&&menuY>=-.16&&menuY<=-.06) {
                            paused=false;
                            glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_DISABLED);
                            glfwSetCursorPos(window,width[0]/2.0,height[0]/2.0);
                            camera.resetMouseCursor(width[0]/2.0,height[0]/2.0);
                        } else if(menuX>=-.28&&menuX<=.28&&menuY>=-.27&&menuY<=-.17) {
                            game.reset();
                            tracer.setScene(Scene.fromBoard(game.getBoard()));
                        } else if(menuX>=-.28&&menuX<=.28&&menuY>=-.38&&menuY<=-.28) {
                            glfwSetWindowShouldClose(window,true);
                        }
                    }
                    mouseHeld=mousePressed;
                    if(!paused) {
                        tracer.render(camera,width[0],height[0]);
                    } else {
                        tracer.renderPauseOverlay(width[0],height[0]);
                    }
                    glfwSwapBuffers(window);frames++;totalFrames++;
                    if(now-titleTime>=1) {
                        String state = paused ? "PAUSE" : HUD.getStatusText(game);
                        String title=paused
                            ? String.format(java.util.Locale.ROOT,"4 Gewinnt | %.1f FPS | %d spp | PAUSE",frames/(now-titleTime),tracer.samples())
                            : String.format(java.util.Locale.ROOT,"4 Gewinnt | %.1f FPS | %d spp | %s | WASD + rechte Maus | Tasten 1-7",frames/(now-titleTime),tracer.samples(),state);
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
