package de.viergewinnt.bvh;

import de.viergewinnt.scene.*;
import java.util.ArrayList;
import java.util.List;

/** Binary, 12-bin SAH. Depth cap guarantees the shader's 32-entry stack is sufficient. */
public final class BVHBuilder {
    private static final int BINS=12, LEAF_SIZE=4, MAX_DEPTH=30;
    private final Mesh mesh;
    private final Triangle[] triangles;
    private final List<BVHNode> nodes=new ArrayList<>();
    private BVHBuilder(Mesh mesh) { this.mesh=mesh;triangles=mesh.triangles.toArray(new Triangle[0]); }
    public static BVHData build(Mesh mesh) {
        if(mesh.triangles.isEmpty()) throw new IllegalArgumentException("Scene must contain triangles");
        BVHBuilder b=new BVHBuilder(mesh); b.buildNode(0,b.triangles.length,0);
        return new BVHData(b.nodes,b.triangles);
    }
    private Vec3 centroid(Triangle t) { return mesh.vertices.get(t.a).add(mesh.vertices.get(t.b)).add(mesh.vertices.get(t.c)).mul(1f/3); }
    private void bounds(BVHNode n,Triangle t) { n.include(mesh.vertices.get(t.a));n.include(mesh.vertices.get(t.b));n.include(mesh.vertices.get(t.c)); }
    private int bin(Triangle t,int axis,float lo,float extent) { return Math.min(BINS-1,Math.max(0,(int)((centroid(t).axis(axis)-lo)/extent*BINS))); }
    private int buildNode(int first,int count,int depth) {
        int index=nodes.size(); BVHNode n=new BVHNode(); nodes.add(n); n.first=first;n.count=count;
        BVHNode centers=new BVHNode();
        for(int i=first;i<first+count;i++) { bounds(n,triangles[i]);centers.include(centroid(triangles[i])); }
        if(count<=LEAF_SIZE||depth>=MAX_DEPTH||n.area()<=0) return index;
        float bestCost=count*n.area(),bestLo=0,bestExtent=0;int bestAxis=-1,bestSplit=-1;
        for(int axis=0;axis<3;axis++) {
            float lo=centers.min.axis(axis),extent=centers.max.axis(axis)-lo;
            if(extent<1e-6f) continue;
            BVHNode[] bins=new BVHNode[BINS];int[] counts=new int[BINS];
            for(int i=0;i<BINS;i++) bins[i]=new BVHNode();
            for(int i=first;i<first+count;i++) { int j=bin(triangles[i],axis,lo,extent); counts[j]++;bounds(bins[j],triangles[i]); }
            for(int split=0;split<BINS-1;split++) {
                BVHNode l=new BVHNode(),r=new BVHNode();int lc=0,rc=0;
                for(int j=0;j<=split;j++) { l.include(bins[j]);lc+=counts[j]; }
                for(int j=split+1;j<BINS;j++) { r.include(bins[j]);rc+=counts[j]; }
                if(lc==0||rc==0) continue;
                float cost=n.area()+lc*l.area()+rc*r.area();
                if(cost<bestCost) { bestCost=cost;bestAxis=axis;bestSplit=split;bestLo=lo;bestExtent=extent; }
            }
        }
        if(bestAxis<0) return index;
        int mid=first;
        for(int i=first;i<first+count;i++) if(bin(triangles[i],bestAxis,bestLo,bestExtent)<=bestSplit) {
            Triangle tmp=triangles[mid];triangles[mid++]=triangles[i];triangles[i]=tmp;
        }
        if(mid==first||mid==first+count) return index;
        n.count=0;n.left=buildNode(first,mid-first,depth+1);n.right=buildNode(mid,first+count-mid,depth+1);
        return index;
    }
}
