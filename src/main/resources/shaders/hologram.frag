#version 430 core
in vec2 uv;
in vec3 worldPosition;
out vec4 color;
uniform sampler2D label,normalDepth;
uniform vec3 tint,cameraPosition,viewport;
uniform float time,opacity;
uniform int plainLabel;
void main() {
    ivec2 size=textureSize(normalDepth,0);
    ivec2 pixel=clamp(ivec2(gl_FragCoord.xy/viewport.xy*vec2(size)),ivec2(0),size-1);
    float surfaceDepth=texelFetch(normalDepth,pixel,0).w;
    // Labels sit on a surface: tolerate one coarse guide pixel, capped in world space.
    float tolerance=plainLabel!=0?max(.035,min(.15,surfaceDepth/float(size.y))):.035;
    if(surfaceDepth>0&&length(worldPosition-cameraPosition)>surfaceDepth+tolerance) discard;
    vec4 text=texture(label,uv);
    if(plainLabel!=0) { color=vec4(text.rgb,text.a*opacity);return; }
    float scan=.95+.05*sin(uv.y*256*3.14159265-time*2);
    float pulse=.96+.04*sin(time*1.6);
    vec2 edge=abs(uv-.5);
    float border=min(abs(edge.x-.48)*4,abs(edge.y-.43));
    float glow=exp(-border*95)*.16;
    color=vec4(text.rgb*scan*pulse+tint*glow,clamp(text.a+glow,0,1)*opacity);
}
