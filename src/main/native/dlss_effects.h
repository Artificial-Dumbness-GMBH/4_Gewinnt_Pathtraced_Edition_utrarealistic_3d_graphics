#pragma once
#include "camera_matrices.h"
#ifdef PT_DLSS
#include <nvsdk_ngx_helpers.h>
#include <nvsdk_ngx_helpers_dlssd_d3d.h>
#endif
class DlssEffects {
    bool supported=false,rrSupported=false,enabled=false,reconstruct=false,pending=false;
#ifdef PT_DLSS
    ID3D12Device* device=nullptr;
    NVSDK_NGX_Parameter* parameters=nullptr;
    NVSDK_NGX_Handle* handle=nullptr;
    HMODULE srModule=nullptr,rrModule=nullptr;
    bool initialized=false;
    UINT inputWidth=0,inputHeight=0,outputWidth=0,outputHeight=0;
    NVSDK_NGX_PerfQuality_Value mode=NVSDK_NGX_PerfQuality_Value_MaxQuality;
    static void require(NVSDK_NGX_Result r,const char* message) {
        if(NVSDK_NGX_FAILED(r)) throw std::runtime_error(std::string(message)+" (NGX "+std::to_string(unsigned(r))+")");
    }
#endif
public:
    bool available() const { return supported; }
    bool rrAvailable() const { return rrSupported; }
    bool active() const { return enabled; }
    bool rrActive() const { return enabled&&reconstruct; }
    void init(ID3D12Device* d) {
        (void)d;
#ifdef PT_DLSS
        device=d;srModule=loadSibling(L"nvngx_dlss.dll");rrModule=loadSibling(L"nvngx_dlssd.dll");
        if(!srModule) return;
        HMODULE self=nullptr;GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,reinterpret_cast<LPCWSTR>(&loadSibling),&self);
        wchar_t file[32768];DWORD n=GetModuleFileNameW(self,file,32768);if(n==0||n>=32768) return;
        std::wstring directory(file,n);directory.resize(directory.find_last_of(L"\\/"));
        const wchar_t* paths[]={directory.c_str()};NVSDK_NGX_FeatureCommonInfo common{};common.PathListInfo={paths,1};
        wchar_t temp[MAX_PATH];DWORD len=GetTempPathW(MAX_PATH,temp);if(len==0||len>=MAX_PATH) return;
        std::wstring logs=std::wstring(temp)+L"viergewinnt-ngx";CreateDirectoryW(logs.c_str(),nullptr);
        initialized=NVSDK_NGX_SUCCEED(NVSDK_NGX_D3D12_Init_with_ProjectID("2c544d74-f02e-4b29-b598-628852932270",NVSDK_NGX_ENGINE_TYPE_CUSTOM,"1.0",logs.c_str(),d,&common));
        if(!initialized||NVSDK_NGX_FAILED(NVSDK_NGX_D3D12_GetCapabilityParameters(&parameters))) return;
        int sr=0,rr=0;parameters->Get(NVSDK_NGX_Parameter_SuperSampling_Available,&sr);
        parameters->Get(NVSDK_NGX_Parameter_SuperSamplingDenoising_Available,&rr);
        supported=sr!=0;rrSupported=rrModule&&rr!=0;
#endif
    }
    void release() {
#ifdef PT_DLSS
        if(handle) NVSDK_NGX_D3D12_ReleaseFeature(handle);handle=nullptr;
#endif
        enabled=pending=false;
    }
    bool configure(bool rr,int quality,float scale,UINT w,UINT h,UINT& rw,UINT& rh) {
        release();(void)rr;(void)quality;(void)scale;(void)w;(void)h;(void)rw;(void)rh;
#ifdef PT_DLSS
        if(!supported||(rr&&!rrSupported)) return false;
        reconstruct=rr;
        const NVSDK_NGX_PerfQuality_Value modes[]={NVSDK_NGX_PerfQuality_Value_DLAA,NVSDK_NGX_PerfQuality_Value_MaxQuality,NVSDK_NGX_PerfQuality_Value_Balanced,NVSDK_NGX_PerfQuality_Value_MaxPerf};
        mode=modes[quality];UINT optimalW=0,optimalH=0,maxW=0,maxH=0,minW=0,minH=0;float sharp=0;
        auto r=rr?NGX_DLSSD_GET_OPTIMAL_SETTINGS(parameters,w,h,mode,&optimalW,&optimalH,&maxW,&maxH,&minW,&minH,&sharp)
            :NGX_DLSS_GET_OPTIMAL_SETTINGS(parameters,w,h,mode,&optimalW,&optimalH,&maxW,&maxH,&minW,&minH,&sharp);
        if(NVSDK_NGX_FAILED(r)||!optimalW||!optimalH||!maxW||!maxH) return false;
        rw=optimalW;rh=optimalH;
        if(scale>0) {
            float lo=std::max(float(minW)/w,float(minH)/h),hi=std::min(float(maxW)/w,float(maxH)/h);
            if(lo>hi) return false;float s=std::clamp(scale,lo,hi);
            rw=std::max(1u,std::clamp(UINT(w*s),minW,maxW));rh=std::max(1u,std::clamp(UINT(h*s),minH,maxH));
        }
        inputWidth=rw;inputHeight=rh;outputWidth=w;outputHeight=h;enabled=pending=true;return true;
#else
        return false;
#endif
    }
    void execute(ID3D12GraphicsCommandList* list,ID3D12Resource* color,ID3D12Resource* depth,ID3D12Resource* motion,ID3D12Resource* output,
            ID3D12Resource* normals,ID3D12Resource* diffuse,ID3D12Resource* specular,const float* camera,UINT rw,UINT rh,float jx,float jy,float ms,bool reset) {
        (void)list;(void)color;(void)depth;(void)motion;(void)output;(void)normals;(void)diffuse;(void)specular;(void)camera;(void)rw;(void)rh;(void)jx;(void)jy;(void)ms;(void)reset;
#ifdef PT_DLSS
        if(!enabled) return;
        if(pending) {
            const int flags=NVSDK_NGX_DLSS_Feature_Flags_IsHDR|NVSDK_NGX_DLSS_Feature_Flags_MVLowRes|NVSDK_NGX_DLSS_Feature_Flags_AutoExposure;
            if(reconstruct) {
                NVSDK_NGX_DLSSD_Create_Params p{};p.InWidth=inputWidth;p.InHeight=inputHeight;p.InTargetWidth=outputWidth;p.InTargetHeight=outputHeight;
                p.InPerfQualityValue=mode;p.InFeatureCreateFlags=flags;p.InDenoiseMode=NVSDK_NGX_DLSS_Denoise_Mode_DLUnified;
                p.InRoughnessMode=NVSDK_NGX_DLSS_Roughness_Mode_Packed;p.InUseHWDepth=NVSDK_NGX_DLSS_Depth_Type_HW;
                require(NGX_D3D12_CREATE_DLSSD_EXT(list,1,1,&handle,parameters,&p),"DLSS RR create failed");
            } else {
                NVSDK_NGX_DLSS_Create_Params p{};p.Feature.InWidth=inputWidth;p.Feature.InHeight=inputHeight;p.Feature.InTargetWidth=outputWidth;p.Feature.InTargetHeight=outputHeight;
                p.Feature.InPerfQualityValue=mode;p.InFeatureCreateFlags=flags;
                require(NGX_D3D12_CREATE_DLSS_EXT(list,1,1,&handle,parameters,&p),"DLSS create failed");
            }
            pending=false;
        }
        if(reconstruct) {
            float view[16],projection[16];cameraMatrices(camera,float(rw)/rh,view,projection);
            NVSDK_NGX_D3D12_DLSSD_Eval_Params p{};p.pInColor=color;p.pInDepth=depth;p.pInMotionVectors=motion;p.pInOutput=output;
            p.pInDiffuseAlbedo=diffuse;p.pInSpecularAlbedo=specular;p.pInNormals=normals;p.pInWorldToViewMatrix=view;p.pInViewToClipMatrix=projection;
            p.InRenderSubrectDimensions={rw,rh};p.InJitterOffsetX=-jx;p.InJitterOffsetY=-jy;p.InMVScaleX=p.InMVScaleY=1;
            p.InReset=reset;p.InPreExposure=p.InExposureScale=1;p.InFrameTimeDeltaInMsec=ms;
            require(NGX_D3D12_EVALUATE_DLSSD_EXT(list,handle,parameters,&p),"DLSS RR evaluate failed");
        } else {
            NVSDK_NGX_D3D12_DLSS_Eval_Params p{};p.Feature.pInColor=color;p.Feature.pInOutput=output;p.pInDepth=depth;p.pInMotionVectors=motion;
            p.InRenderSubrectDimensions={rw,rh};p.InJitterOffsetX=-jx;p.InJitterOffsetY=-jy;p.InMVScaleX=p.InMVScaleY=1;
            p.InReset=reset;p.InPreExposure=p.InExposureScale=1;p.InFrameTimeDeltaInMsec=ms;
            require(NGX_D3D12_EVALUATE_DLSS_EXT(list,handle,parameters,&p),"DLSS evaluate failed");
        }
#endif
    }
    void destroy() {
        release();
#ifdef PT_DLSS
        if(parameters) NVSDK_NGX_D3D12_DestroyParameters(parameters);parameters=nullptr;
        if(initialized) NVSDK_NGX_D3D12_Shutdown1(device);initialized=false;
        if(srModule) FreeLibrary(srModule);srModule=nullptr;if(rrModule) FreeLibrary(rrModule);rrModule=nullptr;
#endif
    }
    ~DlssEffects() { destroy(); }
};
