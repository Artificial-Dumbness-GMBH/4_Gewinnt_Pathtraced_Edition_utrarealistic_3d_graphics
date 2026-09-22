Texture2D<float4> image : register(t0);
SamplerState linearClamp : register(s0);
cbuffer Display : register(b0) { float exposure; };
struct Output { float4 position:SV_POSITION; float2 uv:TEXCOORD0; };
Output vs(uint id:SV_VertexID) {
    Output o; o.uv=float2((id<<1)&2,id&2);
    o.position=float4(o.uv*float2(2,-2)+float2(-1,1),0,1); return o;
}
float4 ps(Output input):SV_TARGET {
    float3 c=max(image.SampleLevel(linearClamp,input.uv,0).rgb*exposure,0);
    c=saturate((c*(2.51*c+.03))/(c*(2.43*c+.59)+.14));
    c=lerp(12.92*c,1.055*pow(c,1.0/2.4)-.055,step(.0031308,c));
    return float4(c,1);
}
