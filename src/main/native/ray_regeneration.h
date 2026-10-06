#pragma once
#include "camera_matrices.h"
#ifdef PT_FSR
#include <ffx_denoiser.h>
#endif
class RayRegeneration {
    bool supported=false,enabled=false;
#ifdef PT_FSR
    HMODULE module=nullptr;
    ffxContext context=nullptr;
    PfnFfxCreateContext create=nullptr;PfnFfxDestroyContext destroyContext=nullptr;PfnFfxDispatch dispatch=nullptr;
    PfnFfxQuery query=nullptr;
#endif
public:
    bool available() const { return supported; }
    bool active() const { return enabled; }
    void init(ID3D12Device* device) {
        (void)device;
#ifdef PT_FSR
        module=loadSibling(L"amd_fidelityfx_denoiser_dx12.dll");
        create=symbol<PfnFfxCreateContext>(module,"ffxCreateContext");destroyContext=symbol<PfnFfxDestroyContext>(module,"ffxDestroyContext");
        dispatch=symbol<PfnFfxDispatch>(module,"ffxDispatch");query=symbol<PfnFfxQuery>(module,"ffxQuery");
        if(create&&destroyContext&&dispatch&&query) {
            uint64_t count=0;ffxQueryDescGetVersions q{};q.header.type=FFX_API_QUERY_DESC_TYPE_GET_VERSIONS;
            q.createDescType=FFX_API_CREATE_CONTEXT_DESC_TYPE_DENOISER;q.device=device;q.outputCount=&count;
            supported=query(nullptr,&q.header)==FFX_API_RETURN_OK&&count>0;
        }
#endif
    }
    void configure(ID3D12Device* device,bool requested,UINT w,UINT h) {
        release();(void)device;(void)requested;(void)w;(void)h;
#ifdef PT_FSR
        if(!requested||!supported) return;
        ffxCreateBackendDX12Desc backend{};backend.header.type=FFX_API_CREATE_CONTEXT_DESC_TYPE_BACKEND_DX12;backend.device=device;
        ffxCreateContextDescDenoiser p{};p.header.type=FFX_API_CREATE_CONTEXT_DESC_TYPE_DENOISER;p.header.pNext=&backend.header;
        p.version=FFX_DENOISER_VERSION;p.maxRenderSize={w,h};
        p.signalFlags=FFX_DENOISER_SIGNAL_DIRECT_DIFFUSE|FFX_DENOISER_SIGNAL_DIRECT_SPECULAR|FFX_DENOISER_SIGNAL_INDIRECT_DIFFUSE|FFX_DENOISER_SIGNAL_INDIRECT_SPECULAR;
        enabled=create(&context,&p.header,nullptr)==FFX_API_RETURN_OK;
        if(!enabled) { release();supported=false; }
#endif
    }
    void execute(ID3D12GraphicsCommandList* list,ID3D12Resource* const* textures,const float* camera,const float* previous,UINT w,UINT h,UINT frame,float jx,float jy,bool reset) {
        (void)list;(void)textures;(void)camera;(void)previous;(void)w;(void)h;(void)frame;(void)jx;(void)jy;(void)reset;
#ifdef PT_FSR
        if(!enabled) return;
        ffxDispatchDescDenoiser p{};p.header.type=FFX_API_DISPATCH_DESC_TYPE_DENOISER;p.commandList=list;
        p.linearDepth=ffxApiGetResourceDX12(textures[18]);p.motionVectors=ffxApiGetResourceDX12(textures[19]);p.normals=ffxApiGetResourceDX12(textures[20]);
        p.diffuseAlbedo=ffxApiGetResourceDX12(textures[15]);p.specularAlbedo=ffxApiGetResourceDX12(textures[16]);
        p.motionVectorScale={1,1,1};p.jitterOffsets={-jx,-jy};
        p.cameraPositionDelta={previous[0]-camera[0],previous[1]-camera[1],previous[2]-camera[2]};
        float view[16],projection[16];cameraMatrices(camera,float(w)/h,view,projection);
        std::memcpy(&p.view,view,sizeof(view));std::memcpy(&p.projection,projection,sizeof(projection));
        p.linearDepthBounds={.1f,1000.f};p.renderSize={w,h};p.frameIndex=frame;
        p.flags=FFX_DENOISER_DISPATCH_NON_GAMMA_ALBEDO|(reset?FFX_DENOISER_DISPATCH_RESET:0);
        ffxDispatchDescDenoiserDirectDiffuse dd{};dd.header.type=FFX_API_DISPATCH_DESC_TYPE_DENOISER_DIRECT_DIFFUSE;
        ffxDispatchDescDenoiserDirectSpecular ds{};ds.header.type=FFX_API_DISPATCH_DESC_TYPE_DENOISER_DIRECT_SPECULAR;
        ffxDispatchDescDenoiserIndirectDiffuse id{};id.header.type=FFX_API_DISPATCH_DESC_TYPE_DENOISER_INDIRECT_DIFFUSE;
        ffxDispatchDescDenoiserIndirectSpecular is{};is.header.type=FFX_API_DISPATCH_DESC_TYPE_DENOISER_INDIRECT_SPECULAR;
        dd.signal.input=ffxApiGetResourceDX12(textures[21]);dd.signal.output=ffxApiGetResourceDX12(textures[25],FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
        ds.signal.input=ffxApiGetResourceDX12(textures[22]);ds.signal.output=ffxApiGetResourceDX12(textures[26],FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
        id.signal.input=ffxApiGetResourceDX12(textures[23]);id.signal.output=ffxApiGetResourceDX12(textures[27],FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
        is.signal.input=ffxApiGetResourceDX12(textures[24]);is.signal.output=ffxApiGetResourceDX12(textures[28],FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
        p.header.pNext=&dd.header;dd.header.pNext=&ds.header;ds.header.pNext=&id.header;id.header.pNext=&is.header;
        if(dispatch(&context,&p.header)!=FFX_API_RETURN_OK) throw std::runtime_error("FSR Ray Regeneration dispatch failed");
#endif
    }
    void release() {
#ifdef PT_FSR
        if(context&&destroyContext) destroyContext(&context,nullptr);context=nullptr;
#endif
        enabled=false;
    }
    void destroy() { release();
#ifdef PT_FSR
        if(module) FreeLibrary(module);module=nullptr;
#endif
    }
    ~RayRegeneration() { destroy(); }
};
