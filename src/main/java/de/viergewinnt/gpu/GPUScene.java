package de.viergewinnt.gpu;

import de.viergewinnt.bvh.*;
import de.viergewinnt.scene.*;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL43.*;

/** Owns four immutable SSBOs. std430 strides: vertex 16, triangle 16, material 48, node 48. */
public final class GPUScene implements AutoCloseable {
    private final int[] buffers=new int[4];
    public final int triangleCount;
    public GPUScene(Scene scene) {
        BVHData bvh=BVHBuilder.build(scene.mesh);triangleCount=bvh.triangles.length;
        try {
            upload(0,Math.multiplyExact(scene.mesh.vertices.size(),16),data->{
                for(Vec3 v:scene.mesh.vertices) vector(data,v);
            });
            upload(1,Math.multiplyExact(triangleCount,16),data->{
                for(Triangle t:bvh.triangles) data.putInt(t.a).putInt(t.b).putInt(t.c).putInt(t.material);
            });
            upload(2,Math.multiplyExact(scene.materials.size(),48),data->{
                for(Material m:scene.materials) { vector(data,m.baseColor);vector(data,m.emission);data.putFloat(m.roughness).putFloat(m.metallic).putFloat(m.texture).putFloat(m.textureScale); }
            });
            upload(3,Math.multiplyExact(bvh.nodes.size(),48),data->{
                for(BVHNode n:bvh.nodes) { vector(data,n.min);vector(data,n.max);data.putInt(n.left).putInt(n.right).putInt(n.first).putInt(n.count); }
            });
        } catch(RuntimeException e) { close();throw e; }
    }
    private static void vector(ByteBuffer b,Vec3 v) { b.putFloat(v.x).putFloat(v.y).putFloat(v.z).putFloat(0); }
    private void upload(int binding,int size,java.util.function.Consumer<ByteBuffer> writer) {
        if(size<=0||size>glGetInteger64(GL_MAX_SHADER_STORAGE_BLOCK_SIZE)) throw new IllegalArgumentException("SSBO size unsupported: "+size);
        ByteBuffer data=MemoryUtil.memAlloc(size);
        try {
            writer.accept(data);data.flip();buffers[binding]=glGenBuffers();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER,buffers[binding]);glBufferData(GL_SHADER_STORAGE_BUFFER,data,GL_STATIC_DRAW);
        } finally { MemoryUtil.memFree(data); }
    }
    public void bind() { for(int i=0;i<buffers.length;i++) glBindBufferBase(GL_SHADER_STORAGE_BUFFER,i,buffers[i]); }
    @Override public void close() { for(int i=0;i<buffers.length;i++) if(buffers[i]!=0) { glDeleteBuffers(buffers[i]);buffers[i]=0; } }
}
