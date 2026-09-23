#pragma once
#include <windows.h>
#include <d3d12.h>
#include <string>
#include <stdexcept>
#include <vector>
#include <filesystem>
#ifdef PT_FSR
#include <ffx_api.h>
#include <dx12/ffx_api_dx12.h>
#include <ffx_upscale.h>
#endif
#ifdef PT_XESS
#include <xess/xess_d3d12.h>
#endif

// Optional SDKs are loaded only from an explicit absolute path. No DLL-name search.
class Upscaler {
    HMODULE library=nullptr;
#ifdef PT_FSR
    ffxContext fsr=nullptr;
    PfnFfxCreateContext createFsr=nullptr;
    PfnFfxDestroyContext destroyFsr=nullptr;
    PfnFfxQuery queryFsr=nullptr;
    PfnFfxDispatch dispatchFsr=nullptr;
    ffxCreateContextDescUpscale fsrDesc{};
    ffxCreateContextDescUpscaleVersion fsrVersion{};
    ffxCreateBackendDX12Desc fsrBackend{};
    ffxOverrideVersion fsrOverride{};
#endif
#ifdef PT_XESS
    xess_context_handle_t xess=nullptr;
    decltype(&xessD3D12CreateContext) createXess=nullptr;
    decltype(&xessD3D12Init) initXess=nullptr;
    decltype(&xessD3D12Execute) executeXess=nullptr;
    decltype(&xessDestroyContext) destroyXess=nullptr;
    decltype(&xessGetInputResolution) inputXess=nullptr;
#endif
    template<class T> T symbol(const char* name) {
        auto p=GetProcAddress(library,name);
        if(!p) throw std::runtime_error(std::string("Missing SDK export: ")+name);
        return reinterpret_cast<T>(p);
    }
public:
    std::string name="Off";
    unsigned width=0,height=0;
    Upscaler()=default;
    Upscaler(const Upscaler&)=delete;
    Upscaler& operator=(const Upscaler&)=delete;
    ~Upscaler() { close(); }
    void close() {
#ifdef PT_FSR
        if(fsr && destroyFsr) destroyFsr(&fsr,nullptr);
        fsr=nullptr;
#endif
#ifdef PT_XESS
        if(xess && destroyXess) destroyXess(xess);
        xess=nullptr;
#endif
        if(library) FreeLibrary(library);
        library=nullptr;name="Off";
    }
    void init(ID3D12Device* device,const std::string& mode,const std::wstring& path,unsigned w,unsigned h) {
        close();width=w;height=h;
        if(mode=="off") return;
        if(!std::filesystem::path(path).is_absolute()) throw std::runtime_error("SDK DLL path must be absolute");
        library=LoadLibraryExW(path.c_str(),nullptr,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_SYSTEM32);
        if(!library) throw std::runtime_error("Cannot load upscaler DLL or one of its dependencies");
#ifdef PT_FSR
        if(mode=="fsr41") {
            createFsr=symbol<PfnFfxCreateContext>("ffxCreateContext");
            destroyFsr=symbol<PfnFfxDestroyContext>("ffxDestroyContext");
            queryFsr=symbol<PfnFfxQuery>("ffxQuery");dispatchFsr=symbol<PfnFfxDispatch>("ffxDispatch");
            uint64_t count=0;
            ffxQueryDescGetVersions versions{};versions.header.type=FFX_API_QUERY_DESC_TYPE_GET_VERSIONS;
            versions.createDescType=FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE;versions.device=device;versions.outputCount=&count;
            if(queryFsr(nullptr,&versions.header)!=FFX_API_RETURN_OK || count==0 || count>128)
                throw std::runtime_error("FSR provider enumeration failed");
            std::vector<uint64_t> ids(count);std::vector<const char*> names(count);
            versions.versionIds=ids.data();versions.versionNames=names.data();
            if(queryFsr(nullptr,&versions.header)!=FFX_API_RETURN_OK || count>ids.size())
                throw std::runtime_error("FSR provider enumeration failed");
            bool found=false;
            for(size_t i=0;i<count;i++) if(names[i] && std::string(names[i]).find("4.1.")!=std::string::npos) {
                fsrOverride={};fsrOverride.header.type=FFX_API_DESC_TYPE_OVERRIDE_VERSION;
                fsrOverride.versionId=ids[i];name=names[i];found=true;break;
            }
            if(!found) throw std::runtime_error("No supported FSR 4.1 provider; refusing silent FSR 3 fallback");
            width=std::max(1u,w*2/3);height=std::max(1u,h*2/3);
            fsrDesc={};fsrDesc.header.type=FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE;
            fsrDesc.flags=FFX_UPSCALE_ENABLE_HIGH_DYNAMIC_RANGE|FFX_UPSCALE_ENABLE_AUTO_EXPOSURE;
            fsrDesc.maxRenderSize={width,height};fsrDesc.maxUpscaleSize={w,h};
            fsrVersion={};fsrVersion.header.type=FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE_VERSION;
            fsrVersion.version=FFX_UPSCALER_VERSION;
            fsrBackend={};fsrBackend.header.type=FFX_API_CREATE_CONTEXT_DESC_TYPE_BACKEND_DX12;fsrBackend.device=device;
            fsrDesc.header.pNext=&fsrVersion.header;fsrVersion.header.pNext=&fsrBackend.header;
            fsrBackend.header.pNext=&fsrOverride.header;
            if(createFsr(&fsr,&fsrDesc.header,nullptr)!=FFX_API_RETURN_OK) throw std::runtime_error("FSR 4.1 context creation failed");
            return;
        }
#endif
#ifdef PT_XESS
        if(mode=="xess") {
            createXess=symbol<decltype(createXess)>("xessD3D12CreateContext");
            destroyXess=symbol<decltype(destroyXess)>("xessDestroyContext");
            initXess=symbol<decltype(initXess)>("xessD3D12Init");
            executeXess=symbol<decltype(executeXess)>("xessD3D12Execute");
            inputXess=symbol<decltype(inputXess)>("xessGetInputResolution");
            if(createXess(device,&xess)!=XESS_RESULT_SUCCESS) throw std::runtime_error("XeSS context creation failed");
            xess_d3d12_init_params_t desc{};desc.outputResolution={w,h};
            desc.qualitySetting=XESS_QUALITY_SETTING_QUALITY;desc.initFlags=XESS_INIT_FLAG_ENABLE_AUTOEXPOSURE;
            if(initXess(xess,&desc)!=XESS_RESULT_SUCCESS) throw std::runtime_error("XeSS initialization failed");
            xess_2d_t input{};
            if(inputXess(xess,&desc.outputResolution,desc.qualitySetting,&input)!=XESS_RESULT_SUCCESS)
                throw std::runtime_error("XeSS input resolution query failed");
            width=input.x;height=input.y;
            xess_version_t version{};
            auto getVersion=symbol<decltype(&xessGetVersion)>("xessGetVersion");
            if(getVersion(&version)!=XESS_RESULT_SUCCESS) throw std::runtime_error("XeSS version query failed");
            name="XeSS-SR "+std::to_string(version.major)+"."+std::to_string(version.minor)+"."+std::to_string(version.patch);
            return;
        }
#endif
        throw std::runtime_error("Requested upscaler was not compiled in, or is unknown");
    }
    void dispatch(ID3D12GraphicsCommandList* cmd,ID3D12Resource* color,ID3D12Resource* depth,
        ID3D12Resource* motion,ID3D12Resource* output,float jx,float jy,float milliseconds,bool reset) {
#ifdef PT_FSR
        if(fsr) {
            ffxDispatchDescUpscale d{};d.header.type=FFX_API_DISPATCH_DESC_TYPE_UPSCALE;d.commandList=cmd;
            d.color=ffxApiGetResourceDX12(color);d.depth=ffxApiGetResourceDX12(depth);d.motionVectors=ffxApiGetResourceDX12(motion);
            d.output=ffxApiGetResourceDX12(output,FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
            d.jitterOffset={jx,jy};d.motionVectorScale={1,1};d.renderSize={width,height};
            d.frameTimeDelta=milliseconds;d.preExposure=1;d.reset=reset;
            d.cameraNear=.1f;d.cameraFar=1000;d.cameraFovAngleVertical=1.0471975512f;d.viewSpaceToMetersFactor=1;
            if(dispatchFsr(&fsr,&d.header)!=FFX_API_RETURN_OK) throw std::runtime_error("FSR dispatch failed");
            return;
        }
#endif
#ifdef PT_XESS
        if(xess) {
            xess_d3d12_execute_params_t d{};d.pColorTexture=color;d.pDepthTexture=depth;
            d.pVelocityTexture=motion;d.pOutputTexture=output;
            // XeSS specifies camera displacement; the ray sample displacement has the opposite sign.
            d.jitterOffsetX=-jx;d.jitterOffsetY=-jy;d.exposureScale=1;d.resetHistory=reset;
            d.inputWidth=width;d.inputHeight=height;
            if(executeXess(xess,cmd,&d)!=XESS_RESULT_SUCCESS) throw std::runtime_error("XeSS dispatch failed");
            return;
        }
#endif
        throw std::runtime_error("Upscaler is not initialized");
    }
};
