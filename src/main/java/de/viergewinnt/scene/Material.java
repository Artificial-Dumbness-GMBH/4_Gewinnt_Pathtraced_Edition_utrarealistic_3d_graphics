package de.viergewinnt.scene;

/** Linear-light metallic/roughness material. Texture 0=solid, 1=wood, 2=stone. */
public final class Material {
    public final Vec3 baseColor, emission;
    public final float roughness, metallic, textureScale;
    public final int texture;
    public Material(Vec3 baseColor, Vec3 emission) {
        this(baseColor,emission,.65f,0,0,1);
    }
    public Material(Vec3 baseColor,Vec3 emission,float roughness,float metallic,int texture,float textureScale) {
        if(baseColor==null||emission==null||!validColor(baseColor,1)||!validColor(emission,Float.MAX_VALUE))
            throw new IllegalArgumentException("Invalid linear material color");
        if(!Float.isFinite(roughness)||!Float.isFinite(metallic)||!Float.isFinite(textureScale)
                ||roughness<.08f||roughness>1||metallic<0||metallic>1||texture<0||texture>2||textureScale<=0)
            throw new IllegalArgumentException("Invalid PBR material");
        this.baseColor=baseColor;this.emission=emission;this.roughness=roughness;
        this.metallic=metallic;this.texture=texture;this.textureScale=textureScale;
    }
    private static boolean validColor(Vec3 v,float maximum) {
        return Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z)
            &&v.x>=0&&v.y>=0&&v.z>=0&&v.x<=maximum&&v.y<=maximum&&v.z<=maximum;
    }
    public static Material diffuse(float r,float g,float b) {
        return new Material(new Vec3(r,g,b),new Vec3(0,0,0));
    }
    public static Material pbr(float r,float g,float b,float roughness,float metallic,int texture) {
        return new Material(new Vec3(r,g,b),new Vec3(0,0,0),roughness,metallic,texture,1);
    }
}
