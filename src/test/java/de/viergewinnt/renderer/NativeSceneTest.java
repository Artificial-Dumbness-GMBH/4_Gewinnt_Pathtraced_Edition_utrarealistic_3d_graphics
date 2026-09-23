package de.viergewinnt.renderer;

import de.viergewinnt.Game.*;
import de.viergewinnt.bvh.*;
import de.viergewinnt.scene.*;
import java.nio.*;

/** ABI checks run without Windows, a native library, or a GPU. */
public final class NativeSceneTest {
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Game game=new Game(new Board());DropAnimation drop=new DropAnimation();
        check(drop.start(game,3),"start moving coin");
        for(Scene scene:new Scene[]{Scene.fromBoard(game.getBoard()),Scene.fromBoard(game.getBoard(),drop)}) {
            NativeScene packed=NativeScene.from(scene);
            BVHData bvh=BVHBuilder.build(scene.mesh);bvh.includeVerticalMotion(scene.movingVertexStart,scene.movingMaxLift);
            ByteBuffer[] buffers=packed.buffers();
            int[] capacities={scene.mesh.vertices.size()*16,bvh.triangles.length*16,scene.materials.size()*48,bvh.nodes.size()*48};
            for(int i=0;i<4;i++) check(buffers[i].isDirect()&&buffers[i].position()==0&&buffers[i].remaining()==capacities[i]&&buffers[i].order()==ByteOrder.nativeOrder(),"direct buffer ABI "+i);
            for(int i=0;i<scene.mesh.vertices.size();i++) {
                Vec3 v=scene.mesh.vertices.get(i);
                check(buffers[0].getFloat(i*16)==v.x&&buffers[0].getFloat(i*16+4)==v.y&&buffers[0].getFloat(i*16+8)==v.z,"vertex stride");
            }
            for(int i=0;i<bvh.triangles.length;i++) {
                Triangle t=bvh.triangles[i];
                check(buffers[1].getInt(i*16)==t.a&&buffers[1].getInt(i*16+4)==t.b
                    &&buffers[1].getInt(i*16+8)==t.c&&buffers[1].getInt(i*16+12)==t.material,"DXR primitive IDs match BVH order");
            }
            for(int i=0;i<bvh.nodes.size();i++) {
                BVHNode n=bvh.nodes.get(i);int offset=i*48;
                check(buffers[3].getFloat(offset+12)==(n.moving?1:0)&&buffers[3].getFloat(offset+20)==n.max.y,"swept motion bounds");
                check(buffers[3].getInt(offset+32)==n.left&&buffers[3].getInt(offset+36)==n.right
                    &&buffers[3].getInt(offset+40)==n.first&&buffers[3].getInt(offset+44)==n.count,"node layout");
            }
            check(packed.movingVertexStart()==scene.movingVertexStart&&packed.movingMaxLift()==scene.movingMaxLift,"motion metadata");
        }
        System.out.println("Native scene ABI passed: direct buffers, strides, BVH/DXR primitive order and moving bounds.");
    }
}
