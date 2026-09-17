package de.viergewinnt.bvh;

import de.viergewinnt.scene.Triangle;
import java.util.List;

public final class BVHData {
    public final List<BVHNode> nodes;
    /** Leaf ranges refer to this reordered array, never the original mesh order. */
    public final Triangle[] triangles;
    BVHData(List<BVHNode> nodes,Triangle[] triangles) { this.nodes=nodes;this.triangles=triangles; }
}
