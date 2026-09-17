package de.viergewinnt.window;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

public class Window {
    private long window;

    public void create() {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("GLFW konnte nicht initialisiert werden.");
        }

        GLFW.glfwDefaultWindowHints();
        window = GLFW.glfwCreateWindow(
                1280,
                720,
                "4 Gewinnt - Pathtraced Edition",
                0,
                0
        );

        if (window == 0) {
            GLFW.glfwTerminate();
            throw new IllegalStateException("Fenster konnte nicht erstellt werden.");
        }

        GLFW.glfwSetWindowPos(window, 320, 180);
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GL.createCapabilities();
        GL11.glClearColor(0.05f, 0.05f, 0.05f, 1.0f);

        while (!GLFW.glfwWindowShouldClose(window)) {
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                GLFW.glfwSetWindowShouldClose(window, true);
            }

            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }

        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}