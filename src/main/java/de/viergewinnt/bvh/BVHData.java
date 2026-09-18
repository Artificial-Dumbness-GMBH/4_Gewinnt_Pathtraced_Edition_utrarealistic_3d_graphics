package de.viergewinnt.bvh;

import de.viergewinnt.scene.Triangle;
import java.util.List;

public final class BVHData {
    public final List<BVHNode> nodes;
    /** Leaf ranges refer to this reordered array, never the original mesh order. */
    public final Triangle[] triangles;
    /** Keep moving-only subtrees tight; sweep just mixed parents/leaves once. */
    public void includeVerticalMotion(int firstVertex,float lift) {
        if(firstVertex<0) return;
        for(int i=nodes.size()-1;i>=0;i--) {
            BVHNode n=nodes.get(i);
            if(n.count>0) {
                boolean any=false,all=true;
                for(int j=n.first;j<n.first+n.count;j++) {
                    Triangle t=triangles[j];
                    boolean moving=t.a>=firstVertex&&t.b>=firstVertex&&t.c>=firstVertex;
                    any|=moving;all&=moving;
                }
                n.moving=all;
                if(any&&!all) n.max=n.max.add(new de.viergewinnt.scene.Vec3(0,lift,0));
            } else {
                BVHNode left=nodes.get(n.left),right=nodes.get(n.right);
                n.moving=left.moving&&right.moving;
                if(!n.moving) {
                    for(BVHNode child:new BVHNode[]{left,right}) {
                        n.include(child);
                        if(child.moving) n.include(child.max.add(new de.viergewinnt.scene.Vec3(0,lift,0)));
                    }
                }
            }
        }
    }
    BVHData(List<BVHNode> nodes,Triangle[] triangles) { this.nodes=nodes;this.triangles=triangles; }
}
