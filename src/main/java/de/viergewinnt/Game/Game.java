package de.viergewinnt.Game;

public class Game {

    private Board board;
    private Player currentPlayer;
    private Player winner;
    private boolean gameOver;

    public Game() {
        board = new Board();
        currentPlayer = Player.Red;
        winner = null;
        gameOver = false;
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