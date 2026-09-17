package de.viergewinnt.Game;

/** Dependency-free game regressions, executed in Maven's test phase. */
public final class BoardTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Board b = new Board();
        check(!b.dropPiece(0, null), "null must not count as a move");
        check(!b.dropPiece(-1, Player.Red) && !b.dropPiece(7, Player.Red), "column bounds");
        check(b.getNextPlayer() == Player.Red && !b.isGameOver(), "initial state");
        for (int i = 0; i < Board.ROWS; i++) check(b.dropPiece(0, b.getNextPlayer()), "stacking");
        Player next = b.getNextPlayer();
        check(!b.dropPiece(0, next) && b.getNextPlayer() == next, "full column preserves turn");
        check(b.getPiece(5, 0) == Player.Red && b.getPiece(0, 0) == Player.Blue, "gravity");
        for (Player player : Player.values()) {
            b.reset();
            for (int c : new int[]{0, 1, 3, 2}) check(b.dropPiece(c, player), "horizontal move");
            won(b, player);
            b.reset();
            for (int i = 0; i < 4; i++) check(b.dropPiece(6, player), "vertical move");
            won(b, player);
            for (boolean mirrored : new boolean[]{false, true}) {
                b.reset();
                Player support = player == Player.Red ? Player.Blue : Player.Red;
                for (int i = 0; i < 4; i++) {
                    int column = mirrored ? 6 - i : i;
                    for (int j = 0; j < i; j++) check(b.dropPiece(column, support), "diagonal support");
                    check(b.dropPiece(column, player), "diagonal move");
                    if (i < 3) check(b.getWinner() == null, "three is not a win");
                }
                won(b, player);
            }
        }
        b.reset();
        // Alternating pairs shift every row: no horizontal, vertical or diagonal four.
        for (int row = 5; row >= 0; row--) for (int col = 0; col < 7; col++) {
            Player p = ((col / 2 + row) % 2 == 0) ? Player.Red : Player.Blue;
            check(b.dropPiece(col, p), "draw move");
        }
        check(b.isFull() && b.isGameOver() && b.getWinner() == null, "draw");
        b.reset();
        check(!b.isFull() && !b.isGameOver() && b.getNextPlayer() == Player.Red, "restart");
        for (int r = 0; r < 6; r++) for (int c = 0; c < 7; c++) check(b.getPiece(r, c) == null, "clear board");
        System.out.println("Board checks passed: invalid moves, gravity, turns, all win directions, draw and restart.");
    }
    private static void won(Board b, Player player) {
        check(b.getWinner() == player && b.isGameOver(), "winner");
        Player next = b.getNextPlayer();
        check(!b.dropPiece(5, next) && b.getNextPlayer() == next, "no moves after victory");
    }
}
