package de.viergewinnt.renderer;

import de.viergewinnt.bvh.*;
import de.viergewinnt.scene.*;
import java.nio.*;

/** CPU-only ABI shared by software BVH and DXR. Native code copies before returning. */
public record NativeScene(ByteBuffer[] buffers,int movingVertexStart,float movingMaxLift) {
    public static NativeScene from(Scene scene) {
        BVHData bvh=BVHBuilder.build(scene.mesh);
        bvh.includeVerticalMotion(scene.movingVertexStart,scene.movingMaxLift);
        ByteBuffer[] b={allocate(scene.mesh.vertices.size(),16),allocate(bvh.triangles.length,16),allocate(scene.materials.size(),48),allocate(bvh.nodes.size(),48)};
        for(Vec3 v:scene.mesh.vertices) vector(b[0],v,0);
        for(Triangle t:bvh.triangles) b[1].putInt(t.a).putInt(t.b).putInt(t.c).putInt(t.material);
        for(Material m:scene.materials) { vector(b[2],m.baseColor,0);vector(b[2],m.emission,0);b[2].putFloat(m.roughness).putFloat(m.metallic).putFloat(m.texture).putFloat(m.textureScale); }
        for(BVHNode n:bvh.nodes) { vector(b[3],n.min,n.moving?1:0);vector(b[3],n.max,0);b[3].putInt(n.left).putInt(n.right).putInt(n.first).putInt(n.count); }
        for(ByteBuffer data:b) data.flip();
        return new NativeScene(b,scene.movingVertexStart,scene.movingMaxLift);
    }
    private static ByteBuffer allocate(int count,int stride) { return ByteBuffer.allocateDirect(Math.multiplyExact(count,stride)).order(ByteOrder.nativeOrder()); }
    private static void vector(ByteBuffer b,Vec3 v,float w) { b.putFloat(v.x).putFloat(v.y).putFloat(v.z).putFloat(w); }
}
