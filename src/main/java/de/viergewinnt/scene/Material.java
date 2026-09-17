package de.viergewinnt.scene;

/** Initial core supports Lambert diffuse surfaces and emission. */
public final class Material {
    public final Vec3 baseColor, emission;
    public Material(Vec3 baseColor, Vec3 emission) {
        this.baseColor=baseColor; this.emission=emission;
    }
    public static Material diffuse(float r,float g,float b) {
        return new Material(new Vec3(r,g,b),new Vec3(0,0,0));
    }
}
