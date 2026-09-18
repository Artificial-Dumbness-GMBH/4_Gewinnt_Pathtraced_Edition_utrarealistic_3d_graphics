package de.viergewinnt.scene;

import java.util.ArrayList;
import java.util.List;

public final class Mesh {
    public final List<Vec3> vertices=new ArrayList<>();
    public final List<Triangle> triangles=new ArrayList<>();
    public void box(float x,float y,float z,float sx,float sy,float sz,int material) {
        int o=vertices.size();
        for(int i=0;i<8;i++) vertices.add(new Vec3(x+((i&1)==0?-sx:sx),y+((i&2)==0?-sy:sy),z+((i&4)==0?-sz:sz)));
        int[] indices={0,2,3,0,3,1,4,5,7,4,7,6,0,4,6,0,6,2,1,3,7,1,7,5,0,1,5,0,5,4,2,6,7,2,7,3};
        for(int i=0;i<indices.length;i+=3) triangles.add(new Triangle(o+indices[i],o+indices[i+1],o+indices[i+2],material));
    }
    /** Closed bevelled token along Z. Rings catch highlights on both faces. */
    public void disc(float x,float y,float z,float radius,float halfDepth,int material) {
        int o=vertices.size(),segments=64;
        float bevel=Math.min(.045f,Math.min(radius*.15f,halfDepth*.4f));
        float[] radii={radius-bevel,radius,radius,radius-bevel};
        float[] depths={-halfDepth,-halfDepth+bevel,halfDepth-bevel,halfDepth};
        vertices.add(new Vec3(x,y,z-halfDepth));vertices.add(new Vec3(x,y,z+halfDepth));
        for(int ring=0;ring<4;ring++) for(int i=0;i<segments;i++) {
            double a=2*Math.PI*i/segments;
            vertices.add(new Vec3(x+radii[ring]*(float)Math.cos(a),y+radii[ring]*(float)Math.sin(a),z+depths[ring]));
        }
        for(int i=0;i<segments;i++) {
            int next=(i+1)%segments;
            triangles.add(new Triangle(o,o+2+next,o+2+i,material));
            triangles.add(new Triangle(o+1,o+2+3*segments+i,o+2+3*segments+next,material));
            for(int ring=0;ring<3;ring++) {
                int a=o+2+ring*segments+i,b=o+2+ring*segments+next;
                triangles.add(new Triangle(a,b,b+segments,material));
                triangles.add(new Triangle(a,b+segments,a+segments,material));
            }
        }
    }
}
