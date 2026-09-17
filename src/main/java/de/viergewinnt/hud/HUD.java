package de.viergewinnt.hud;

import de.viergewinnt.Game.Game;

public final class HUD {
    private HUD() {
    }

    public static String getStatusText(Game game) {
        if (game == null) {
            return "Spiel";
        }

        if (game.isGameOver()) {
            if (game.getWinner() == null) {
                return "Unentschieden";
            }
            return game.getWinner() + " gewinnt!";
        }

        return "Spieler: " + game.getCurrentPlayer();
    }
}
