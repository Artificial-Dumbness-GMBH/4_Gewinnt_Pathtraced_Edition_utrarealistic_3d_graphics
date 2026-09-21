package de.viergewinnt.bvh;

import de.viergewinnt.scene.Vec3;

/** std430: vec4(min.xyz,moving), vec4 max, ivec4(left,right,first,count), 48 bytes. */
public final class BVHNode {
    public Vec3 min=new Vec3(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY);
    public Vec3 max=new Vec3(Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY);
    public boolean moving;
    public int left=-1,right=-1,first,count;
    void include(Vec3 v) {
        min=new Vec3(Math.min(min.x,v.x),Math.min(min.y,v.y),Math.min(min.z,v.z));
        max=new Vec3(Math.max(max.x,v.x),Math.max(max.y,v.y),Math.max(max.z,v.z));
    }
    void include(BVHNode n) { if(n.min.x<=n.max.x) { include(n.min);include(n.max); } }
    float area() { Vec3 d=max.sub(min); return min.x>max.x?0:2*(d.x*d.y+d.y*d.z+d.z*d.x); }
}
