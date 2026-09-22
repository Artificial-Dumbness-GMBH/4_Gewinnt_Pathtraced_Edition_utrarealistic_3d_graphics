// Port of resources/shaders/pathtrace.comp; same BVH and material layout.
struct Material { float4 baseColor; float4 emission; float4 surface; };
struct Node { float4 lower; float4 upper; int4 data; };
StructuredBuffer<float4> vertices : register(t0);
StructuredBuffer<uint4> triangles : register(t1);
StructuredBuffer<Material> materials : register(t2);
StructuredBuffer<Node> nodes : register(t3);
RWTexture2D<float4> accumulation : register(u0);
RWTexture2D<float4> normalDepth : register(u1);
RWTexture2D<float2> motion : register(u2);
RWTexture2D<float> deviceDepth : register(u3);
cbuffer Frame : register(b0) {
    float3 cameraPosition; uint frameIndex;
    float3 cameraForward; uint sampleSequence;
    float3 cameraRight; uint samplesPerFrame;
    float3 cameraUp; uint maxBounces;
    float3 previousPosition; uint triangleCount;
    float3 previousForward; int movingVertexStart;
    float3 previousRight; float movingLift;
    float3 previousUp; float previousLift;
    uint2 renderSize; float2 jitter;
    uint2 outputSize; float exposure; uint temporal;
};
static const float3 lightPosition=float3(0,13.8,0),lightSize=float3(6,0,5),lightRadiance=float3(18,17.2,16);
static const int lightMaterial=4,bruteForce=0,gradient=0;
#define movingOffset float3(0,movingLift,0)
const float PI=3.14159265359;
static uint rng;
float randomFloat() {
    rng=rng*747796405u+2891336453u;
    uint word=((rng>>((rng>>28u)+4u))^rng)*277803737u;
    return float((word>>22u)^word)*(1.0/4294967296.0);
}
bool boxHit(Node n,float3 origin,float3 direction,float closest) {
    if(n.lower.w>0) origin-=movingOffset; // Tight local bounds for moving-only subtrees.
    float lo=.0001,hi=closest;
    for(int a=0;a<3;a++) {
        if(abs(direction[a])<1e-8) {
            if(origin[a]<n.lower[a]||origin[a]>n.upper[a]) return false;
        } else {
            float x=(n.lower[a]-origin[a])/direction[a],y=(n.upper[a]-origin[a])/direction[a];
            lo=max(lo,min(x,y));hi=min(hi,max(x,y));if(hi<lo) return false;
        }
    }
    return true;
}
float triangleDistance(int i,float3 origin,float3 direction) {
    uint4 t=triangles[i];float3 a=vertices[t.x].xyz,e1=vertices[t.y].xyz-a,e2=vertices[t.z].xyz-a;
    if(movingVertexStart>=0&&t.x>=uint(movingVertexStart)) a+=movingOffset;
    float3 p=cross(direction,e2);float det=dot(e1,p);if(abs(det)<1e-8) return 1e30;
    float3 s=origin-a;float u=dot(s,p)/det;if(u<0||u>1) return 1e30;
    float3 q=cross(s,e1);float v=dot(direction,q)/det;if(v<0||u+v>1) return 1e30;
    float distance=dot(e2,q)/det;
    return distance>.0001?distance:1e30;
}
void triangleHit(int i,float3 origin,float3 direction,inout float closest,inout int hit) {
    float distance=triangleDistance(i,origin,direction);
    if(distance<closest) { closest=distance;hit=i; }
}
// Shadow visibility only needs any blocker on the finite light segment.
bool occluded(float3 origin,float3 direction,float limit) {
    if(bruteForce!=0) {
        for(int i=0;i<triangleCount;i++) if(triangleDistance(i,origin,direction)<limit) return true;
    } else {
        uint stack[32];int count=1;stack[0]=0u;
        while(count>0) {
            Node n=nodes[stack[--count]];
            if(!boxHit(n,origin,direction,limit)) continue;
            if(n.data.w>0) {
                for(int j=0;j<n.data.w;j++) if(triangleDistance(n.data.z+j,origin,direction)<limit) return true;
            } else { stack[count++]=uint(n.data.y);stack[count++]=uint(n.data.x); }
        }
    }
    return false;
}
int intersectScene(float3 origin,float3 direction,out float distance,out float3 normal) {
    distance=1e30;int hit=-1;normal=((float3)(0));
    if(bruteForce!=0) {
        for(int i=0;i<triangleCount;i++) triangleHit(i,origin,direction,distance,hit);
    } else {
        uint stack[32];int count=1;stack[0]=0u;
        while(count>0) {
            Node n=nodes[stack[--count]];
            if(!boxHit(n,origin,direction,distance)) continue;
            if(n.data.w>0) {
                for(int j=0;j<n.data.w;j++) triangleHit(n.data.z+j,origin,direction,distance,hit);
            } else {
                // Builder caps depth at 30; at most 31 pending entries.
                stack[count++]=uint(n.data.y);stack[count++]=uint(n.data.x);
            }
        }
    }
    if(hit>=0) {
        uint4 t=triangles[hit];float3 a=vertices[t.x].xyz;
        normal=normalize(cross(vertices[t.y].xyz-a,vertices[t.z].xyz-a));
        if(dot(normal,direction)>0) normal=-normal;
    }
    return hit;
}
float3 cosineHemisphere(float3 normal) {
    float r=sqrt(randomFloat()),phi=6.28318530718*randomFloat();
    float3 tangent=normalize(cross(abs(normal.y)<.99?float3(0,1,0):float3(1,0,0),normal));
    return normalize(tangent*(r*cos(phi))+cross(normal,tangent)*(r*sin(phi))+normal*sqrt(max(0,1-r*r)));
}
// World-space procedural textures need no external assets or UV seams.
float hash(float3 p) { return frac(sin(dot(p,float3(127.1,311.7,74.7)))*43758.5453); }
float noise(float3 p) {
    float3 i=floor(p),f=frac(p);f=f*f*(3-2*f);
    return lerp(lerp(lerp(hash(i),hash(i+float3(1,0,0)),f.x),lerp(hash(i+float3(0,1,0)),hash(i+float3(1,1,0)),f.x),f.y),
               lerp(lerp(hash(i+float3(0,0,1)),hash(i+float3(1,0,1)),f.x),lerp(hash(i+float3(0,1,1)),hash(i+float3(1,1,1)),f.x),f.y),f.z);
}
float3 surfaceColor(Material m,float3 p,out float roughness) {
    p*=m.surface.w;roughness=m.surface.x;
    if(m.surface.z==1) {
        float grain=.5+.5*sin(p.z*18+noise(p*float3(.15,2,1.5))*3);
        float broad=noise(p*float3(.35,2,3));
        float pores=noise(p*float3(3,18,55));
        roughness=clamp(roughness+(pores-.5)*.12,.08,1);
        return m.baseColor.rgb*lerp(.72,1.05,.55*broad+.30*grain+.15*pores);
    }
    if(m.surface.z==2) {
        float mottling=.65+.35*noise(p*4);
        float2 tile=abs(frac(p.xz*.35)-.5);
        float grout=smoothstep(.478,.492,max(tile.x,tile.y));
        return m.baseColor.rgb*lerp(mottling,.30,grout);
    }
    return m.baseColor.rgb;
}
float3 fresnel(float3 f0,float cosine) { return f0+(1-f0)*pow(clamp(1-cosine,0,1),5); }
float distribution(float nh,float roughness) {
    float a=roughness*roughness,a2=a*a;
    float d=nh*nh*(a2-1)+1;
    return a2/(PI*d*d);
}
float masking(float nv,float roughness) {
    float a=roughness*roughness;
    return 2*nv/max(nv+sqrt(a*a+(1-a*a)*nv*nv),1e-7);
}
float specularChance(float metallic) { return lerp(.35,.9,metallic); }
    return a*b+c; // NV_gpu_shader5 guarantees half arithmetic, not a half FMA overload.

}
#endif
float3 brdf(float3 n,float3 v,float3 l,float3 base,float roughness,float metallic,out float pdf) {
    float nv=max(dot(n,v),0),nl=max(dot(n,l),0);pdf=0;
    if(nv<=0||nl<=0||dot(v+l,v+l)<1e-10) return ((float3)(0));
    float3 h=normalize(v+l);float nh=max(dot(n,h),0),vh=max(dot(v,h),1e-6);
    float3 f=fresnel(lerp(((float3)(.04)),base,metallic),vh);
    float3 diffuse=(1-f)*(1-metallic)*base/PI;

    float d=distribution(nh,roughness),chance=specularChance(metallic);
    pdf=lerp(nl/PI,d*nh/(4*vh),chance);
    return diffuse+f*d*masking(nv,roughness)*masking(nl,roughness)/max(4*nv*nl,1e-7);
}
float3 sampleDirection(float3 n,float3 v,float roughness,float metallic) {
    if(randomFloat()>specularChance(metallic)) return cosineHemisphere(n);
    float a=roughness*roughness,u=min(randomFloat(),.999999),phi=2*PI*randomFloat();
    float ct=sqrt((1-u)/(1+(a*a-1)*u)),st=sqrt(max(0,1-ct*ct));
    float3 t=normalize(cross(abs(n.y)<.99?float3(0,1,0):float3(1,0,0),n));
    float3 h=normalize(t*(st*cos(phi))+cross(n,t)*(st*sin(phi))+n*ct);
    return reflect(-v,h);
}
float lightPdf(float3 from,float3 point,float3 direction) {
    float lightCosine=max(direction.y,0);
    return lightCosine>0?dot(point-from,point-from)/(4*lightSize.x*lightSize.z*lightCosine):0;
}
float powerWeight(float a,float b) { return a*a/max(a*a+b*b,1e-20); }
float3 directLight(float3 point,float3 n,float3 v,float3 base,float roughness,float metallic,bool lastBounce) {
    float3 target=lightPosition+float3((2*randomFloat()-1)*lightSize.x,0,(2*randomFloat()-1)*lightSize.z);
    float3 delta=target-point;float distanceToLight=length(delta);float3 l=delta/distanceToLight;
    if(dot(n,l)<=0||l.y<=0) return ((float3)(0));
    if(occluded(point+n*.001,l,distanceToLight-.003)) return ((float3)(0));
    float pdf;float3 f=brdf(n,v,l,base,roughness,metallic,pdf);
    float lp=lightPdf(point,target,l);
    // No BSDF continuation at the last bounce, so there is no competing estimator.
    return lightRadiance*f*max(dot(n,l),0)*(lastBounce?1:powerWeight(lp,pdf))/max(lp,1e-7);
}
float3 trace(float3 origin,float3 direction) {
    float3 radiance=((float3)(0)),throughput=((float3)(1)),previousPoint=origin;
    float previousPdf=0;
    for(int bounce=0;bounce<maxBounces;bounce++) {
        float distance;float3 normal;int hit=intersectScene(origin,direction,distance,normal);
        if(hit<0) {
            radiance+=throughput*lerp(float3(.035,.045,.07),float3(.22,.30,.45),clamp(direction.y*.5+.5,0,1));break;
        }
        float3 point=origin+direction*distance;
        uint materialIndex=triangles[hit].w;Material m=materials[materialIndex];
        if(any(m.emission.rgb>0)) {
            float weight=1;
            if(materialIndex==uint(lightMaterial)) {
                if(direction.y<=0) break;
                if(bounce>0) weight=powerWeight(previousPdf,lightPdf(previousPoint,point,direction));
            }
            radiance+=throughput*m.emission.rgb*weight;break;
        }
        float roughness;float3 base=surfaceColor(m,point,roughness),view=-direction;
        radiance+=throughput*directLight(point,normal,view,base,roughness,m.surface.y,bounce==maxBounces-1);
        if(bounce==maxBounces-1) break;
        float3 next=sampleDirection(normal,view,roughness,m.surface.y);
        float pdf;float3 f=brdf(normal,view,next,base,roughness,m.surface.y,pdf);
        if(pdf<=1e-8) break;
        throughput*=f*max(dot(normal,next),0)/pdf;
        if(max(throughput.r,max(throughput.g,throughput.b))<1e-5) break;
        // Fixed bounce budget: no roulette probability to fold into MIS densities.
        previousPoint=point;previousPdf=pdf;
        origin=point+normal*.001;direction=next;
    }
    return radiance;
}

