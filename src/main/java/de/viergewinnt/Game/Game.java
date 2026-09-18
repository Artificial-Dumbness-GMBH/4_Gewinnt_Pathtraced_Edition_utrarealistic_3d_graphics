package de.viergewinnt.Game;

public class Game {

    private Board board;
    private Player currentPlayer;
    private Player winner;
    private boolean gameOver;

    public Game() { this(new Board()); }

    public Game(Board initialBoard) {
        if(initialBoard==null) throw new IllegalArgumentException("Board is required");
        board=initialBoard;
        int red=0,blue=0;
        for(int r=0;r<Board.ROWS;r++) for(int c=0;c<Board.COLUMNS;c++) {
            if(board.getPiece(r,c)==Player.Red) red++;
            if(board.getPiece(r,c)==Player.Blue) blue++;
        }
        currentPlayer=red<=blue?Player.Red:Player.Blue;
        winner=board.hasWon(Player.Red)?Player.Red:board.hasWon(Player.Blue)?Player.Blue:null;
        gameOver=winner!=null||board.isFull();
    }

    public boolean play(int column) {
        if (gameOver) {
            return false;
        }

        if (!board.dropPiece(column, currentPlayer)) {
            return false;
        }

        if (board.hasWon(currentPlayer)) {
            winner = currentPlayer;
            gameOver = true;
            System.out.println(currentPlayer + " GEWINNT!");
            return true;
        }

        if (board.isFull()) {
            winner = null;
            gameOver = true;
            System.out.println("UNENTSCHIEDEN!");
            return true;
        }

        currentPlayer = (currentPlayer == Player.Red) ? Player.Blue : Player.Red;
        return true;
    }

    public Board getBoard() {
        return board;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public Player getWinner() {
        return winner;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void reset() {
        board = new Board();
        currentPlayer = Player.Red;
        winner = null;
        gameOver = false;
    }
}