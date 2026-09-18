package de.viergewinnt.scene;

import de.viergewinnt.Game.Board;
import de.viergewinnt.Game.Player;
import java.util.ArrayList;
import java.util.List;

/** CPU snapshot. Rebuild/upload only when geometry or materials change. */
public final class Scene {
    public final Mesh mesh=new Mesh();
    public final List<Material> materials=new ArrayList<>();
    // One downward-facing rectangle, shared with the shader's light sampler.
    public static final float ROOM_HALF_WIDTH=22,ROOM_HALF_DEPTH=26;
    public static final Vec3 LIGHT_POSITION=new Vec3(0,13.8f,0);
    public static final Vec3 LIGHT_SIZE=new Vec3(6,0,5);
    public static final Vec3 LIGHT_RADIANCE=new Vec3(18,17.2f,16);
    public static final int LIGHT_MATERIAL=4;
    public static Scene fromBoard(Board board) {
        Scene s=new Scene();
        s.materials.add(Material.pbr(.34f,.16f,.065f,.42f,0,1)); // walnut
        s.materials.add(Material.pbr(.055f,.085f,.12f,.27f,.72f,0)); // anodized frame
        s.materials.add(Material.pbr(.65f,.018f,.028f,.24f,0,0));
        s.materials.add(Material.pbr(.025f,.16f,.70f,.24f,0,0));
        s.materials.add(new Material(new Vec3(1,1,1),LIGHT_RADIANCE));
        s.materials.add(Material.pbr(.17f,.20f,.24f,.85f,0,2)); // stone floor
        s.materials.add(Material.pbr(.6f,.4f,.16f,.28f,.85f,0)); // brass trim
        s.materials.add(Material.pbr(.44f,.47f,.50f,.9f,0,0)); // studio wall
        s.materials.add(Material.pbr(.025f,.055f,.085f,.97f,0,0)); // central rug
        s.mesh.box(0,-.27f,0,6,.22f,3.2f,0);
        s.mesh.box(0,-2.1f,0,ROOM_HALF_WIDTH,.15f,ROOM_HALF_DEPTH,5);
        s.mesh.box(0,-1.91f,0,8,.035f,5.5f,8);
        for(int side=-1;side<=1;side+=2) {
            s.mesh.box(side*5,-1.15f,-2.3f,.16f,.75f,.16f,1);
            s.mesh.box(side*5,-1.15f,2.3f,.16f,.75f,.16f,1);
            s.mesh.box(side*3.68f,3,0,.12f,3.1f,.30f,1);
            s.mesh.box(side*3.68f,.08f,0,.35f,.12f,.95f,1);
            s.mesh.box(side*3.68f,3,.305f,.028f,3,.016f,6);
        }
        // Open grid keeps every legal cell visible from both sides.
        for(int c=0;c<=Board.COLUMNS;c++) s.mesh.box(c-3.5f,3,0,.045f,3.05f,.22f,1);
        for(int r=0;r<=Board.ROWS;r++) s.mesh.box(0,r,0,3.55f,.045f,.22f,1);
        s.mesh.box(0,6.12f,0,3.8f,.1f,.3f,0);
        s.mesh.box(0,6.23f,0,3.8f,.018f,.31f,6);
        // A 44 x 52 room around the origin; the board/table are centered at x=z=0.
        s.mesh.box(0,6,-ROOM_HALF_DEPTH,ROOM_HALF_WIDTH,8,.2f,7);
        s.mesh.box(0,6,ROOM_HALF_DEPTH,ROOM_HALF_WIDTH,8,.2f,7);
        s.mesh.box(-ROOM_HALF_WIDTH,6,0,.2f,8,ROOM_HALF_DEPTH,7);
        s.mesh.box(ROOM_HALF_WIDTH,6,0,.2f,8,ROOM_HALF_DEPTH,7);
        s.mesh.box(0,14.1f,0,ROOM_HALF_WIDTH,.1f,ROOM_HALF_DEPTH,7);
        for(int side=-1;side<=1;side+=2) {
            // Low walnut wainscoting and brass lines run around the room.
            s.mesh.box(0,-.5f,side*(ROOM_HALF_DEPTH-.23f),ROOM_HALF_WIDTH,1.3f,.04f,0);
            s.mesh.box(0,.84f,side*(ROOM_HALF_DEPTH-.29f),ROOM_HALF_WIDTH,.025f,.025f,6);
            s.mesh.box(side*(ROOM_HALF_WIDTH-.23f),-.5f,0,.04f,1.3f,ROOM_HALF_DEPTH,0);
            s.mesh.box(side*(ROOM_HALF_WIDTH-.29f),.84f,0,.025f,.025f,ROOM_HALF_DEPTH,6);
            for(int i=-5;i<=5;i++) s.mesh.box(i*4,6,side*(ROOM_HALF_DEPTH-.27f),.06f,7.8f,.05f,0);
            for(int i=-5;i<=5;i++) s.mesh.box(side*(ROOM_HALF_WIDTH-.27f),6,i*4.5f,.05f,7.8f,.06f,0);
        }
        // Recessed ceiling light centered above the game.
        // Emitting underside only: geometry and sampling use exactly the same area.
        int o=s.mesh.vertices.size();Vec3 p=LIGHT_POSITION,h=LIGHT_SIZE;
        s.mesh.vertices.add(new Vec3(p.x-h.x,p.y,p.z-h.z));
        s.mesh.vertices.add(new Vec3(p.x+h.x,p.y,p.z-h.z));
        s.mesh.vertices.add(new Vec3(p.x+h.x,p.y,p.z+h.z));
        s.mesh.vertices.add(new Vec3(p.x-h.x,p.y,p.z+h.z));
        s.mesh.triangles.add(new Triangle(o,o+1,o+2,LIGHT_MATERIAL));
        s.mesh.triangles.add(new Triangle(o,o+2,o+3,LIGHT_MATERIAL));
        s.mesh.box(p.x,p.y+.08f,p.z,h.x+.08f,.07f,h.z+.08f,1);
        for(int r=0;r<Board.ROWS;r++) for(int c=0;c<Board.COLUMNS;c++) {
            Player piece=board.getPiece(r,c);
            if(piece!=null) s.mesh.disc(c-3,Board.ROWS-r-.5f,0,.42f,.16f,piece==Player.Red?2:3);
        }
        return s;
    }
}
