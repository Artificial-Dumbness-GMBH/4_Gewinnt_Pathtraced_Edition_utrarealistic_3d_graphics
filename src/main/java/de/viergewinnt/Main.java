package de.viergewinnt;

import de.viergewinnt.Game.Board;
import de.viergewinnt.Game.Player;

public class Main {
    public static void main(String[] args) {

         Board board = new Board();
         if (java.util.Arrays.asList(args).contains("--console")) {
             board.dropPiece(0, Player.Red);
             board.dropPiece(0, Player.Blue);
             board.dropPiece(1, Player.Red);
             board.dropPiece(1, Player.Blue);
             board.dropPiece(2, Player.Red);

             board.printBoard();
         } else {
             new de.viergewinnt.window.Window().create(board);
         }
    }
}
