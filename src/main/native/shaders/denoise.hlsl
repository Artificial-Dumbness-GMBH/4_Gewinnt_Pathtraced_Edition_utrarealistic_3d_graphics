Texture2D<float4> color : register(t0);
Texture2D<float4> guide : register(t1);
RWTexture2D<float4> result : register(u0);
cbuffer Filter : register(b0) { uint width,height; float strength; uint enabled; };
[numthreads(8,8,1)]
void main(uint3 id:SV_DispatchThreadID) {
    int2 p=id.xy;if(p.x>=width||p.y>=height) return;
    float4 center=guide[p];float3 sum=0;float total=0;
    if(enabled==0) { result[p]=color[p];return; }
    for(int y=-2;y<=2;y++) for(int x=-2;x<=2;x++) {
        int2 q=clamp(p+int2(x,y),int2(0,0),int2(width-1,height-1));
        float4 g=guide[q];
        float w=exp(-float(x*x+y*y)/(2*strength*strength))*pow(saturate(dot(center.xyz,g.xyz)),32)
            *exp(-abs(center.w-g.w)/max(.02,center.w*.015));
        if(x==0&&y==0) w=1;
        sum+=color[q].rgb*w;total+=w;
    }
    result[p]=float4(sum/max(total,1e-8),1);
}
