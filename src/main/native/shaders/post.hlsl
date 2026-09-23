#include "common.hlsli"
float luminance(float3 c) { return dot(c,float3(.2126,.7152,.0722)); }
[numthreads(8,8,1)]
void denoise(uint3 tid:SV_DispatchThreadID) {
    int2 p=tid.xy,size=int2(dimensions.xy);if(any(p>=size)) return;
    float4 guide=textures[1][p],albedo=textures[2][p];
    float3 center=textures[post.y][p].rgb,total=0;float weightSum=0;
    int stride=int(1u<<post.x);
    float sigma=options.y*(.12+.45*sqrt(max(luminance(center),0)));
    for(int y=-2;y<=2;y++) for(int x=-2;x<=2;x++) {
        int2 q=clamp(p+int2(x,y)*stride,0,size-1);
        float4 g=textures[1][q],a=textures[2][q];
        if(a.w!=albedo.w) continue;
        float3 neighbor=textures[post.y][q].rgb;
        float3 wp=cameraRay(p+.5+motion.zw,size)*guide.w,wq=cameraRay(q+.5+motion.zw,size)*g.w;
        float normalWeight=guide.w==0?1:pow(max(dot(guide.xyz,g.xyz),0),32);
        float plane=guide.w==0?0:abs(dot(wp-wq,guide.xyz));
        float lum=luminance(neighbor)-luminance(center);
        float3 delta=a.rgb-albedo.rgb;
        float weight=exp(-float(x*x+y*y)*.4-lum*lum/max(sigma*sigma,.0001)-dot(delta,delta)*80
            -plane/max(.02,guide.w*.002))*normalWeight;
        total+=neighbor*weight;weightSum+=weight;
    }
    outputs[5+(post.x%2)][p]=float4(total/max(weightSum,1e-8),1);
}
[numthreads(8,8,1)]
void temporal(uint3 tid:SV_DispatchThreadID) {
    int2 p=tid.xy,size=int2(dimensions.xy);if(any(p>=size)) return;
    float3 color=textures[post.y][p].rgb;
    float2 oldPixel=p+.5+motion.zw+textures[4][p].xy-float2(previousPosition.w,previousForward.w);
    if(post.z!=0&&options.w>0&&all(oldPixel>=.5)&&all(oldPixel<float2(size)-.5)) {
        int2 q=int2(oldPixel);float4 guide=textures[1][p],oldGuide=textures[8][q];
        float3 world=cameraPosition+cameraRay(p+.5+motion.zw,size)*guide.w;
        float expected=length(world-previousPosition.xyz);
        if(dot(guide.xyz,oldGuide.xyz)>.95&&abs(oldGuide.w-expected)<max(.04,.01*expected)) {
            float3 lo=color,hi=color;
            for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
                float3 n=textures[post.y][clamp(p+int2(x,y),0,size-1)].rgb;lo=min(lo,n);hi=max(hi,n);
            }
            float3 history=textures[7].SampleLevel(linearClamp,oldPixel/dimensions.xy,0).rgb;
            color=lerp(color,clamp(history,lo,hi),options.w);
        }
    }
    outputs[9][p]=float4(color,1);
}
[numthreads(8,8,1)]
void history(uint3 tid:SV_DispatchThreadID) {
    if(any(tid.xy>=uint2(dimensions.xy))) return;
    outputs[7][tid.xy]=textures[9][tid.xy];outputs[8][tid.xy]=textures[1][tid.xy];
}
[numthreads(8,8,1)]
void tonemap(uint3 tid:SV_DispatchThreadID) {
    if(any(tid.xy>=uint2(dimensions.zw))) return;
    float2 uv=(tid.xy+.5)/dimensions.zw;
    uint w,h;textures[post.y].GetDimensions(w,h);float2 texel=1.0/float2(w,h);
    float3 color=textures[post.y].SampleLevel(linearClamp,uv,0).rgb;
    float3 blur=(textures[post.y].SampleLevel(linearClamp,uv+float2(texel.x,0),0).rgb
        +textures[post.y].SampleLevel(linearClamp,uv-float2(texel.x,0),0).rgb
        +textures[post.y].SampleLevel(linearClamp,uv+float2(0,texel.y),0).rgb
        +textures[post.y].SampleLevel(linearClamp,uv-float2(0,texel.y),0).rgb)*.25;
    color=max(color+options.z*(color-blur),0)*options.x;
    float3 mapped=saturate((color*(2.51*color+.03))/(color*(2.43*color+.59)+.14));
    float3 srgb=lerp(12.92*mapped,1.055*pow(mapped,1.0/2.4)-.055,step(.0031308,mapped));
    outputs[11][tid.xy]=float4(srgb,1);
}
[numthreads(8,8,1)]
void composite(uint3 tid:SV_DispatchThreadID) {
    if(any(tid.xy>=uint2(dimensions.zw))) return;
    float3 color=textures[11][tid.xy].rgb;
    if(post.w!=0) {
        float scale=max(.001,min((dimensions.z-24)/960,(dimensions.w-24)/660));
        float2 panel=(float2(tid.xy)+.5-(dimensions.zw-float2(960,660)*scale)*.5)/scale;
        if(post.w==1) color*=.3;
        if(all(panel>=0)&&all(panel<float2(960,660))) {
            float4 ui=textures[13].SampleLevel(linearClamp,panel/float2(960,660),0);
            color=lerp(color,ui.rgb,ui.a);
        }
    }
    outputs[12][tid.xy]=float4(color,1);
}
