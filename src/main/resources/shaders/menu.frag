#version 430 core
in vec2 uv;
out vec4 color;
uniform sampler2D menuTexture;
uniform vec3 viewport;
uniform float panelScale;
void main() {
    vec2 size=vec2(960,660)*panelScale;
    vec2 point=vec2(uv.x,1-uv.y)*viewport.xy;
    vec2 local=(point-(viewport.xy-size)*.5)/size;
    if(any(lessThan(local,vec2(0)))||any(greaterThan(local,vec2(1)))) { color=vec4(0);return; }
    color=texture(menuTexture,local);
}
