package de.viergewinnt.scene;

import static org.lwjgl.glfw.GLFW.*;

public final class Camera {
    private Vec3 position=new Vec3(0,3,12);
    private float yaw=-90,pitch=0;
    private double lastX,lastY;
    private boolean tracking;
    public Vec3 position() { return position; }
    public Vec3 forward() {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        return new Vec3((float)(Math.cos(y)*Math.cos(p)),(float)Math.sin(p),(float)(Math.sin(y)*Math.cos(p)));
    }
    public Vec3 right() { return forward().cross(new Vec3(0,1,0)).normalized(); }
    public Vec3 up() { return right().cross(forward()).normalized(); }
    /** Mouse look while right button is held; WASD movement is frame-rate independent. */
    public boolean update(long window,float dt) {
        boolean changed=false;
        double[] x=new double[1],y=new double[1];glfwGetCursorPos(window,x,y);
        boolean looking=glfwGetMouseButton(window,GLFW_MOUSE_BUTTON_RIGHT)==GLFW_PRESS;
        if(looking&&tracking&&(x[0]!=lastX||y[0]!=lastY)) {
            yaw+=(float)(x[0]-lastX)*.12f;pitch=Math.max(-89,Math.min(89,pitch-(float)(y[0]-lastY)*.12f));changed=true;
        }
        tracking=looking;lastX=x[0];lastY=y[0];
        Vec3 move=new Vec3(0,0,0);
        if(glfwGetKey(window,GLFW_KEY_W)==GLFW_PRESS) move=move.add(forward());
        if(glfwGetKey(window,GLFW_KEY_S)==GLFW_PRESS) move=move.sub(forward());
        if(glfwGetKey(window,GLFW_KEY_D)==GLFW_PRESS) move=move.add(right());
        if(glfwGetKey(window,GLFW_KEY_A)==GLFW_PRESS) move=move.sub(right());
        if(move.dot(move)>0) { position=position.add(move.normalized().mul(5*Math.min(dt,.1f)));changed=true; }
        return changed;
    }
}
