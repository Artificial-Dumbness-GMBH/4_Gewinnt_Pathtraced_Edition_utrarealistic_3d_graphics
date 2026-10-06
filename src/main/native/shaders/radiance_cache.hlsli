#ifndef RADIANCE_CACHE_HLSLI
#define RADIANCE_CACHE_HLSLI
// Experimental view-independent approximation, limited to rough dielectric surfaces.
// Read only last frame's immutable snapshot; atomic writes train the next frame.
static const uint CACHE_SLOTS=16384,CACHE_STRIDE=32,CACHE_MAX_SAMPLES=128;
uint cacheHash(float3 p,float3 n,uint material) {
    int3 cell=int3(floor(p*4));int3 normal=int3(round(n*4));
    uint h=uint(cell.x)*73856093u^uint(cell.y)*19349663u^uint(cell.z)*83492791u;
    h^=uint(normal.x+4)*2654435761u^uint(normal.y+4)*2246822519u^uint(normal.z+4)*3266489917u^material*668265263u;
    h^=h>>16;h*=2246822519u;h^=h>>13;return h==0?1:h;
}
bool cacheLookup(uint key,out float3 value) {
    uint offset=(key&(CACHE_SLOTS-1))*CACHE_STRIDE;uint count=cacheRead.Load(offset+4);value=0;
    if(features.y==0||cacheRead.Load(offset)!=key||count<8||count>CACHE_MAX_SAMPLES) return false;
    value=float3(cacheRead.Load3(offset+8))/(4096.0*count);return true;
}
void cacheTrain(uint key,float3 value) {
    if(any(!isfinite(value))||any(value<0)) return;
    uint offset=(key&(CACHE_SLOTS-1))*CACHE_STRIDE,old;
    cacheWrite.InterlockedCompareExchange(offset,0,key,old);if(old!=0&&old!=key) return;
    uint count=cacheWrite.Load(offset+4);
    [allow_uav_condition] while(count<CACHE_MAX_SAMPLES) {
        cacheWrite.InterlockedCompareExchange(offset+4,count,count+1,old);
        if(old==count) {
            uint3 q=uint3(min(value,64)*4096+.5);
            cacheWrite.InterlockedAdd(offset+8,q.x,old);cacheWrite.InterlockedAdd(offset+12,q.y,old);cacheWrite.InterlockedAdd(offset+16,q.z,old);return;
        }
        count=old;
    }
}
#endif
