#version 430 core
in vec2 uv;
out vec4 color;
uniform sampler2D image;
void main() {
    vec3 hdr=max(texture(image,uv).rgb,vec3(0));
    vec3 mapped=hdr/(vec3(1)+hdr);
    // Explicit linear -> sRGB; framebuffer sRGB conversion is disabled.
    vec3 srgb=mix(12.92*mapped,1.055*pow(mapped,vec3(1.0/2.4))-.055,step(vec3(.0031308),mapped));
    color=vec4(srgb,1);
}