float3 cameraRay(float2 pixel) {
    float2 p=pixel/float2(renderSize)*2-1; p.y=-p.y;
    p.x*=float(renderSize.x)/renderSize.y;
    return normalize(cameraForward+.57735026919*(p.x*cameraRight+p.y*cameraUp));
}
[numthreads(8,8,1)]
void main(uint3 id : SV_DispatchThreadID) {
    uint2 pixel=id.xy;
    if(any(pixel>=renderSize)) return;
    float3 normal; float distance;
    float3 ray=cameraRay(float2(pixel)+.5+jitter);
    int hit=intersectScene(cameraPosition,ray,distance,normal);
    float3 point=cameraPosition+ray*(hit>=0?distance:1000);
    float z=dot(point-cameraPosition,cameraForward);
    deviceDepth[pixel]=hit>=0?saturate(1000.0/999.9-100.0/(999.9*max(z,.1))):1;
    normalDepth[pixel]=float4(normal,hit>=0?distance:0);
    if(hit>=0 && movingVertexStart>=0 && triangles[hit].x>=uint(movingVertexStart))
        point.y+=previousLift-movingLift;
    float3 delta=point-previousPosition;
    float prevZ=dot(delta,previousForward);
    float2 prevUV=float2(dot(delta,previousRight)/(max(prevZ,.001)*.57735026919*float(renderSize.x)/renderSize.y),
        -dot(delta,previousUp)/(max(prevZ,.001)*.57735026919))*.5+.5;
    // Previous minus current, in render-resolution pixels, without jitter.
    motion[pixel]=prevZ>.001?(prevUV*float2(renderSize)-(float2(pixel)+.5+jitter)):float2(0,0);
    float3 color=0;
    for(uint sampleIndex=0;sampleIndex<samplesPerFrame;sampleIndex++) {
        rng=(pixel.x+pixel.y*renderSize.x)^((sampleSequence*samplesPerFrame+sampleIndex+1)*277803737u);
        float2 offset=temporal!=0?.5+jitter:float2(randomFloat(),randomFloat());
        color+=trace(cameraPosition,cameraRay(float2(pixel)+offset));
    }
    color/=samplesPerFrame;
    if(temporal==0 && frameIndex>0) color=lerp(accumulation[pixel].rgb,color,1.0/(frameIndex+1));
    accumulation[pixel]=float4(color,1);
}
