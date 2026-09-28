#version 430 core
uniform vec3 anchor,panelRight,cameraPosition,cameraForward,cameraRight,cameraUp;
uniform float aspect;
uniform vec3 panelSize;
out vec2 uv;
out vec3 worldPosition;
void main() {
    const vec2 corners[6]=vec2[6](vec2(0,0),vec2(1,0),vec2(1,1),vec2(0,0),vec2(1,1),vec2(0,1));
    uv=corners[gl_VertexID];
    worldPosition=anchor+(uv.x-.5)*panelSize.x*panelRight+vec3(0,(.5-uv.y)*panelSize.y,0);
    vec3 relative=worldPosition-cameraPosition;
    float distance=dot(relative,cameraForward),nearPlane=.05,farPlane=150;
    gl_Position=vec4(dot(relative,cameraRight)/(.57735026919*aspect),dot(relative,cameraUp)/.57735026919,
        (farPlane+nearPlane)/(farPlane-nearPlane)*distance-2*farPlane*nearPlane/(farPlane-nearPlane),distance);
}
