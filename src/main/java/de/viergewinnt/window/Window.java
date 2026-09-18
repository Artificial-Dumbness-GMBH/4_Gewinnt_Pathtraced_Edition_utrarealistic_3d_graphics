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
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
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
import de.viergewinnt.hud.HologramRenderer;
import de.viergewinnt.input.Input;
import de.viergewinnt.renderer.PathTracer;
import de.viergewinnt.scene.Camera;
import de.viergewinnt.scene.Scene;
import de.viergewinnt.renderer.RenderSettings;
import de.viergewinnt.renderer.SettingsStore;
import de.viergewinnt.ui.PauseMenu;
import de.viergewinnt.ui.MenuRenderer;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;

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
            boolean[] dropKeys=new boolean[Board.COLUMNS],menuKeys=new boolean[4];
            Game game=new Game(board);
            RenderSettings settings=SettingsStore.load(SettingsStore.defaultPath()).systemOverrides();
            PauseMenu menu=new PauseMenu(settings);
            int[] width=new int[1],height=new int[1],windowWidth=new int[1],windowHeight=new int[1];
            double[] cursorX=new double[1],cursorY=new double[1];
            double last=glfwGetTime(),titleTime=last,lastMouseX=Double.NaN,lastMouseY=Double.NaN;
            int frames=0,totalFrames=0,smokeFrames=Integer.getInteger("pt.smokeFrames",0);
            boolean paused=false,escapeHeld=false,mouseHeld=false;
            try(PathTracer tracer=new PathTracer(Scene.fromBoard(game.getBoard()),settings);MenuRenderer menuRenderer=new MenuRenderer();HologramRenderer hologram=new HologramRenderer()) {
                while(!glfwWindowShouldClose(window)) {
                    glfwPollEvents();double now=glfwGetTime();float dt=(float)(now-last);last=now;
                    glfwGetWindowSize(window,windowWidth,windowHeight);glfwGetFramebufferSize(window,width,height);
                    if(width[0]<=0||height[0]<=0||windowWidth[0]<=0||windowHeight[0]<=0) { glfwWaitEventsTimeout(.05);continue; }
                    boolean escape=glfwGetKey(window,GLFW_KEY_ESCAPE)==GLFW_PRESS;
                    if(escape&&!escapeHeld) {
                        if(!(paused&&menu.back())) {
                            paused=!paused;
                            if(paused) {
                                menu.open(HUD.getStatusText(game));glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_NORMAL);
                                lastMouseX=Double.NaN;lastMouseY=Double.NaN;
                            } else captureMouse(camera,windowWidth[0],windowHeight[0]);
                        }
                    }
                    escapeHeld=escape;
                    int column=Input.getTriggeredColumn(window,dropKeys); // Track releases even while paused.
                    boolean tab=triggered(GLFW_KEY_TAB,menuKeys,0),enter=triggered(GLFW_KEY_ENTER,menuKeys,1);
                    boolean up=triggered(GLFW_KEY_UP,menuKeys,2),down=triggered(GLFW_KEY_DOWN,menuKeys,3);
                    boolean mouse=glfwGetMouseButton(window,GLFW_MOUSE_BUTTON_LEFT)==GLFW_PRESS;
                    if(paused) {
                        glfwGetCursorPos(window,cursorX,cursorY);
                        double[] point=PauseMenu.panelPoint(cursorX[0],cursorY[0],windowWidth[0],windowHeight[0],width[0],height[0]);
                        if(cursorX[0]!=lastMouseX||cursorY[0]!=lastMouseY) menu.hover(point[0],point[1]);
                        lastMouseX=cursorX[0];lastMouseY=cursorY[0];
                        if(tab||up||down) menu.focusNext(up||(tab&&glfwGetKey(window,GLFW_KEY_LEFT_SHIFT)==GLFW_PRESS)?-1:1);
                        PauseMenu.Action action=mouse&&!mouseHeld?menu.click(point[0],point[1]):enter?menu.activateFocused():PauseMenu.Action.NONE;
                        switch(action) {
                            case RESUME -> { paused=false;captureMouse(camera,windowWidth[0],windowHeight[0]); }
                            case RESTART -> { game.reset();tracer.setScene(Scene.fromBoard(game.getBoard()));menu.open(HUD.getStatusText(game)); }
                            case QUIT -> glfwSetWindowShouldClose(window,true);
                            case SETTINGS -> {
                                tracer.applySettings(menu.settings());
                                try { SettingsStore.save(SettingsStore.defaultPath(),menu.settings());menu.saved(true); }
                                catch(java.io.IOException|SecurityException e) { menu.saved(false);System.err.println("Einstellungen nicht gespeichert: "+e.getMessage()); }
                            }
                            default -> { }
                        }
                    } else {
                        camera.update(window,dt); // The tracer detects camera changes itself.
                        if(!game.isGameOver()&&column>=0&&game.play(column)) tracer.setScene(Scene.fromBoard(game.getBoard()));
                    }
                    mouseHeld=mouse;
                    if(glfwWindowShouldClose(window)) break;
                    if(paused) tracer.renderPaused(camera,width[0],height[0]);
                    else tracer.render(camera,width[0],height[0]);
                    hologram.render(game,camera,tracer.depthGuideTexture(),width[0],height[0],(float)(now%3600),paused);
                    if(paused) menuRenderer.render(menu,width[0],height[0]);
                    glfwSwapBuffers(window);frames++;totalFrames++;
                    if(now-titleTime>=1) {
                        String state=paused?"PAUSE":HUD.getStatusText(game);
                        String title=String.format(java.util.Locale.ROOT,"4 Gewinnt | %.1f FPS | %d spp | %s | ESC: Menü",frames/(now-titleTime),tracer.samples(),state);
                        glfwSetWindowTitle(window,title);if(Boolean.getBoolean("pt.benchmark")) System.out.println(title);
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
    private boolean triggered(int key,boolean[] held,int slot) {
        boolean pressed=glfwGetKey(window,key)==GLFW_PRESS,result=pressed&&!held[slot];held[slot]=pressed;return result;
    }
    private void captureMouse(Camera camera,int width,int height) {
        glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_DISABLED);glfwSetCursorPos(window,width/2.0,height/2.0);
        camera.resetMouseCursor(width/2.0,height/2.0);
    }
}
