package de.viergewinnt.Game;

public class Board {
    public static final int COLUMNS = 7;
    public static final int ROWS = 6;
    private final Player[][] board;

    public Board() {
        board = new Player[ROWS][COLUMNS];
    }

    public boolean dropPiece(int column, Player player) {
        if (column < 0 || column >= COLUMNS || player == null) {
            return false;
        }

        for (int row = ROWS - 1; row >= 0; row--) {
            if (board[row][column] == null) {
                board[row][column] = player;
                return true;
            }
        }

        return false;
    }

    public Player getPiece(int row, int column) {
        if (row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
            return null;
        }
        return board[row][column];
    }

    public boolean isFull() {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                if (board[row][column] == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean hasWon(Player player) {
        if (player == null) {
            return false;
        }

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column <= COLUMNS - 4; column++) {
                if (board[row][column] == player
                        && board[row][column + 1] == player
                        && board[row][column + 2] == player
                        && board[row][column + 3] == player) {
                    return true;
                }
            }
        }

        for (int row = 0; row <= ROWS - 4; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                if (board[row][column] == player
                        && board[row + 1][column] == player
                        && board[row + 2][column] == player
                        && board[row + 3][column] == player) {
                    return true;
                }
            }
        }

        for (int row = 0; row <= ROWS - 4; row++) {
            for (int column = 0; column <= COLUMNS - 4; column++) {
                if (board[row][column] == player
                        && board[row + 1][column + 1] == player
                        && board[row + 2][column + 2] == player
                        && board[row + 3][column + 3] == player) {
                    return true;
                }
            }
        }

        for (int row = 3; row < ROWS; row++) {
            for (int column = 0; column <= COLUMNS - 4; column++) {
                if (board[row][column] == player
                        && board[row - 1][column + 1] == player
                        && board[row - 2][column + 2] == player
                        && board[row - 3][column + 3] == player) {
                    return true;
                }
            }
        }

        return false;
    }

    public void printBoard() {
        System.out.println();
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                Player player = board[row][column];
                if (player == null) {
                    System.out.print("White ");
                } else {
                    switch (player) {
                        case Red:
                            System.out.print("Red ");
                            break;
                        case Blue:
                            System.out.print("Blue ");
                            break;
                        default:
                            System.out.print("White ");
                            break;
                    }
                }
            }
            System.out.println();
        }
        System.out.println("1 2 3 4 5 6 7");
    }
}