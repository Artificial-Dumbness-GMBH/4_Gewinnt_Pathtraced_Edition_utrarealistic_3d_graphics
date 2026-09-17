package de.viergewinnt.bvh;

import de.viergewinnt.Game.*;
import de.viergewinnt.scene.*;
import java.util.*;

/** Dependency-free regression checks, also executed by Maven in the test phase. */
public final class BVHTest {
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        Board board=new Board();check(board.dropPiece(0,Player.Red),"drop");check(board.getPiece(5,0)==Player.Red,"board state");
        check(!board.dropPiece(-1,Player.Blue),"invalid column");
        Scene scene=Scene.fromBoard(board);verify(scene.mesh);
        Mesh mesh=new Mesh();Random random=new Random(17);
        for(int i=0;i<1000;i++) mesh.box(random.nextFloat()*20-10,random.nextFloat()*20-10,random.nextFloat()*20-10,.1f,.2f,.3f,0);
        verify(mesh);
        Mesh degenerate=new Mesh();degenerate.vertices.add(new Vec3(0,0,0));
        for(int i=0;i<100;i++) degenerate.triangles.add(new Triangle(0,0,0,0));
        verify(degenerate);
        try { BVHBuilder.build(new Mesh());throw new AssertionError("empty scene must fail"); } catch(IllegalArgumentException expected) { }
        System.out.println("BVH checks passed: leaf coverage, bounds, depth, degenerate geometry and 6000 BVH/brute-force ray comparisons.");
    }
    private static void verify(Mesh mesh) {
        BVHData data=BVHBuilder.build(mesh);
        boolean[] covered=new boolean[data.triangles.length];validate(data,mesh,0,0,covered);
        for(boolean b:covered) check(b,"missing triangle");
        Set<Triangle> original=Collections.newSetFromMap(new IdentityHashMap<Triangle,Boolean>());original.addAll(mesh.triangles);
        Set<Triangle> reordered=Collections.newSetFromMap(new IdentityHashMap<Triangle,Boolean>());reordered.addAll(Arrays.asList(data.triangles));
        check(original.equals(reordered),"permutation");
        Random random=new Random(42);
        for(int i=0;i<2000;i++) {
            Vec3 origin=new Vec3(random.nextFloat()*30-15,random.nextFloat()*30-15,random.nextFloat()*30-15);
            Vec3 dir=i%3==0?new Vec3(0,0,-1):new Vec3(random.nextFloat()-.5f,random.nextFloat()-.5f,random.nextFloat()-.5f).normalized();
            float brute=Float.POSITIVE_INFINITY;
            for(Triangle t:mesh.triangles) brute=Math.min(brute,hit(mesh,t,origin,dir));
            float accelerated=traverse(data,mesh,origin,dir);
            check(brute==accelerated||Math.abs(brute-accelerated)<1e-4,"ray mismatch "+i+": "+brute+" / "+accelerated);
        }
    }
    private static void validate(BVHData b,Mesh m,int i,int depth,boolean[] covered) {
        check(depth<=30,"stack depth");BVHNode n=b.nodes.get(i);
        if(n.count>0) {
            for(int j=n.first;j<n.first+n.count;j++) {
                check(!covered[j],"duplicate leaf coverage");covered[j]=true;Triangle t=b.triangles[j];
                for(int v:new int[]{t.a,t.b,t.c}) for(int axis=0;axis<3;axis++) {
                    float p=m.vertices.get(v).axis(axis);check(p>=n.min.axis(axis)&&p<=n.max.axis(axis),"leaf bounds");
                }
            }
        } else {
            for(int child:new int[]{n.left,n.right}) {
                check(child>i&&child<b.nodes.size(),"invalid child");BVHNode c=b.nodes.get(child);
                for(int a=0;a<3;a++) check(c.min.axis(a)>=n.min.axis(a)&&c.max.axis(a)<=n.max.axis(a),"parent bounds");
                validate(b,m,child,depth+1,covered);
            }
        }
    }
    private static float traverse(BVHData b,Mesh m,Vec3 o,Vec3 d) {
        int[] stack=new int[32];int count=1;float closest=Float.POSITIVE_INFINITY;
        while(count>0) {
            BVHNode n=b.nodes.get(stack[--count]);float lo=.0001f,hi=closest;boolean intersects=true;
            for(int a=0;a<3;a++) {
                if(Math.abs(d.axis(a))<1e-8f) { if(o.axis(a)<n.min.axis(a)||o.axis(a)>n.max.axis(a)) intersects=false; }
                else { float x=(n.min.axis(a)-o.axis(a))/d.axis(a),y=(n.max.axis(a)-o.axis(a))/d.axis(a);lo=Math.max(lo,Math.min(x,y));hi=Math.min(hi,Math.max(x,y)); }
            }
            if(!intersects||hi<lo) continue;
            if(n.count>0) for(int j=n.first;j<n.first+n.count;j++) closest=Math.min(closest,hit(m,b.triangles[j],o,d));
            else { check(count+2<=32,"stack overflow");stack[count++]=n.right;stack[count++]=n.left; }
        }
        return closest;
    }
    private static float hit(Mesh m,Triangle t,Vec3 o,Vec3 d) {
        Vec3 a=m.vertices.get(t.a),e1=m.vertices.get(t.b).sub(a),e2=m.vertices.get(t.c).sub(a),p=d.cross(e2);
        float det=e1.dot(p);if(Math.abs(det)<1e-8f) return Float.POSITIVE_INFINITY;
        Vec3 s=o.sub(a);float u=s.dot(p)/det;if(u<0||u>1) return Float.POSITIVE_INFINITY;
        Vec3 q=s.cross(e1);float v=d.dot(q)/det;if(v<0||u+v>1) return Float.POSITIVE_INFINITY;
        float distance=e2.dot(q)/det;return distance>.0001f?distance:Float.POSITIVE_INFINITY;
    }
}
