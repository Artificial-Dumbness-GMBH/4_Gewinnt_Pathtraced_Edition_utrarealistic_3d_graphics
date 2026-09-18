package de.viergewinnt.scene;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwGetKey;

public final class Camera {
    private Vec3 position=new Vec3(5,3.8f,12);
    private float yaw=-112.62f,pitch=-3.52f;
    private double lastX,lastY;
    private boolean mouseInitialized;
    private Vec3 forward,right,up;
    private final double[] cursorX=new double[1],cursorY=new double[1];
    public Camera() { updateBasis(); }
    public Vec3 position() { return position; }
    public Vec3 forward() { return forward; }
    public Vec3 right() { return right; }
    public Vec3 up() { return up; }
    private void updateBasis() {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        forward=new Vec3((float)(Math.cos(y)*Math.cos(p)),(float)Math.sin(p),(float)(Math.sin(y)*Math.cos(p)));
        right=forward.cross(new Vec3(0,1,0)).normalized();up=right.cross(forward).normalized();
    }
    public void resetMouseCursor(double centerX,double centerY) {
        lastX=centerX;lastY=centerY;mouseInitialized=true;
    }
    /** Keep movement inside the room and outside the central table footprint. */
    public static boolean isWalkable(float x,float z) {
        return Math.abs(x)<Scene.ROOM_HALF_WIDTH-.7f&&Math.abs(z)<Scene.ROOM_HALF_DEPTH-.7f
            &&!(Math.abs(x)<6.5f&&Math.abs(z)<3.7f);
    }
    /** Mouse look is active whenever the cursor is captured; no right-click is required. */
    public boolean update(long window,float dt) {
        boolean changed=false;
        double[] x=cursorX,y=cursorY;glfwGetCursorPos(window,x,y);
        if(!mouseInitialized) resetMouseCursor(x[0],y[0]);
        if(x[0]!=lastX||y[0]!=lastY) {
            yaw+=(float)(x[0]-lastX)*.12f;pitch=Math.max(-89,Math.min(89,pitch-(float)(y[0]-lastY)*.12f));updateBasis();changed=true;
        }
        lastX=x[0];lastY=y[0];
        Vec3 horizontalForward=new Vec3(forward().x,0,forward().z).normalized();
        Vec3 horizontalRight=new Vec3(-horizontalForward.z,0,horizontalForward.x);
        Vec3 move=new Vec3(0,0,0);
        if(glfwGetKey(window,GLFW_KEY_W)==GLFW_PRESS) move=move.add(horizontalForward);
        if(glfwGetKey(window,GLFW_KEY_S)==GLFW_PRESS) move=move.sub(horizontalForward);
        if(glfwGetKey(window,GLFW_KEY_D)==GLFW_PRESS) move=move.add(horizontalRight);
        if(glfwGetKey(window,GLFW_KEY_A)==GLFW_PRESS) move=move.sub(horizontalRight);
        if(move.dot(move)>0) {
            Vec3 step=move.normalized().mul(5*Math.min(dt,.1f)),next=position;
            if(isWalkable(next.x+step.x,next.z)) next=new Vec3(next.x+step.x,next.y,next.z);
            if(isWalkable(next.x,next.z+step.z)) next=new Vec3(next.x,next.y,next.z+step.z);
            changed|=next.x!=position.x||next.z!=position.z;position=next;
        }
        return changed;
    }
}
