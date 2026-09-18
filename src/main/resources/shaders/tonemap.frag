#version 430 core
in vec2 uv;
out vec4 color;
uniform sampler2D image;
uniform int paused,sampleCount,denoise;
uniform float aspect;
uniform sampler2D normalDepth,albedoGuide;
float luminance(vec3 value) {
    return dot(value,vec3(.2126,.7152,.0722));
}
int glyphRow(int code,int row) {
    if(row<0||row>6) return 0;
    if(code==65) { int rows[7]=int[7](14,17,17,31,17,17,17);return rows[row]; }
    if(code==66) { int rows[7]=int[7](30,17,17,30,17,17,30);return rows[row]; }
    if(code==68) { int rows[7]=int[7](30,17,17,17,17,17,30);return rows[row]; }
    if(code==69) { int rows[7]=int[7](31,16,16,30,16,16,31);return rows[row]; }
    if(code==73) { int rows[7]=int[7](31,4,4,4,4,4,31);return rows[row]; }
    if(code==78) { int rows[7]=int[7](17,25,21,19,17,17,17);return rows[row]; }
    if(code==80) { int rows[7]=int[7](30,17,17,30,16,16,16);return rows[row]; }
    if(code==82) { int rows[7]=int[7](30,17,17,30,20,18,17);return rows[row]; }
    if(code==83) { int rows[7]=int[7](15,16,16,14,1,1,30);return rows[row]; }
    if(code==84) { int rows[7]=int[7](31,4,4,4,4,4,4);return rows[row]; }
    if(code==85) { int rows[7]=int[7](17,17,17,17,17,17,14);return rows[row]; }
    if(code==87) { int rows[7]=int[7](17,17,17,21,21,21,10);return rows[row]; }
    return 0;
}
float glyph(vec2 position,vec2 origin,float scale,int code) {
    vec2 local=(position-origin)/scale;
    int column=int(floor(local.x));int row=6-int(floor(local.y));
    if(column<0||column>4||row<0||row>6) return 0;
    return float((glyphRow(code,row)>>(4-column))&1);
}
void main() {
    ivec2 size=textureSize(image,0),pixel=clamp(ivec2(uv*vec2(size)),ivec2(0),size-1);
    vec3 center=texture(image,uv).rgb;
    vec4 geometry=texelFetch(normalDepth,pixel,0),albedo=texelFetch(albedoGuide,pixel,0);
    vec3 filtered=vec3(0);float totalWeight=0;
    float sigma=.12+.45*sqrt(max(luminance(center),0));
    for(int y=-2;y<=2;y++) for(int x=-2;x<=2;x++) {
        ivec2 q=clamp(pixel+ivec2(x,y),ivec2(0),size-1);
        vec4 g=texelFetch(normalDepth,q,0),a=texelFetch(albedoGuide,q,0);
        if(a.a!=albedo.a) continue;
        float normalWeight=geometry.w==0?1:pow(max(dot(geometry.xyz,g.xyz),0),64);
        float depthWeight=exp(-abs(g.w-geometry.w)/max(.015,.015*geometry.w));
        vec3 delta=a.rgb-albedo.rgb;
        float albedoWeight=exp(-dot(delta,delta)*80);
        vec3 neighbor=texelFetch(image,q,0).rgb;
        float difference=luminance(neighbor)-luminance(center);
        float weight=exp(-float(x*x+y*y)*.4-difference*difference/(sigma*sigma))*normalWeight*depthWeight*albedoWeight;
        filtered+=neighbor*weight;totalWeight+=weight;
    }
    // Fade filtering as convergence improves so fine grain is retained.
    float strength=denoise==0?0:.85/(1+float(sampleCount)*.015);
    vec3 hdr=max(mix(center,filtered/max(totalWeight,1e-8),strength),vec3(0));
    // Filmic highlight shoulder, applied exactly once in linear light.
    vec3 mapped=clamp((hdr*(2.51*hdr+.03))/(hdr*(2.43*hdr+.59)+.14),0,1);
    // Explicit linear -> sRGB; framebuffer sRGB conversion is disabled.
    vec3 srgb=mix(12.92*mapped,1.055*pow(mapped,vec3(1.0/2.4))-.055,step(vec3(.0031308),mapped));
    if(paused!=0) {
        srgb*=.32;
        vec2 p=vec2((uv.x-.5)*aspect,uv.y-.5);
        float panel=step(-.36,p.x)*step(p.x,.36)*step(-.40,p.y)*step(p.y,.32);
        float header=step(-.36,p.x)*step(p.x,.36)*step(.19,p.y)*step(p.y,.30);
        float firstButton=step(-.28,p.x)*step(p.x,.28)*step(-.16,p.y)*step(p.y,-.06);
        float secondButton=step(-.28,p.x)*step(p.x,.28)*step(-.27,p.y)*step(p.y,-.17);
        float thirdButton=step(-.28,p.x)*step(p.x,.28)*step(-.38,p.y)*step(p.y,-.28);
        srgb+=vec3(.035,.05,.075)*panel;
        srgb+=vec3(.08,.16,.24)*header;
        srgb+=vec3(.12,.38,.55)*firstButton;
        srgb+=vec3(.12,.24,.32)*secondButton;
        srgb+=vec3(.16,.12,.12)*thirdButton;
        float text=0;
        float titleScale=.008;
        text+=glyph(p,vec2(-.10,.245),titleScale,80)+glyph(p,vec2(-.06,.245),titleScale,65)+glyph(p,vec2(-.02,.245),titleScale,85)+glyph(p,vec2(.02,.245),titleScale,83)+glyph(p,vec2(.06,.245),titleScale,69);
        float buttonScale=.0065;
        text+=glyph(p,vec2(-.072,-.105),buttonScale,87)+glyph(p,vec2(-.039,-.105),buttonScale,69)+glyph(p,vec2(-.006,-.105),buttonScale,73)+glyph(p,vec2(.027,-.105),buttonScale,84)+glyph(p,vec2(.060,-.105),buttonScale,69)+glyph(p,vec2(.093,-.105),buttonScale,82);
        text+=glyph(p,vec2(-.095,-.225),buttonScale,78)+glyph(p,vec2(-.062,-.225),buttonScale,69)+glyph(p,vec2(-.029,-.225),buttonScale,85)+glyph(p,vec2(.004,-.225),buttonScale,83)+glyph(p,vec2(.037,-.225),buttonScale,84)+glyph(p,vec2(.070,-.225),buttonScale,65)+glyph(p,vec2(.103,-.225),buttonScale,82)+glyph(p,vec2(.136,-.225),buttonScale,84);
        text+=glyph(p,vec2(-.075,-.315),buttonScale,66)+glyph(p,vec2(-.042,-.315),buttonScale,69)+glyph(p,vec2(-.009,-.315),buttonScale,69)+glyph(p,vec2(.024,-.315),buttonScale,78)+glyph(p,vec2(.057,-.315),buttonScale,68)+glyph(p,vec2(.090,-.315),buttonScale,69)+glyph(p,vec2(.123,-.315),buttonScale,78);
        srgb+=vec3(1)*min(text,1);
    }
    color=vec4(srgb,1);
}
