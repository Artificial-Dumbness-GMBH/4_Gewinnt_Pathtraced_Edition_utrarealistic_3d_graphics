#version 430 core
in vec2 uv;
out vec4 color;
uniform sampler2D image;
uniform int paused,sampleCount,denoise;
uniform float denoiseStrength,exposure,sharpness;
uniform sampler2D normalDepth,albedoGuide;
float luminance(vec3 value) {
    return dot(value,vec3(.2126,.7152,.0722));
}
void main() {
    ivec2 size=textureSize(image,0),pixel=clamp(ivec2(uv*vec2(size)),ivec2(0),size-1);
    vec3 center=texture(image,uv).rgb;
    vec4 geometry=texelFetch(normalDepth,pixel,0),albedo=texelFetch(albedoGuide,pixel,0);
    vec3 filtered=vec3(0);float totalWeight=0;
    float sigma=denoiseStrength*(.12+.45*sqrt(max(luminance(center),0)));
    if(denoise!=0) for(int y=-2;y<=2;y++) for(int x=-2;x<=2;x++) {
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
    if(sharpness>0&&denoise==0) {
        vec2 texel=1.0/vec2(size);
        vec3 blur=(texture(image,uv+vec2(texel.x,0)).rgb+texture(image,uv-vec2(texel.x,0)).rgb
                  +texture(image,uv+vec2(0,texel.y)).rgb+texture(image,uv-vec2(0,texel.y)).rgb)*.25;
        hdr=max(hdr+sharpness*(center-blur),vec3(0));
    }
    // Filmic highlight shoulder, applied exactly once in linear light.
    hdr*=exposure;
    vec3 mapped=clamp((hdr*(2.51*hdr+.03))/(hdr*(2.43*hdr+.59)+.14),0,1);
    // Explicit linear -> sRGB; framebuffer sRGB conversion is disabled.
    vec3 srgb=mix(12.92*mapped,1.055*pow(mapped,vec3(1.0/2.4))-.055,step(vec3(.0031308),mapped));
    if(paused!=0) srgb*=.30;
    color=vec4(srgb,1);
}
