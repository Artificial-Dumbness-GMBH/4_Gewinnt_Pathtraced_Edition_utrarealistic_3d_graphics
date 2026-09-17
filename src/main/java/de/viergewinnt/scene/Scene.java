package de.viergewinnt.scene;

import de.viergewinnt.Game.Board;
import de.viergewinnt.Game.Player;
import java.util.ArrayList;
import java.util.List;

/** CPU snapshot. Rebuild/upload only when geometry or materials change. */
public final class Scene {
    public final Mesh mesh=new Mesh();
    public final List<Material> materials=new ArrayList<>();
    public static Scene fromBoard(Board board) {
        Scene s=new Scene();
        s.materials.add(Material.diffuse(.55f,.58f,.62f));
        s.materials.add(Material.diffuse(.035f,.12f,.65f));
        s.materials.add(Material.diffuse(.8f,.025f,.025f));
        s.materials.add(Material.diffuse(.04f,.3f,.85f));
        s.materials.add(new Material(new Vec3(1,1,1),new Vec3(6,5.5f,4.5f)));
        s.mesh.box(0,-.25f,0,12,.2f,12,0);
        // Open grid: pieces stay visible from both sides.
        for(int c=0;c<=Board.COLUMNS;c++) s.mesh.box(c-3.5f,3,0,.06f,3.1f,.22f,1);
        for(int r=0;r<=Board.ROWS;r++) s.mesh.box(0,r,0,3.56f,.06f,.22f,1);
        s.mesh.box(0,8,0,2,.05f,1.5f,4);
        for(int r=0;r<Board.ROWS;r++) for(int c=0;c<Board.COLUMNS;c++) {
            Player p=board.getPiece(r,c);
            if(p!=null) s.mesh.disc(c-3,Board.ROWS-r-.5f,0,.42f,.16f,p==Player.Red?2:3);
        }
        return s;
    }
}
