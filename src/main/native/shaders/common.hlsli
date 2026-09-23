#ifndef COMMON_HLSLI
#define COMMON_HLSLI
cbuffer Constants : register(b0) {
    float4 cameraPosition4,cameraForward4,cameraRight4,cameraUp4;
    float4 previousPosition,previousForward,previousRight,previousUp;
    float4 lightPosition4,lightSize4,lightRadiance4;
    uint4 counts; // accumulation frame, sequence, samples, bounces
    int4 geometry; // triangle count, moving vertex, hardware RT, jitter enabled
    float4 motion; // lift, previous lift, jitter x/y
    float4 dimensions; // render width/height, output width/height
    float4 options; // exposure, filter strength, sharpness, temporal blend
    uint4 post; // pass, source texture, valid history, UI mode
};
#define cameraPosition cameraPosition4.xyz
#define cameraForward cameraForward4.xyz
#define cameraRight cameraRight4.xyz
#define cameraUp cameraUp4.xyz
#define lightPosition lightPosition4.xyz
#define lightSize lightSize4.xyz
#define lightRadiance lightRadiance4.xyz
#define frameIndex counts.x
#define sampleSequence counts.y
#define samplesPerFrame counts.z
#define maxBounces counts.w
#define triangleCount geometry.x
#define movingVertexStart geometry.y
#define movingOffset float3(0,motion.x,0)
#define lightMaterial 4
#define bruteForce 0
struct Material { float4 baseColor,emission,surface; };
struct Node { float4 lower,upper; int4 data; };
StructuredBuffer<float4> vertices : register(t0);
StructuredBuffer<uint4> triangles : register(t1);
StructuredBuffer<Material> materials : register(t2);
StructuredBuffer<Node> nodes : register(t3);
#ifdef HARDWARE_RT
RaytracingAccelerationStructure scene : register(t4);
#endif
Texture2D<float4> textures[16] : register(t5);
RWTexture2D<float4> outputs[16] : register(u0);
SamplerState linearClamp : register(s0);
float3 cameraRay(float2 pixel,int2 size) {
    float2 p=pixel/float2(size)*2-1;p.y=-p.y;p.x*=float(size.x)/size.y;
    return normalize(cameraForward+.57735026919*(p.x*cameraRight+p.y*cameraUp));
}
float2 projectPrevious(float3 world) {
    float3 delta=world-previousPosition.xyz;
    float z=max(dot(delta,previousForward.xyz),.0001);
    float2 p=float2(dot(delta,previousRight.xyz)*dimensions.y/dimensions.x,-dot(delta,previousUp.xyz))/(z*.57735026919);
    return (p*.5+.5)*dimensions.xy;
}
#endif
