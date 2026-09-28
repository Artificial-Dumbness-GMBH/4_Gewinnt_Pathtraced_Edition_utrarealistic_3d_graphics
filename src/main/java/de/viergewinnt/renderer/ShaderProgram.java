package de.viergewinnt.renderer;

import de.viergewinnt.scene.Vec3;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.lwjgl.opengl.GL43.*;

public class ShaderProgram implements AutoCloseable {
    protected final int id;
    private final Map<String,Integer> locations=new HashMap<>();
    public ShaderProgram(String[] paths,int[] types,String defines) {
        id=glCreateProgram();
        try {
            for(int i=0;i<paths.length;i++) {
                String source=read(paths[i]);
                int end=source.indexOf('\n');
                source=source.substring(0,end+1)+defines+source.substring(end+1);
                int shader=glCreateShader(types[i]);
                try {
                    glShaderSource(shader,source);glCompileShader(shader);
                    if(glGetShaderi(shader,GL_COMPILE_STATUS)==GL_FALSE) throw new IllegalStateException(paths[i]+": "+glGetShaderInfoLog(shader));
                    glAttachShader(id,shader);
                } finally { glDeleteShader(shader); }
            }
            glLinkProgram(id);
            if(glGetProgrami(id,GL_LINK_STATUS)==GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(id));
        } catch(RuntimeException e) { glDeleteProgram(id);throw e; }
    }
    private static String read(String path) {
        try(InputStream in=ShaderProgram.class.getResourceAsStream(path)) {
            if(in==null) throw new IllegalArgumentException("Missing shader: "+path);
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;
            while((n=in.read(bytes))!=-1) out.write(bytes,0,n);
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        } catch(IOException e) { throw new IllegalStateException(path,e); }
    }
    public void use() { glUseProgram(id); }
    private int location(String name) { return locations.computeIfAbsent(name,n->glGetUniformLocation(id,n)); }
    public void scalar(String name,float value) { glUniform1f(location(name),value); }
    public void integer(String name,int value) { glUniform1i(location(name),value); }
    public void vector(String name,Vec3 v) { glUniform3f(location(name),v.x,v.y,v.z); }
    @Override public void close() { glDeleteProgram(id); }
}
