#version 430 core
in vec2 uv;
out vec4 color;
uniform sampler2D image;
float luminance(vec3 value) {
    return dot(value,vec3(.2126,.7152,.0722));
}
void main() {
    vec2 texel=1.0/vec2(textureSize(image,0));
    vec3 center=texture(image,uv).rgb;
    float centerLuminance=luminance(center);
    vec3 filtered=vec3(0);float totalWeight=0;
    float edgeSigma=.055+.16*sqrt(max(centerLuminance,0));
    for(int offsetY=-1;offsetY<=1;offsetY++) for(int offsetX=-1;offsetX<=1;offsetX++) {
        vec3 neighbor=texture(image,uv+vec2(offsetX,offsetY)*texel).rgb;
        float radiusSquared=float(offsetX*offsetX+offsetY*offsetY);
        float spatialWeight=exp(-radiusSquared*1.0);
        float luminanceDifference=abs(luminance(neighbor)-centerLuminance);
        float edgeWeight=exp(-(luminanceDifference*luminanceDifference)/(edgeSigma*edgeSigma));
        float weight=spatialWeight*edgeWeight;
        filtered+=neighbor*weight;totalWeight+=weight;
    }
    vec3 denoised=filtered/max(totalWeight,.001);
    vec3 hdr=max(mix(denoised,center,.3),vec3(0));
    vec3 mapped=hdr/(vec3(1)+hdr);
    // Explicit linear -> sRGB; framebuffer sRGB conversion is disabled.
    vec3 srgb=mix(12.92*mapped,1.055*pow(mapped,vec3(1.0/2.4))-.055,step(vec3(.0031308),mapped));
    color=vec4(srgb,1);
}
