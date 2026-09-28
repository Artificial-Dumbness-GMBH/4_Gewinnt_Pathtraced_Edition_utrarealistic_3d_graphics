package de.viergewinnt.scene;

import de.viergewinnt.Game.*;
import de.viergewinnt.bvh.*;

public final class DropRegressionTest {
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void run() {
        Game game=new Game();DropAnimation drop=new DropAnimation();
        check(!drop.start(game,-1)&&!drop.start(game,7),"invalid column");
        check(drop.start(game,3)&&!drop.start(game,2),"only one falling coin");
        check(game.getBoard().getPiece(5,3)==null&&game.getCurrentPlayer()==Player.Red,"premature move");
        float start=drop.lift();drop.advance(game,.2f);
        check(drop.lift()<start&&drop.lift()>0,"gravity");
        float frozen=drop.lift();check(drop.lift()==frozen,"motion must require explicit time");
        for(int i=0;i<200&&drop.active();i++) {
            check(drop.lift()>=0&&drop.lift()<=drop.maxLift(),"penetration/invalid bounds");drop.advance(game,.01f);
        }
        check(!drop.active()&&game.getBoard().getPiece(5,3)==Player.Red&&game.getCurrentPlayer()==Player.Blue,"landing commits once");
        check(!drop.advance(game,5),"duplicate landing");
        for(int i=0;i<5;i++) { check(drop.start(game,3),"stack entry");drop.advance(game,5); }
        check(!drop.start(game,3),"full column");
        game.reset();drop.start(game,1);drop.cancel();game.reset();
        check(!drop.advance(game,5)&&game.getBoard().getPiece(5,1)==null,"restart during fall");
        for(int col:new int[]{0,1,0,1,0,1}) game.play(col);
        drop.start(game,0);check(!game.isGameOver(),"winner before landing");drop.advance(game,5);
        check(game.getWinner()==Player.Red&&!drop.start(game,2),"winner after landing");
        check(Math.abs(DropAnimation.cellY(4)-DropAnimation.cellY(5)-2*DropAnimation.RADIUS)<1e-6,"stack must touch");
        check(Math.abs(DropAnimation.cellY(5)-DropAnimation.RADIUS-DropAnimation.FLOOR_Y)<1e-6,"floor contact");

        Scene empty=Scene.fromBoard(new Board());
        for(int col=0;col<Board.COLUMNS;col++) {
            // Sweep the full rectangular envelope of the moving token through every shaft.
            for(float x:new float[]{-.479f,-.3f,0,.3f,.479f}) for(float z:new float[]{-.16f,0,.16f}) {
                float d=nearest(empty.mesh,new Vec3(col-3+x,7.5f,z),new Vec3(0,-1,0));
                check(Math.abs(d-(7.5f-DropAnimation.FLOOR_Y))<1e-4,"blocked shaft "+col+": "+d);
            }
            for(int row=0;row<Board.ROWS;row++) {
                float d=nearest(empty.mesh,new Vec3(col-3,DropAnimation.cellY(row),1),new Vec3(0,0,-1));
                check(d>2,"fake/filled circular hole");
                d=nearest(empty.mesh,new Vec3(col-3+.46f,DropAnimation.cellY(row),1),new Vec3(0,0,-1));
                check(d<1,"missing retaining plate");
            }
        }
        game=new Game();drop=new DropAnimation();drop.start(game,3);
        Scene moving=Scene.fromBoard(game.getBoard(),drop);BVHData bvh=BVHBuilder.build(moving.mesh);
        bvh.includeVerticalMotion(moving.movingVertexStart,moving.movingMaxLift);
        for(BVHNode n:bvh.nodes) {
            if(n.count==0) {
                for(int c:new int[]{n.left,n.right}) {
                    BVHNode child=bvh.nodes.get(c);
                    float childMax=child.max.y+(child.moving?moving.movingMaxLift:0);
                    check(childMax<=n.max.y+(n.moving?moving.movingMaxLift:0)+1e-6,"swept parent bounds");
                }
            } else for(int j=n.first;j<n.first+n.count;j++) {
                Triangle t=bvh.triangles[j];
                for(int v:new int[]{t.a,t.b,t.c}) {
                    Vec3 p=moving.mesh.vertices.get(v);float lift=v>=moving.movingVertexStart?moving.movingMaxLift:0;
                    check(p.y>=n.min.y-1e-6&&p.y+lift<=n.max.y+(n.moving?moving.movingMaxLift:0)+1e-6,"swept leaf bounds");
                }
            }
        }
        System.out.println("Drop checks passed: gravity, input lock, delayed win/turn, restart, full columns, touching stack, 7 clear shafts, 42 real holes and swept BVH bounds.");
    }
    private static float nearest(Mesh m,Vec3 origin,Vec3 direction) {
        float nearest=Float.POSITIVE_INFINITY;
        for(Triangle t:m.triangles) {
            Vec3 a=m.vertices.get(t.a),e1=m.vertices.get(t.b).sub(a),e2=m.vertices.get(t.c).sub(a),p=direction.cross(e2);
            float det=e1.dot(p);if(Math.abs(det)<1e-8f) continue;
            Vec3 s=origin.sub(a);float u=s.dot(p)/det;if(u<0||u>1) continue;
            Vec3 q=s.cross(e1);float v=direction.dot(q)/det;if(v<0||u+v>1) continue;
            float d=e2.dot(q)/det;if(d>.0001f) nearest=Math.min(nearest,d);
        }
        return nearest;
    }
}
