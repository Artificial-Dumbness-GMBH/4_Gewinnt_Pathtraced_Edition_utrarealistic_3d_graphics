package de.viergewinnt.Game;
public class Board {
   public static final int COLUMNS = 7;
   public static final int ROWS = 6;
   private final Player[][] board;
   public Board() {
       board = new Player[ROWS][COLUMNS];
   }
   public boolean dropPiece(int column, Player player) {
       // Ungültige Spalte
       if (column < 0 || column >= COLUMNS) {
           return false;
       }
       // Von unten nach oben suchen
       for (int row = ROWS - 1; row >= 0; row--) {
           if (board[row][column] == null) {
               board[row][column] = player;
               return true;
           }
       }
       // Spalte ist voll
       return false;
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