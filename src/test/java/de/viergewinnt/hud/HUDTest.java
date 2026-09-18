package de.viergewinnt.hud;

import de.viergewinnt.Game.*;

public final class HUDTest {
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Game game=new Game();check(HUD.status(game)==HUD.Status.RED_TURN,"red starts");
        check(!game.play(-1)&&HUD.status(game)==HUD.Status.RED_TURN,"invalid move changed label");
        game.play(0);check(HUD.status(game)==HUD.Status.BLUE_TURN,"blue turn");
        game.reset();for(int column:new int[]{0,1,0,1,0,1,0}) game.play(column);
        check(HUD.status(game)==HUD.Status.RED_WON&&HUD.getStatusText(game).equals("ROT GEWINNT!"),"red winner label");
        check(!game.play(3)&&HUD.status(game)==HUD.Status.RED_WON,"winner disappeared");
        game.reset();check(HUD.status(game)==HUD.Status.RED_TURN,"restart label");
        for(int column:new int[]{0,1,0,1,2,1,2,1}) game.play(column);
        check(HUD.status(game)==HUD.Status.BLUE_WON,"blue winner label");
        Board full=new Board();
        for(int r=Board.ROWS-1;r>=0;r--) for(int c=0;c<Board.COLUMNS;c++) full.dropPiece(c,(r+c/2)%2==0?Player.Red:Player.Blue);
        check(!full.hasWon(Player.Red)&&!full.hasWon(Player.Blue)&&full.isFull(),"invalid draw fixture");
        check(HUD.status(new Game(full))==HUD.Status.DRAW,"draw label");
        check(HUD.status(null)==HUD.Status.READY,"missing game");
        check(HologramRenderer.ANCHOR.y-1.875f/2>6.25f,"hologram overlaps board roof");
        System.out.println("HUD checks passed: turns, invalid moves, both winners, draw, restart and board clearance.");
    }
}
