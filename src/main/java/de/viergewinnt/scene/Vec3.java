package de.viergewinnt.scene;

/** Immutable CPU vector; geometry does not depend on an OpenGL context. */
public final class Vec3 {
    public final float x, y, z;
    public Vec3(float x, float y, float z) { this.x=x; this.y=y; this.z=z; }
    public Vec3 add(Vec3 v) { return new Vec3(x+v.x,y+v.y,z+v.z); }
    public Vec3 sub(Vec3 v) { return new Vec3(x-v.x,y-v.y,z-v.z); }
    public Vec3 mul(float s) { return new Vec3(x*s,y*s,z*s); }
    public float dot(Vec3 v) { return x*v.x+y*v.y+z*v.z; }
    public Vec3 cross(Vec3 v) { return new Vec3(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x); }
    public Vec3 normalized() { float n=(float)Math.sqrt(dot(this)); return n>0?mul(1/n):new Vec3(0,0,0); }
    public float axis(int a) { return a==0?x:a==1?y:z; }
}
