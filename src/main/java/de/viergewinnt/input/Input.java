package de.viergewinnt.input;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_1;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;

public final class Input {
    private Input() {
    }

    public static boolean isPressed(long window, int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
    }

    public static int getColumn(long window) {
        for (int column = 0; column < 7; column++) {
            if (isPressed(window, GLFW_KEY_1 + column)) {
                return column;
            }
        }
        return -1;
    }

    public static int getTriggeredColumn(long window, boolean[] previousKeys) {
        for (int column = 0; column < 7; column++) {
            boolean pressed = isPressed(window, GLFW_KEY_1 + column);
            if (pressed && !previousKeys[column]) {
                previousKeys[column] = true;
                return column;
            }
            if (!pressed) {
                previousKeys[column] = false;
            }
        }
        return -1;
    }
}