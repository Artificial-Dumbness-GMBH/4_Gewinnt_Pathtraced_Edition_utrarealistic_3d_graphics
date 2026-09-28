package de.viergewinnt.Game;

/** A move commits on landing, so turn/winner changes match the visible board. */
public final class DropAnimation {
    public static final float RADIUS=.48f, ROW_PITCH=2*RADIUS, FLOOR_Y=.05f, START_Y=6.85f;
    private static final double GRAVITY=18, BOUNCE_TIME=.16, BOUNCE_HEIGHT=.07;
    private int column=-1,row;
    private Player player;
    private double elapsed,fallTime;
    public static float cellY(int row) { return FLOOR_Y+RADIUS+(Board.ROWS-1-row)*ROW_PITCH; }
    public boolean active() { return column>=0; }
    public int column() { return column; }
    public int row() { return row; }
    public Player player() { return player; }
    public float maxLift() { return START_Y-cellY(row); }
    public float lift() {
        if(!active()) return 0;
        if(elapsed<fallTime) return (float)Math.max(0,maxLift()-.5*GRAVITY*elapsed*elapsed);
        double t=Math.min(1,(elapsed-fallTime)/BOUNCE_TIME);
        return (float)(4*BOUNCE_HEIGHT*t*(1-t));
    }
    public boolean start(Game game,int col) {
        if(active()||game.isGameOver()||col<0||col>=Board.COLUMNS) return false;
        int target=Board.ROWS-1;
        while(target>=0&&game.getBoard().getPiece(target,col)!=null) target--;
        if(target<0) return false;
        column=col;row=target;player=game.getCurrentPlayer();elapsed=0;
        fallTime=Math.sqrt(2*maxLift()/GRAVITY);return true;
    }
    /** Call only during active play; pause/minimize therefore freezes the motion. */
    public boolean advance(Game game,float seconds) {
        if(!active()) return false;
        if(!Float.isFinite(seconds)||seconds<0) throw new IllegalArgumentException("Invalid animation delta");
        elapsed+=seconds;
        if(elapsed<fallTime+BOUNCE_TIME) return false;
        int col=column;cancel();
        if(!game.play(col)) throw new IllegalStateException("Board changed during drop");
        return true;
    }
    public void cancel() { column=-1;player=null;elapsed=0; }
}
