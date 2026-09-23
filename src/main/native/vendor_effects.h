#pragma once
#include <windows.h>
#include <d3d12.h>
#include <algorithm>
#include <string>
#include <stdexcept>
#ifdef PT_FSR
#include <ffx_upscale.h>
#include <dx12/ffx_api_dx12.h>
#endif
#ifdef PT_XESS
#include <xess/xess_d3d12.h>
#endif

// Only load SDK binaries next to this bridge; never search the current directory.
inline HMODULE loadSibling(const wchar_t* name) {
    HMODULE self=nullptr;
    if(!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            reinterpret_cast<LPCWSTR>(&loadSibling),&self)) return nullptr;
    wchar_t path[32768];DWORD n=GetModuleFileNameW(self,path,32768);
    if(n==0||n>=32768) return nullptr;
    std::wstring full(path,n);auto separator=full.find_last_of(L"\\/");
    if(separator==std::wstring::npos) return nullptr;
    full.resize(separator+1);full+=name;
    return LoadLibraryExW(full.c_str(),nullptr,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
}
template<class T> inline T symbol(HMODULE dll,const char* name) { return dll?reinterpret_cast<T>(GetProcAddress(dll,name)):nullptr; }
class VendorEffects {
    int selected=0;
    bool fsrSupported=false,xessSupported=false;
    std::string description="Upscaling off";
#ifdef PT_FSR
    HMODULE fsrModule=nullptr;
    ffxContext fsrContext=nullptr;
    PfnFfxCreateContext createFsr=nullptr;
    PfnFfxDestroyContext destroyFsr=nullptr;
    PfnFfxQuery queryFsr=nullptr;
    PfnFfxDispatch dispatchFsr=nullptr;
#endif
#ifdef PT_XESS
    HMODULE xessModule=nullptr;
    xess_context_handle_t xessContext=nullptr;
    decltype(&xessD3D12CreateContext) createXess=nullptr;
    decltype(&xessD3D12Init) initXess=nullptr;
    decltype(&xessD3D12Execute) executeXess=nullptr;
    decltype(&xessDestroyContext) destroyXess=nullptr;
    decltype(&xessGetInputResolution) inputXess=nullptr;
#endif
    void releaseContexts() {
#ifdef PT_FSR
        if(fsrContext&&destroyFsr) destroyFsr(&fsrContext,nullptr);fsrContext=nullptr;
#endif
#ifdef PT_XESS
        if(xessContext&&destroyXess) destroyXess(xessContext);xessContext=nullptr;
#endif
        selected=0;
    }
public:
    VendorEffects()=default;
    VendorEffects(const VendorEffects&)=delete;
    VendorEffects& operator=(const VendorEffects&)=delete;
    ~VendorEffects() { destroy(); }
    void init(ID3D12Device* device) {
        (void)device;
#ifdef PT_FSR
        fsrModule=loadSibling(L"amd_fidelityfx_upscaler_dx12.dll");
        createFsr=symbol<PfnFfxCreateContext>(fsrModule,"ffxCreateContext");
        destroyFsr=symbol<PfnFfxDestroyContext>(fsrModule,"ffxDestroyContext");
        queryFsr=symbol<PfnFfxQuery>(fsrModule,"ffxQuery");dispatchFsr=symbol<PfnFfxDispatch>(fsrModule,"ffxDispatch");
        if(createFsr&&destroyFsr&&queryFsr&&dispatchFsr) {
            uint64_t count=0;ffxQueryDescGetVersions q{};q.header.type=FFX_API_QUERY_DESC_TYPE_GET_VERSIONS;
            q.createDescType=FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE;q.device=device;q.outputCount=&count;
            fsrSupported=queryFsr(nullptr,&q.header)==FFX_API_RETURN_OK&&count>0;
        }
#endif
#ifdef PT_XESS
        xessModule=loadSibling(L"libxess.dll");
        createXess=symbol<decltype(createXess)>(xessModule,"xessD3D12CreateContext");
        initXess=symbol<decltype(initXess)>(xessModule,"xessD3D12Init");
        executeXess=symbol<decltype(executeXess)>(xessModule,"xessD3D12Execute");
        destroyXess=symbol<decltype(destroyXess)>(xessModule,"xessDestroyContext");
        inputXess=symbol<decltype(inputXess)>(xessModule,"xessGetInputResolution");
        if(createXess&&initXess&&executeXess&&destroyXess&&inputXess) {
            xessSupported=createXess(device,&xessContext)>=XESS_RESULT_SUCCESS;
            if(xessContext) destroyXess(xessContext);xessContext=nullptr;
        }
#endif
    }
    bool fsrAvailable() const { return fsrSupported; }
    bool xessAvailable() const { return xessSupported; }
    bool active() const { return selected!=0; }
    const std::string& status() const { return description; }
    void configure(ID3D12Device* device,int requested,int quality,UINT width,UINT height,UINT& rw,UINT& rh) {
        releaseContexts();description=requested?"Requested upscaler unavailable; native fallback":"Upscaling off";
        (void)device;(void)quality;(void)width;(void)height;(void)rw;(void)rh;
#ifdef PT_FSR
        if(requested==1&&fsrSupported) {
            const float ratios[]={1,1.5f,1.7f,2};
            UINT fw=std::max(1u,UINT(width/ratios[quality])),fh=std::max(1u,UINT(height/ratios[quality]));
            ffxCreateBackendDX12Desc backend{};backend.header.type=FFX_API_CREATE_CONTEXT_DESC_TYPE_BACKEND_DX12;backend.device=device;
            ffxCreateContextDescUpscaleVersion version{};version.header.type=FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE_VERSION;version.header.pNext=&backend.header;version.version=FFX_UPSCALER_VERSION;
            ffxCreateContextDescUpscale desc{};desc.header.type=FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE;desc.header.pNext=&version.header;
            desc.flags=FFX_UPSCALE_ENABLE_HIGH_DYNAMIC_RANGE|FFX_UPSCALE_ENABLE_AUTO_EXPOSURE;desc.maxRenderSize={fw,fh};desc.maxUpscaleSize={width,height};
            if(createFsr(&fsrContext,&desc.header,nullptr)==FFX_API_RETURN_OK) {
                selected=1;rw=fw;rh=fh;
                ffxQueryGetProviderVersion q{};q.header.type=FFX_API_QUERY_DESC_TYPE_GET_PROVIDER_VERSION;
                description="FSR provider (version unknown)";
                if(queryFsr(&fsrContext,&q.header)==FFX_API_RETURN_OK&&q.versionName) description=q.versionName;
            } else { releaseContexts();fsrSupported=false;description="FSR initialization failed; native fallback"; }
        }
#endif
#ifdef PT_XESS
        if(requested==2&&xessSupported) {
            const xess_quality_settings_t qualities[]={XESS_QUALITY_SETTING_AA,XESS_QUALITY_SETTING_QUALITY,XESS_QUALITY_SETTING_BALANCED,XESS_QUALITY_SETTING_PERFORMANCE};
            xess_d3d12_init_params_t p{};p.outputResolution={width,height};p.qualitySetting=qualities[quality];p.initFlags=XESS_INIT_FLAG_ENABLE_AUTOEXPOSURE;
            xess_2d_t input{};
            if(createXess(device,&xessContext)>=XESS_RESULT_SUCCESS&&initXess(xessContext,&p)>=XESS_RESULT_SUCCESS
                &&inputXess(xessContext,&p.outputResolution,p.qualitySetting,&input)>=XESS_RESULT_SUCCESS&&input.x&&input.y) {
                selected=2;rw=input.x;rh=input.y;description="XeSS-SR (SDK 3.0.2)";
            } else { releaseContexts();xessSupported=false;description="XeSS initialization failed; native fallback"; }
        }
#endif
    }
    void execute(ID3D12GraphicsCommandList* list,ID3D12Resource* color,ID3D12Resource* depth,ID3D12Resource* motion,ID3D12Resource* output,
            UINT rw,UINT rh,UINT w,UINT h,float jx,float jy,float ms,bool reset) {
        (void)list;(void)color;(void)depth;(void)motion;(void)output;(void)rw;(void)rh;(void)w;(void)h;(void)jx;(void)jy;(void)ms;(void)reset;
#ifdef PT_FSR
        if(selected==1) {
            ffxDispatchDescUpscale p{};p.header.type=FFX_API_DISPATCH_DESC_TYPE_UPSCALE;p.commandList=list;
            p.color=ffxApiGetResourceDX12(color);p.depth=ffxApiGetResourceDX12(depth);p.motionVectors=ffxApiGetResourceDX12(motion);
            p.output=ffxApiGetResourceDX12(output,FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
            p.jitterOffset={-jx,-jy};p.motionVectorScale={1,1};p.renderSize={rw,rh};p.upscaleSize={w,h};
            p.frameTimeDelta=ms;p.preExposure=1;p.reset=reset;p.cameraNear=.1f;p.cameraFar=1000;p.cameraFovAngleVertical=1.04719755f;p.viewSpaceToMetersFactor=1;
            if(dispatchFsr(&fsrContext,&p.header)!=FFX_API_RETURN_OK) throw std::runtime_error("FSR dispatch failed; frame aborted");
        }
#endif
#ifdef PT_XESS
        if(selected==2) {
            xess_d3d12_execute_params_t p{};p.pColorTexture=color;p.pVelocityTexture=motion;p.pDepthTexture=depth;p.pOutputTexture=output;
            p.jitterOffsetX=-jx;p.jitterOffsetY=-jy;p.exposureScale=1;p.resetHistory=reset;p.inputWidth=rw;p.inputHeight=rh;
            if(executeXess(xessContext,list,&p)<XESS_RESULT_SUCCESS) throw std::runtime_error("XeSS dispatch failed; frame aborted");
        }
#endif
    }
    void destroy() {
        releaseContexts();
#ifdef PT_FSR
        if(fsrModule) FreeLibrary(fsrModule);fsrModule=nullptr;
#endif
#ifdef PT_XESS
        if(xessModule) FreeLibrary(xessModule);xessModule=nullptr;
#endif
    }
};
