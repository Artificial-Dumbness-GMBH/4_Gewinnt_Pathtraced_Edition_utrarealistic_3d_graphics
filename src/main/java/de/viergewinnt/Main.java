package de.viergewinnt;

import de.viergewinnt.Game.Board;
import de.viergewinnt.Game.Player;
import de.viergewinnt.window.Window;

public class Main {

    public static void main(String[] args) {
        boolean consoleMode = args != null && args.length > 0 && "--console".equals(args[0]);

        if (consoleMode) {
            Board board = new Board();
            board.dropPiece(0, Player.Red);
            board.dropPiece(1, Player.Red);
            board.dropPiece(2, Player.Red);
            board.dropPiece(3, Player.Red);
            board.printBoard();

            if (board.hasWon(Player.Red)) {
                System.out.println("ROT GEWINNT!");
            }
            return;
        }

        new Window().create();
    }
}