#pragma once
#include "vendor_effects.h"
#include <dxgi1_6.h>
#ifdef PT_XESS
#include <xess_fg/xefg_swapchain_d3d12.h>
#include <xell/xell_d3d12.h>
#endif

/** Optional XeSS 3 MFG swapchain. Application uploads are fenced once per real frame. */
class FrameGeneration {
    UINT maximum=1,multiplier=1,frameId=0,framesPresented=1;
    bool failed=false;
#ifdef PT_XESS
    HMODULE fgModule=nullptr,llModule=nullptr;
    xefg_swapchain_handle_t fg=nullptr;
    xell_context_handle_t ll=nullptr;
#define FG_FN(name) decltype(&name) p_##name=nullptr
    FG_FN(xefgSwapChainD3D12CreateContext);FG_FN(xefgSwapChainD3D12InitFromSwapChainDesc);
    FG_FN(xefgSwapChainD3D12GetSwapChainPtr);FG_FN(xefgSwapChainD3D12TagFrameResource);
    FG_FN(xefgSwapChainGetProperties);FG_FN(xefgSwapChainSetEnabled);FG_FN(xefgSwapChainSetNumInterpolatedFrames);
    FG_FN(xefgSwapChainSetUiCompositionState);FG_FN(xefgSwapChainSetLatencyReduction);
    FG_FN(xefgSwapChainTagFrameConstants);FG_FN(xefgSwapChainSetPresentId);
    FG_FN(xefgSwapChainGetLastPresentStatus);FG_FN(xefgSwapChainDestroy);
    FG_FN(xellD3D12CreateContext);FG_FN(xellDestroyContext);FG_FN(xellSetSleepMode);
    FG_FN(xellSleep);FG_FN(xellAddMarkerData);
#undef FG_FN
    static void check(int result,const char* operation) {
        if(result<0) throw std::runtime_error(std::string(operation)+" failed ("+std::to_string(result)+")");
    }
#endif
public:
    FrameGeneration()=default;
    FrameGeneration(const FrameGeneration&)=delete;
    FrameGeneration& operator=(const FrameGeneration&)=delete;
    ~FrameGeneration() { destroy(); }
    UINT maxMultiplier() const { return maximum; }
    std::string status() const {
        if(failed) return "XeSS-FG disabled after SDK error";
        if(maximum==1) return "FG unavailable";
        return multiplier>1?"XeSS-FG "+std::to_string(multiplier)+"x requested / "+std::to_string(framesPresented)+" presented":"XeSS-FG off";
    }
    bool attach(ID3D12Device* device,ID3D12CommandQueue* queue,IDXGIFactory2* factory,HWND window,const DXGI_SWAP_CHAIN_DESC1& desc,IDXGISwapChain4** output) {
        (void)device;(void)queue;(void)factory;(void)window;(void)desc;(void)output;
#ifdef PT_XESS
        try {
            llModule=loadSibling(L"libxell.dll");fgModule=loadSibling(L"libxess_fg.dll");
            if(!fgModule||!llModule) { destroy();return false; }
#define LOAD_FG(name) p_##name=symbol<decltype(p_##name)>(fgModule,#name);if(!p_##name) { destroy();return false; }
            LOAD_FG(xefgSwapChainD3D12CreateContext);LOAD_FG(xefgSwapChainD3D12InitFromSwapChainDesc);
            LOAD_FG(xefgSwapChainD3D12GetSwapChainPtr);LOAD_FG(xefgSwapChainD3D12TagFrameResource);
            LOAD_FG(xefgSwapChainGetProperties);LOAD_FG(xefgSwapChainSetEnabled);LOAD_FG(xefgSwapChainSetNumInterpolatedFrames);
            LOAD_FG(xefgSwapChainSetUiCompositionState);LOAD_FG(xefgSwapChainSetLatencyReduction);
            LOAD_FG(xefgSwapChainTagFrameConstants);LOAD_FG(xefgSwapChainSetPresentId);
            LOAD_FG(xefgSwapChainGetLastPresentStatus);LOAD_FG(xefgSwapChainDestroy);
#undef LOAD_FG
#define LOAD_LL(name) p_##name=symbol<decltype(p_##name)>(llModule,#name);if(!p_##name) { destroy();return false; }
            LOAD_LL(xellD3D12CreateContext);LOAD_LL(xellDestroyContext);LOAD_LL(xellSetSleepMode);LOAD_LL(xellSleep);LOAD_LL(xellAddMarkerData);
#undef LOAD_LL
            check(p_xellD3D12CreateContext(device,&ll),"XeLL create");
            xell_sleep_params_t sleep{};sleep.bLowLatencyMode=1;check(p_xellSetSleepMode(ll,&sleep),"XeLL sleep mode");
            check(p_xefgSwapChainD3D12CreateContext(device,&fg),"XeSS-FG create");
            check(p_xefgSwapChainSetLatencyReduction(fg,ll),"XeSS-FG latency context");
            xefg_swapchain_properties_t props{};check(p_xefgSwapChainGetProperties(fg,&props),"XeSS-FG properties");
            if(!props.maxSupportedInterpolations) { destroy();return false; }
            xefg_swapchain_d3d12_init_params_t init{};init.maxInterpolatedFrames=std::min(3u,props.maxSupportedInterpolations);
            init.uiMode=XEFG_SWAPCHAIN_UI_MODE_BACKBUFFER_HUDLESS;
            check(p_xefgSwapChainD3D12InitFromSwapChainDesc(fg,window,&desc,nullptr,queue,factory,&init),"XeSS-FG initialize");
            check(p_xefgSwapChainSetUiCompositionState(fg,XEFG_SWAPCHAIN_UI_COMPOSITION_STATE_ENABLED),"XeSS-FG UI");
            check(p_xefgSwapChainSetEnabled(fg,0),"XeSS-FG initially off");
            check(p_xefgSwapChainD3D12GetSwapChainPtr(fg,__uuidof(IDXGISwapChain4),reinterpret_cast<void**>(output)),"XeSS-FG swapchain");
            maximum=init.maxInterpolatedFrames+1;return true;
        } catch(const std::exception&) {
            if(*output) { (*output)->Release();*output=nullptr; }
            destroy();return false;
        }
#else
        return false;
#endif
    }
    void mode(UINT requested) {
        UINT next=std::clamp(requested,1u,maximum);if(next==multiplier) return;
#ifdef PT_XESS
        if(fg) {
            if(next>1) check(p_xefgSwapChainSetNumInterpolatedFrames(fg,next-1),"XeSS-FG multiplier");
            check(p_xefgSwapChainSetEnabled(fg,next>1),"XeSS-FG enable");
        }
#endif
        multiplier=next;
    }
    void marker(int type) {
        (void)type;
#ifdef PT_XESS
        if(ll) check(p_xellAddMarkerData(ll,frameId,static_cast<xell_latency_marker_type_t>(type)),"XeLL marker");
#endif
    }
    void begin() {
        if(++frameId==0) ++frameId;
#ifdef PT_XESS
        if(ll) check(p_xellSleep(ll,frameId),"XeLL sleep");
#endif
        marker(0);
    }
    void tag(ID3D12GraphicsCommandList* list,ID3D12Resource* hudless,ID3D12Resource* depth,ID3D12Resource* motion,
            const float* camera,UINT rw,UINT rh,float jx,float jy,float milliseconds,bool reset) {
        (void)list;(void)hudless;(void)depth;(void)motion;(void)camera;(void)rw;(void)rh;(void)jx;(void)jy;(void)milliseconds;(void)reset;
#ifdef PT_XESS
        if(!fg) return;
        if(multiplier>1) {
            xefg_swapchain_frame_constant_data_t c{};
            for(int axis=0;axis<3;axis++) {
                c.viewMatrix[axis*4]=camera[8+axis];c.viewMatrix[axis*4+1]=camera[12+axis];c.viewMatrix[axis*4+2]=camera[4+axis];
                c.viewMatrix[12]-=camera[axis]*camera[8+axis];
                c.viewMatrix[13]-=camera[axis]*camera[12+axis];c.viewMatrix[14]-=camera[axis]*camera[4+axis];
            }
            c.viewMatrix[15]=1;c.projectionMatrix[0]=1.7320508f*rh/rw;c.projectionMatrix[5]=1.7320508f;
            c.projectionMatrix[10]=1000.f/999.9f;c.projectionMatrix[11]=1;c.projectionMatrix[14]=-100.f/999.9f;
            c.jitterOffsetX=-jx;c.jitterOffsetY=-jy;c.motionVectorScaleX=c.motionVectorScaleY=1;c.resetHistory=reset;c.frameRenderTime=milliseconds;
            check(p_xefgSwapChainTagFrameConstants(fg,frameId,&c),"XeSS-FG constants");
            ID3D12Resource* resources[]={hudless,depth,motion};
            const xefg_swapchain_resource_type_t types[]={XEFG_SWAPCHAIN_RES_HUDLESS_COLOR,XEFG_SWAPCHAIN_RES_DEPTH,XEFG_SWAPCHAIN_RES_MOTION_VECTOR};
            for(int i=0;i<3;i++) {
                xefg_swapchain_d3d12_resource_data_t r{};r.type=types[i];r.validity=XEFG_SWAPCHAIN_RV_ONLY_NOW;
                auto d=resources[i]->GetDesc();r.resourceSize={UINT(d.Width),d.Height};r.pResource=resources[i];r.incomingState=D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE;
                check(p_xefgSwapChainD3D12TagFrameResource(fg,list,frameId,&r),"XeSS-FG resource");
            }
        }
        check(p_xefgSwapChainSetPresentId(fg,frameId),"XeSS-FG present ID");
#endif
    }
    // Call after Present AND the application fence; never destroy SDK-owned resources mid-submit.
    void presented() {
#ifdef PT_XESS
        if(fg) {
            xefg_swapchain_present_status_t s{};
            int result=p_xefgSwapChainGetLastPresentStatus(fg,&s);framesPresented=s.framesPresented;
            if(result<0||s.frameGenResult<0) {
                check(p_xefgSwapChainSetEnabled(fg,0),"Disable failed XeSS-FG");maximum=multiplier=1;failed=true;
            }
        }
#endif
    }
    void destroy() {
#ifdef PT_XESS
        if(fg&&p_xefgSwapChainDestroy) p_xefgSwapChainDestroy(fg);fg=nullptr;
        if(ll&&p_xellDestroyContext) p_xellDestroyContext(ll);ll=nullptr;
        if(fgModule) FreeLibrary(fgModule);fgModule=nullptr;
        if(llModule) FreeLibrary(llModule);llModule=nullptr;
#endif
        maximum=multiplier=1;
    }
};
