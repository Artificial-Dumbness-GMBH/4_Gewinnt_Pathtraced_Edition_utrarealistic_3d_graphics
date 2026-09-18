package de.viergewinnt.scene;

import de.viergewinnt.Game.*;
import java.util.HashMap;
import java.util.Map;

/** Dependency-free regressions, run alongside BVHTest by Maven. */
public final class SceneRegressionTest {
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        DropRegressionTest.run();
        Board board=new Board();board.dropPiece(2,Player.Red);
        Game game=new Game(board);
        check(game.getBoard()==board,"supplied board discarded");
        check(game.getCurrentPlayer()==Player.Blue,"turn after supplied board");
        check(!game.play(-1)&&game.getCurrentPlayer()==Player.Blue,"invalid move consumed turn");
        for(int i=0;i<3;i++) board.dropPiece(2,Player.Red);
        game=new Game(board);
        check(game.isGameOver()&&game.getWinner()==Player.Red&&!game.play(0),"supplied winner");
        game.reset();check(!game.isGameOver()&&game.getWinner()==null&&game.getCurrentPlayer()==Player.Red,"restart");
        int[] moves={0,1,0,1,0,1,0};
        for(int move:moves) check(game.play(move),"legal move");
        check(game.getWinner()==Player.Red&&!game.play(3),"win stops input");

        Mesh disc=new Mesh();disc.disc(0,0,0,.42f,.16f,0);
        Map<Long,Integer> edges=new HashMap<>();
        for(Triangle t:disc.triangles) {
            Vec3 a=disc.vertices.get(t.a),b=disc.vertices.get(t.b),c=disc.vertices.get(t.c);
            Vec3 n=b.sub(a).cross(c.sub(a));
            check(n.dot(n)>1e-12,"degenerate bevel triangle");
            check(n.dot(a.add(b).add(c))>0,"inward token face");
            int[] indices={t.a,t.b,t.c};
            for(int i=0;i<3;i++) {
                int u=indices[i],v=indices[(i+1)%3];
                long key=((long)Math.min(u,v)<<32)|Math.max(u,v);
                edges.merge(key,1,Integer::sum);
            }
        }
        check(edges.values().stream().allMatch(count->count==2),"token mesh is not closed");
        Scene scene=Scene.fromBoard(board);
        for(Triangle t:scene.mesh.triangles) {
            check(t.material>=0&&t.material<scene.materials.size(),"material index");
            check(t.a>=0&&t.a<scene.mesh.vertices.size()&&t.b>=0&&t.b<scene.mesh.vertices.size()
                &&t.c>=0&&t.c<scene.mesh.vertices.size(),"vertex index");
        }
        double lightArea=0;
        for(Triangle t:scene.mesh.triangles) if(t.material==Scene.LIGHT_MATERIAL) {
            Vec3 a=scene.mesh.vertices.get(t.a),b=scene.mesh.vertices.get(t.b),c=scene.mesh.vertices.get(t.c);
            Vec3 n=b.sub(a).cross(c.sub(a));
            check(n.y<0,"emitter must face down");lightArea+=Math.sqrt(n.dot(n))*.5;
        }
        check(Math.abs(lightArea-4*Scene.LIGHT_SIZE.x*Scene.LIGHT_SIZE.z)<1e-5,"light sampler area differs from mesh");
        System.out.println("Scene/game checks passed: supplied board, turns, wins, reset, closed bevel mesh, indices and light area.");
    }
}
