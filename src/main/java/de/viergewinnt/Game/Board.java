package de.viergewinnt.Game;
public class Board {
   public static final int COLUMNS = 7;
   public static final int ROWS = 6;
   private final Player[][] board;
   private Player winner;
   private Player nextPlayer = Player.Red;
   public Board() {
       board = new Player[ROWS][COLUMNS];
   }
   public boolean dropPiece(int column, Player player) {
       // Ungültige Spalte
       if (column < 0 || column >= COLUMNS || player == null || winner != null) {
           return false;
       }
       // Von unten nach oben suchen
       for (int row = ROWS - 1; row >= 0; row--) {
           if (board[row][column] == null) {
               board[row][column] = player;
               if (winsAt(row, column, player)) winner = player;
               nextPlayer = player == Player.Red ? Player.Blue : Player.Red;
               return true;
           }
       }
       // Spalte ist voll
       return false;
   }
   public Player getWinner() { return winner; }
   public Player getNextPlayer() { return nextPlayer; }
   public boolean isGameOver() { return winner != null || isFull(); }
   public void reset() {
       for (Player[] row : board) java.util.Arrays.fill(row, null);
       winner = null;
       nextPlayer = Player.Red;
   }
   private boolean winsAt(int row, int column, Player player) {
       int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
       for (int[] d : directions) {
           if (1 + count(row, column, d[0], d[1], player)
                 + count(row, column, -d[0], -d[1], player) >= 4) return true;
       }
       return false;
   }
   private int count(int row, int column, int dr, int dc, Player player) {
       int count = 0;
       for (int r = row + dr, c = column + dc; getPiece(r, c) == player; r += dr, c += dc) count++;
       return count;
   }
   public Player getPiece(int row, int column) {
       if (row < 0 || row >= ROWS ||
           column < 0 || column >= COLUMNS) {
           return null;
       }
       return board[row][column];
   }
   public boolean isFull() {
       for (int column = 0; column < COLUMNS; column++) {
           if (board[0][column] == null) {
               return false;
           }
       }
       return true;
   }
   public void printBoard() {
       System.out.println();
       for (int row = 0; row < ROWS; row++) {
           for (int column = 0; column < COLUMNS; column++) {
               Player player = board[row][column];
               if (player == Player.Red) {
                   System.out.print("Red ");
               } else if (player == Player.Blue) {
                   System.out.print("Blue ");
               } else {
                   System.out.print("White ");
               }
           }
           System.out.println();
       }
       System.out.println("1 2 3 4 5 6 7");
   }
}
