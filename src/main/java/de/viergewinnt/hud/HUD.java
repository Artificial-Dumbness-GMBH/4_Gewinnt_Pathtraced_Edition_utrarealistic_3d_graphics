package de.viergewinnt.hud;

import de.viergewinnt.Game.Game;
import de.viergewinnt.Game.Player;

/** One source of truth for the hologram, pause menu and window title. */
public final class HUD {
    private HUD() {}
    public enum Status {
        READY("SPIEL BEREIT","Tasten 1–7 wählen eine Spalte",.25f,.85f,1),
        RED_TURN("ROT IST AM ZUG","Spalte mit 1–7 wählen",1,.24f,.29f),
        BLUE_TURN("BLAU IST AM ZUG","Spalte mit 1–7 wählen",.22f,.60f,1),
        RED_WON("ROT GEWINNT!","ESC öffnen · Neues Spiel starten",1,.24f,.29f),
        BLUE_WON("BLAU GEWINNT!","ESC öffnen · Neues Spiel starten",.22f,.60f,1),
        DRAW("UNENTSCHIEDEN","ESC öffnen · Neues Spiel starten",.3f,.95f,.87f);
        public final String title,detail;
        public final float red,green,blue;
        Status(String title,String detail,float red,float green,float blue) {
            this.title=title;this.detail=detail;this.red=red;this.green=green;this.blue=blue;
        }
    }
    public static Status status(Game game) {
        if(game==null) return Status.READY;
        if(game.isGameOver()) return game.getWinner()==null?Status.DRAW:game.getWinner()==Player.Red?Status.RED_WON:Status.BLUE_WON;
        return game.getCurrentPlayer()==Player.Red?Status.RED_TURN:Status.BLUE_TURN;
    }
    public static String getStatusText(Game game) { return status(game).title; }
}
