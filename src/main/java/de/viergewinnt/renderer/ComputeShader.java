package de.viergewinnt.renderer;

import static org.lwjgl.opengl.GL43.*;

public final class ComputeShader extends ShaderProgram {
    public ComputeShader(int x,int y,boolean half) {
        super(new String[]{"/shaders/pathtrace.comp"},new int[]{GL_COMPUTE_SHADER},
              "#define GROUP_X "+x+"\n#define GROUP_Y "+y+"\n#define IMAGE_FORMAT "+(half?"rgba16f":"rgba32f")+"\n");
    }
}
