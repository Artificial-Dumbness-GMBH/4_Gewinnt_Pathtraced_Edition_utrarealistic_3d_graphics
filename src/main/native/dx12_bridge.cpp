#define NOMINMAX
#include <windows.h>
#include <d3d12.h>
#include <d3dcompiler.h>
#include <d3d12sdklayers.h>
#include <dxgi1_6.h>
#include <jni.h>
#include <wrl.h>
#include <array>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <vector>
#include "upscaler.h"
#include "shaders.h"
using Microsoft::WRL::ComPtr;

static void check(HRESULT hr,const char* operation) {
    if(FAILED(hr)) { char code[16];snprintf(code,sizeof(code),"0x%08X",unsigned(hr));
        throw std::runtime_error(std::string(operation)+": "+code); }
}
static void fail(JNIEnv* env,const std::exception& e) {
    if(!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());
}
struct Texture {
    ComPtr<ID3D12Resource> resource;
    D3D12_RESOURCE_STATES state=D3D12_RESOURCE_STATE_UNORDERED_ACCESS;
};
struct alignas(16) Frame {
    float position[3];UINT frame;
    float forward[3];UINT sequence;
    float right[3];UINT samples;
    float up[3];UINT bounces;
    float prevPosition[3];UINT triangles;
    float prevForward[3];int movingStart;
    float prevRight[3];float lift;
    float prevUp[3];float prevLift;
    UINT width,height;float jx,jy;
    UINT outWidth,outHeight;float exposure;UINT temporal;
};
static_assert(sizeof(Frame)==160);
struct Backend {
    ComPtr<IDXGIAdapter1> adapter;
    ComPtr<ID3D12Device> device;
    ComPtr<ID3D12CommandQueue> queue;
    ComPtr<IDXGISwapChain4> swap;
    ComPtr<ID3D12DescriptorHeap> rtv,heap;
    ComPtr<ID3D12RootSignature> computeRoot,displayRoot,filterRoot;
    ComPtr<ID3D12PipelineState> computePipeline,displayPipeline,filterPipeline;
    ComPtr<ID3D12CommandAllocator> allocator;
    ComPtr<ID3D12GraphicsCommandList> cmd;
    ComPtr<ID3D12Fence> fence;
    std::array<ComPtr<ID3D12Resource>,2> buffers;
    std::array<ComPtr<ID3D12Resource>,4> scene;
    Texture color,guide,motion,depth,filtered,output;
    Upscaler upscaler;
    HANDLE event=nullptr;
    UINT64 fenceValue=0;
    UINT rtvStride=0,stride=0,width=0,height=0;
    bool dxr=false,historyReset=true,sceneReady=false;
    std::string mode="off";
    std::wstring sdkPath;
    Frame frame{};
    ~Backend() {
        // Keep all GPU resources alive until queued commands have finished.
        try { wait(); } catch(...) { }
        upscaler.close();
        if(event) CloseHandle(event);
    }
    void wait() {
        if(!queue||!fence||!event) return;
        check(queue->Signal(fence.Get(),++fenceValue),"Queue signal");
        UINT64 done=fence->GetCompletedValue();
        if(done==UINT64_MAX) check(device->GetDeviceRemovedReason(),"Device removed");
        if(done<fenceValue) {
            check(fence->SetEventOnCompletion(fenceValue,event),"Fence event");
            if(WaitForSingleObject(event,INFINITE)!=WAIT_OBJECT_0) throw std::runtime_error("GPU fence wait failed");
        }
    }
    D3D12_CPU_DESCRIPTOR_HANDLE cpu(UINT i) { auto h=heap->GetCPUDescriptorHandleForHeapStart();h.ptr+=SIZE_T(i)*stride;return h; }
    D3D12_GPU_DESCRIPTOR_HANDLE gpu(UINT i) { auto h=heap->GetGPUDescriptorHandleForHeapStart();h.ptr+=UINT64(i)*stride;return h; }
    void transition(Texture& t,D3D12_RESOURCE_STATES to) {
        if(t.state==to) return;
        D3D12_RESOURCE_BARRIER b{};b.Type=D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        b.Transition={t.resource.Get(),D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES,t.state,to};cmd->ResourceBarrier(1,&b);t.state=to;
    }
    void texture(Texture& t,UINT w,UINT h,DXGI_FORMAT format) {
        t.resource.Reset();t.state=D3D12_RESOURCE_STATE_UNORDERED_ACCESS;
        D3D12_HEAP_PROPERTIES hp{};hp.Type=D3D12_HEAP_TYPE_DEFAULT;
        D3D12_RESOURCE_DESC d{};d.Dimension=D3D12_RESOURCE_DIMENSION_TEXTURE2D;d.Width=w;d.Height=h;
        d.DepthOrArraySize=1;d.MipLevels=1;d.Format=format;d.SampleDesc.Count=1;d.Flags=D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS;
        check(device->CreateCommittedResource(&hp,D3D12_HEAP_FLAG_NONE,&d,t.state,nullptr,IID_PPV_ARGS(&t.resource)),"Create texture");
    }
    void srv(Texture& t,UINT slot) { device->CreateShaderResourceView(t.resource.Get(),nullptr,cpu(slot)); }
    void uav(Texture& t,UINT slot) { device->CreateUnorderedAccessView(t.resource.Get(),nullptr,nullptr,cpu(slot)); }
    void targets() {
        auto h=rtv->GetCPUDescriptorHandleForHeapStart();
        for(UINT i=0;i<2;i++) {check(swap->GetBuffer(i,IID_PPV_ARGS(&buffers[i])),"Get backbuffer");device->CreateRenderTargetView(buffers[i].Get(),nullptr,h);h.ptr+=rtvStride;}
        upscaler.init(device.Get(),mode,sdkPath,width,height);
        // The SDK owns its quality-mode input size. Native mode uses the configured cap.
        UINT rw=upscaler.width,rh=upscaler.height;
        if(mode=="off") {
            float scale=std::min(1.f,std::min(float(frame.width)/width,float(frame.height)/height));
            rw=std::max(1u,UINT(width*scale));rh=std::max(1u,UINT(height*scale));
        }
        frame.width=rw;frame.height=rh;frame.outWidth=width;frame.outHeight=height;
        texture(color,rw,rh,DXGI_FORMAT_R32G32B32A32_FLOAT);
        texture(guide,rw,rh,DXGI_FORMAT_R32G32B32A32_FLOAT);
        texture(motion,rw,rh,DXGI_FORMAT_R16G16_FLOAT);
        texture(depth,rw,rh,DXGI_FORMAT_R32_FLOAT);
        texture(filtered,rw,rh,DXGI_FORMAT_R16G16B16A16_FLOAT);
        texture(output,width,height,DXGI_FORMAT_R16G16B16A16_FLOAT);
        uav(color,4);uav(guide,5);uav(motion,6);uav(depth,7);
        srv(color,8);srv(guide,9);uav(filtered,10);
        srv(mode=="off"?filtered:output,11);
        historyReset=true;frame.frame=0;
    }
};
static Backend& backend(jlong handle) { if(!handle) throw std::runtime_error("Closed DX12 backend");return *reinterpret_cast<Backend*>(handle); }
static ComPtr<ID3DBlob> compile(const char* source,const char* entry,const char* profile) {
    ComPtr<ID3DBlob> code,error;
    HRESULT hr=D3DCompile(source,strlen(source),"embedded.hlsl",nullptr,nullptr,entry,profile,D3DCOMPILE_OPTIMIZATION_LEVEL3,0,&code,&error);
    if(FAILED(hr)) throw std::runtime_error(error?std::string(static_cast<char*>(error->GetBufferPointer()),error->GetBufferSize()):"Shader compilation failed");
    return code;
}
static ComPtr<ID3D12RootSignature> root(Backend& b,D3D12_ROOT_SIGNATURE_DESC& d) {
    ComPtr<ID3DBlob> blob,error;check(D3D12SerializeRootSignature(&d,D3D_ROOT_SIGNATURE_VERSION_1,&blob,&error),"Serialize root signature");
    ComPtr<ID3D12RootSignature> r;check(b.device->CreateRootSignature(0,blob->GetBufferPointer(),blob->GetBufferSize(),IID_PPV_ARGS(&r)),"Create root signature");return r;
}
static void pipelines(Backend& b) {
    D3D12_DESCRIPTOR_RANGE ranges[2]{};
    ranges[0]={D3D12_DESCRIPTOR_RANGE_TYPE_SRV,4,0,0,0};ranges[1]={D3D12_DESCRIPTOR_RANGE_TYPE_UAV,4,0,0,0};
    D3D12_ROOT_PARAMETER p[3]{};
    p[0].ParameterType=D3D12_ROOT_PARAMETER_TYPE_32BIT_CONSTANTS;p[0].Constants={0,0,sizeof(Frame)/4};
    for(UINT i=1;i<3;i++) {p[i].ParameterType=D3D12_ROOT_PARAMETER_TYPE_DESCRIPTOR_TABLE;p[i].DescriptorTable={1,&ranges[i-1]};}
    D3D12_ROOT_SIGNATURE_DESC d{};d.NumParameters=3;d.pParameters=p;b.computeRoot=root(b,d);
    auto cs=compile(pathtraceSource,"main","cs_5_1");
    D3D12_COMPUTE_PIPELINE_STATE_DESC cp{};cp.pRootSignature=b.computeRoot.Get();cp.CS={cs->GetBufferPointer(),cs->GetBufferSize()};
    check(b.device->CreateComputePipelineState(&cp,IID_PPV_ARGS(&b.computePipeline)),"Create pathtracing pipeline");
    p[0].Constants.Num32BitValues=4;ranges[0].NumDescriptors=2;ranges[1].NumDescriptors=1;
    b.filterRoot=root(b,d);cs=compile(denoiseSource,"main","cs_5_1");cp.pRootSignature=b.filterRoot.Get();cp.CS={cs->GetBufferPointer(),cs->GetBufferSize()};
    check(b.device->CreateComputePipelineState(&cp,IID_PPV_ARGS(&b.filterPipeline)),"Create denoise pipeline");
    p[0].Constants.Num32BitValues=1;ranges[0].NumDescriptors=1;d.NumParameters=2;
    D3D12_STATIC_SAMPLER_DESC sampler{};sampler.Filter=D3D12_FILTER_MIN_MAG_MIP_LINEAR;
    sampler.AddressU=sampler.AddressV=sampler.AddressW=D3D12_TEXTURE_ADDRESS_MODE_CLAMP;
    sampler.MaxLOD=D3D12_FLOAT32_MAX;sampler.ShaderVisibility=D3D12_SHADER_VISIBILITY_PIXEL;
    d.NumStaticSamplers=1;d.pStaticSamplers=&sampler;b.displayRoot=root(b,d);
    auto vs=compile(presentSource,"vs","vs_5_1"),ps=compile(presentSource,"ps","ps_5_1");
    D3D12_GRAPHICS_PIPELINE_STATE_DESC gp{};gp.pRootSignature=b.displayRoot.Get();
    gp.VS={vs->GetBufferPointer(),vs->GetBufferSize()};gp.PS={ps->GetBufferPointer(),ps->GetBufferSize()};
    gp.RasterizerState.FillMode=D3D12_FILL_MODE_SOLID;gp.RasterizerState.CullMode=D3D12_CULL_MODE_NONE;gp.RasterizerState.DepthClipEnable=TRUE;
    gp.BlendState.RenderTarget[0].RenderTargetWriteMask=D3D12_COLOR_WRITE_ENABLE_ALL;
    gp.SampleMask=UINT_MAX;gp.PrimitiveTopologyType=D3D12_PRIMITIVE_TOPOLOGY_TYPE_TRIANGLE;
    gp.NumRenderTargets=1;gp.RTVFormats[0]=DXGI_FORMAT_R8G8B8A8_UNORM;gp.SampleDesc.Count=1;
    check(b.device->CreateGraphicsPipelineState(&gp,IID_PPV_ARGS(&b.displayPipeline)),"Create display pipeline");
}
static void initialize(Backend& b,bool warp) {
    if(warp) {ComPtr<ID3D12Debug> debug;if(SUCCEEDED(D3D12GetDebugInterface(IID_PPV_ARGS(&debug)))) debug->EnableDebugLayer();}
    ComPtr<IDXGIFactory6> factory;check(CreateDXGIFactory2(0,IID_PPV_ARGS(&factory)),"Create DXGI factory");
    if(warp) {
        check(factory->EnumWarpAdapter(IID_PPV_ARGS(&b.adapter)),"Create explicit WARP test adapter");
        check(D3D12CreateDevice(b.adapter.Get(),D3D_FEATURE_LEVEL_12_0,IID_PPV_ARGS(&b.device)),"Create WARP test device");
    }
    for(UINT i=0;!b.device;i++) {
        ComPtr<IDXGIAdapter1> candidate;
        HRESULT hr=factory->EnumAdapterByGpuPreference(i,DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,IID_PPV_ARGS(&candidate));
        if(hr==DXGI_ERROR_NOT_FOUND) break;check(hr,"Enumerate GPU");
        DXGI_ADAPTER_DESC1 desc{};check(candidate->GetDesc1(&desc),"Get GPU description");
        if(desc.Flags&DXGI_ADAPTER_FLAG_SOFTWARE) continue;
        if(SUCCEEDED(D3D12CreateDevice(candidate.Get(),D3D_FEATURE_LEVEL_12_0,IID_PPV_ARGS(&b.device)))) {b.adapter=candidate;break;}
    }
    if(!b.device) throw std::runtime_error("No hardware DirectX 12 device available");
    D3D12_FEATURE_DATA_D3D12_OPTIONS5 options{};
    b.dxr=SUCCEEDED(b.device->CheckFeatureSupport(D3D12_FEATURE_D3D12_OPTIONS5,&options,sizeof(options)))&&options.RaytracingTier>=D3D12_RAYTRACING_TIER_1_0;
    D3D12_COMMAND_QUEUE_DESC q{};q.Type=D3D12_COMMAND_LIST_TYPE_DIRECT;
    check(b.device->CreateCommandQueue(&q,IID_PPV_ARGS(&b.queue)),"Create queue");
    check(b.device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT,IID_PPV_ARGS(&b.allocator)),"Create allocator");
    check(b.device->CreateCommandList(0,D3D12_COMMAND_LIST_TYPE_DIRECT,b.allocator.Get(),nullptr,IID_PPV_ARGS(&b.cmd)),"Create command list");
    check(b.cmd->Close(),"Close initial command list");
    check(b.device->CreateFence(0,D3D12_FENCE_FLAG_NONE,IID_PPV_ARGS(&b.fence)),"Create fence");
    b.event=CreateEventW(nullptr,FALSE,FALSE,nullptr);if(!b.event) throw std::runtime_error("CreateEvent failed");
    D3D12_DESCRIPTOR_HEAP_DESC hd{};hd.Type=D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV;hd.NumDescriptors=12;hd.Flags=D3D12_DESCRIPTOR_HEAP_FLAG_SHADER_VISIBLE;
    check(b.device->CreateDescriptorHeap(&hd,IID_PPV_ARGS(&b.heap)),"Create shader descriptor heap");
    b.stride=b.device->GetDescriptorHandleIncrementSize(hd.Type);
    hd.Type=D3D12_DESCRIPTOR_HEAP_TYPE_RTV;hd.NumDescriptors=2;hd.Flags=D3D12_DESCRIPTOR_HEAP_FLAG_NONE;
    check(b.device->CreateDescriptorHeap(&hd,IID_PPV_ARGS(&b.rtv)),"Create RTV heap");b.rtvStride=b.device->GetDescriptorHandleIncrementSize(hd.Type);
    pipelines(b);
}
#define JNI_NAME(name) Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_##name
extern "C" JNIEXPORT jlong JNICALL JNI_NAME(create)(JNIEnv* env,jclass,jboolean warp) {
    try {auto b=std::make_unique<Backend>();initialize(*b,warp);return reinterpret_cast<jlong>(b.release());}catch(const std::exception& e){fail(env,e);return 0;}
}
extern "C" JNIEXPORT void JNICALL JNI_NAME(destroy)(JNIEnv*,jclass,jlong h) {delete reinterpret_cast<Backend*>(h);}
extern "C" JNIEXPORT jstring JNICALL JNI_NAME(adapterName)(JNIEnv* env,jclass,jlong h) {
    try {DXGI_ADAPTER_DESC1 d{};check(backend(h).adapter->GetDesc1(&d),"GPU name");return env->NewString(reinterpret_cast<const jchar*>(d.Description),jsize(wcslen(d.Description)));}
    catch(const std::exception& e){fail(env,e);return nullptr;}
}
extern "C" JNIEXPORT jboolean JNICALL JNI_NAME(raytracingSupported)(JNIEnv* env,jclass,jlong h) {
    try {return backend(h).dxr;}catch(const std::exception& e){fail(env,e);return false;}
}
extern "C" JNIEXPORT jstring JNICALL JNI_NAME(upscalerName)(JNIEnv* env,jclass,jlong h) {
    try {return env->NewStringUTF(backend(h).upscaler.name.c_str());}catch(const std::exception& e){fail(env,e);return nullptr;}
}
extern "C" JNIEXPORT void JNICALL JNI_NAME(attach)(JNIEnv* env,jclass,jlong h,jlong hwnd,jint w,jint ht,jint rw,jint rh,jstring mode,jstring dll) {
    try {
        auto& b=backend(h);if(b.swap||!hwnd||w<1||ht<1||rw<1||rh<1) throw std::runtime_error("Invalid or repeated window attachment");
        const char* m=env->GetStringUTFChars(mode,nullptr);if(!m) return;b.mode=m;env->ReleaseStringUTFChars(mode,m);
        const jchar* s=env->GetStringChars(dll,nullptr);if(!s) return;b.sdkPath.assign(reinterpret_cast<const wchar_t*>(s),env->GetStringLength(dll));env->ReleaseStringChars(dll,s);
        b.frame.width=rw;b.frame.height=rh;b.width=w;b.height=ht;b.frame.temporal=b.mode!="off";
        ComPtr<IDXGIFactory4> f;check(CreateDXGIFactory2(0,IID_PPV_ARGS(&f)),"Create swapchain factory");
        DXGI_SWAP_CHAIN_DESC1 d{};d.Width=w;d.Height=ht;d.Format=DXGI_FORMAT_R8G8B8A8_UNORM;d.BufferCount=2;
        d.BufferUsage=DXGI_USAGE_RENDER_TARGET_OUTPUT;d.SwapEffect=DXGI_SWAP_EFFECT_FLIP_DISCARD;d.SampleDesc.Count=1;
        ComPtr<IDXGISwapChain1> swap;check(f->CreateSwapChainForHwnd(b.queue.Get(),reinterpret_cast<HWND>(hwnd),&d,nullptr,nullptr,&swap),"Create swapchain");
        check(swap.As(&b.swap),"Query swapchain");check(f->MakeWindowAssociation(reinterpret_cast<HWND>(hwnd),DXGI_MWA_NO_ALT_ENTER),"Window association");b.targets();
    }catch(const std::exception& e){fail(env,e);}
}
extern "C" JNIEXPORT void JNICALL JNI_NAME(resize)(JNIEnv* env,jclass,jlong h,jint w,jint ht,jint rw,jint rh) {
    try {auto& b=backend(h);if(!b.swap||w<1||ht<1||rw<1||rh<1) throw std::runtime_error("Invalid resize");b.wait();
        for(auto& buffer:b.buffers) buffer.Reset();
        check(b.swap->ResizeBuffers(2,w,ht,DXGI_FORMAT_UNKNOWN,0),"Resize swapchain");
        b.width=w;b.height=ht;b.frame.width=rw;b.frame.height=rh;b.targets();
    }catch(const std::exception& e){fail(env,e);}
}
extern "C" JNIEXPORT void JNICALL JNI_NAME(upload)(JNIEnv* env,jclass,jlong h,jobjectArray data,jint movingStart) {
    try {auto& b=backend(h);if(env->GetArrayLength(data)!=4) throw std::runtime_error("Four scene buffers required");b.wait();
        std::array<ComPtr<ID3D12Resource>,4> resources,staging;const UINT strides[]={16,16,48,48};
        check(b.allocator->Reset(),"Reset scene allocator");check(b.cmd->Reset(b.allocator.Get(),nullptr),"Reset scene upload");
        for(UINT i=0;i<4;i++) {
            jobject buffer=env->GetObjectArrayElement(data,i);if(!buffer) throw std::runtime_error("Null scene buffer");
            void* address=env->GetDirectBufferAddress(buffer);jlong size=env->GetDirectBufferCapacity(buffer);env->DeleteLocalRef(buffer);
            if(!address||size<=0||size%strides[i]||size>INT_MAX) throw std::runtime_error("Invalid direct scene buffer");
            D3D12_HEAP_PROPERTIES hp{};hp.Type=D3D12_HEAP_TYPE_UPLOAD;
            D3D12_RESOURCE_DESC d{};d.Dimension=D3D12_RESOURCE_DIMENSION_BUFFER;d.Width=size;d.Height=1;d.DepthOrArraySize=1;d.MipLevels=1;d.SampleDesc.Count=1;d.Layout=D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
            check(b.device->CreateCommittedResource(&hp,D3D12_HEAP_FLAG_NONE,&d,D3D12_RESOURCE_STATE_GENERIC_READ,nullptr,IID_PPV_ARGS(&staging[i])),"Create scene staging buffer");
            void* mapped=nullptr;D3D12_RANGE noRead{0,0};check(staging[i]->Map(0,&noRead,&mapped),"Map scene buffer");memcpy(mapped,address,size_t(size));staging[i]->Unmap(0,nullptr);
            hp.Type=D3D12_HEAP_TYPE_DEFAULT;
            check(b.device->CreateCommittedResource(&hp,D3D12_HEAP_FLAG_NONE,&d,D3D12_RESOURCE_STATE_COPY_DEST,nullptr,IID_PPV_ARGS(&resources[i])),"Create GPU scene buffer");
            b.cmd->CopyBufferRegion(resources[i].Get(),0,staging[i].Get(),0,UINT64(size));
            D3D12_RESOURCE_BARRIER barrier{};barrier.Type=D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            barrier.Transition={resources[i].Get(),D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES,D3D12_RESOURCE_STATE_COPY_DEST,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE};
            b.cmd->ResourceBarrier(1,&barrier);
            if(i==1) b.frame.triangles=UINT(size/16);
        }
        check(b.cmd->Close(),"Close scene upload");ID3D12CommandList* lists[]={b.cmd.Get()};
        b.queue->ExecuteCommandLists(1,lists);b.wait();
        b.scene=std::move(resources);
        for(UINT i=0;i<4;i++) {D3D12_SHADER_RESOURCE_VIEW_DESC d{};d.Shader4ComponentMapping=D3D12_DEFAULT_SHADER_4_COMPONENT_MAPPING;d.ViewDimension=D3D12_SRV_DIMENSION_BUFFER;
            d.Buffer.NumElements=UINT(b.scene[i]->GetDesc().Width/strides[i]);d.Buffer.StructureByteStride=strides[i];b.device->CreateShaderResourceView(b.scene[i].Get(),&d,b.cpu(i));}
        b.frame.movingStart=movingStart;b.historyReset=true;b.frame.frame=0;b.sceneReady=true;
    }catch(const std::exception& e){fail(env,e);}
}
static float halton(UINT index,UINT base) {float result=0,fraction=1;while(index){fraction/=base;result+=fraction*(index%base);index/=base;}return result;}
extern "C" JNIEXPORT void JNICALL JNI_NAME(render)(JNIEnv* env,jclass,jlong h,jfloatArray camera,jfloat lift,jint samples,jint bounces,jfloat exposure,jfloat strength,jboolean denoise,jfloat ms,jboolean reset,jboolean vsync) {
    try {auto& b=backend(h);if(!b.swap||!b.sceneReady||env->GetArrayLength(camera)!=12) throw std::runtime_error("Renderer is not ready");
        if(samples<1||samples>16||bounces<1||bounces>12||!std::isfinite(lift)||!std::isfinite(exposure)||!std::isfinite(strength)||!std::isfinite(ms)) throw std::runtime_error("Invalid frame settings");
        float c[12];env->GetFloatArrayRegion(camera,0,12,c);if(env->ExceptionCheck()) return;
        for(float v:c) if(!std::isfinite(v)) throw std::runtime_error("Invalid camera");
        bool changed=memcmp(b.frame.position,c,12)||memcmp(b.frame.forward,c+3,12)||memcmp(b.frame.right,c+6,12)||memcmp(b.frame.up,c+9,12)||b.frame.lift!=lift;
        memcpy(b.frame.prevPosition,b.frame.position,12);memcpy(b.frame.prevForward,b.frame.forward,12);memcpy(b.frame.prevRight,b.frame.right,12);memcpy(b.frame.prevUp,b.frame.up,12);b.frame.prevLift=b.frame.lift;
        memcpy(b.frame.position,c,12);memcpy(b.frame.forward,c+3,12);memcpy(b.frame.right,c+6,12);memcpy(b.frame.up,c+9,12);b.frame.lift=lift;
        if(reset) b.historyReset=true;
        if(changed||b.historyReset||b.frame.samples!=UINT(samples)||b.frame.bounces!=UINT(bounces)) b.frame.frame=0;
        if(b.historyReset) {memcpy(b.frame.prevPosition,c,12);memcpy(b.frame.prevForward,c+3,12);memcpy(b.frame.prevRight,c+6,12);memcpy(b.frame.prevUp,c+9,12);b.frame.prevLift=lift;}
        b.frame.samples=samples;b.frame.bounces=bounces;b.frame.exposure=exposure;
        UINT phase=UINT(std::ceil(8.f*float(b.width)/b.frame.width*float(b.width)/b.frame.width));
        b.frame.jx=b.frame.temporal?halton(b.frame.sequence%phase+1,2)-.5f:0;
        b.frame.jy=b.frame.temporal?halton(b.frame.sequence%phase+1,3)-.5f:0;
        b.wait();check(b.allocator->Reset(),"Reset allocator");check(b.cmd->Reset(b.allocator.Get(),nullptr),"Reset commands");
        ID3D12DescriptorHeap* heaps[]={b.heap.Get()};b.cmd->SetDescriptorHeaps(1,heaps);
        for(Texture* t:{&b.color,&b.guide,&b.motion,&b.depth}) b.transition(*t,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);
        b.cmd->SetPipelineState(b.computePipeline.Get());b.cmd->SetComputeRootSignature(b.computeRoot.Get());
        b.cmd->SetComputeRoot32BitConstants(0,sizeof(Frame)/4,&b.frame,0);b.cmd->SetComputeRootDescriptorTable(1,b.gpu(0));b.cmd->SetComputeRootDescriptorTable(2,b.gpu(4));
        // The accumulation UAV is read on subsequent stationary frames.
        D3D12_RESOURCE_BARRIER u{};u.Type=D3D12_RESOURCE_BARRIER_TYPE_UAV;u.UAV.pResource=b.color.resource.Get();b.cmd->ResourceBarrier(1,&u);
        b.cmd->Dispatch((b.frame.width+7)/8,(b.frame.height+7)/8,1);
        b.transition(b.color,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);b.transition(b.guide,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
        b.transition(b.filtered,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);
        b.cmd->SetPipelineState(b.filterPipeline.Get());b.cmd->SetComputeRootSignature(b.filterRoot.Get());
        struct {UINT w,h;float strength;UINT enabled;} filter{b.frame.width,b.frame.height,strength,UINT(denoise)};
        b.cmd->SetComputeRoot32BitConstants(0,4,&filter,0);b.cmd->SetComputeRootDescriptorTable(1,b.gpu(8));b.cmd->SetComputeRootDescriptorTable(2,b.gpu(10));
        b.cmd->Dispatch((b.frame.width+7)/8,(b.frame.height+7)/8,1);
        if(b.frame.temporal) {
            b.transition(b.filtered,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);b.transition(b.depth,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);b.transition(b.motion,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
            b.transition(b.output,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);
            b.upscaler.dispatch(b.cmd.Get(),b.filtered.resource.Get(),b.depth.resource.Get(),b.motion.resource.Get(),b.output.resource.Get(),b.frame.jx,b.frame.jy,std::clamp(ms,1.f,1000.f),b.historyReset);
            b.transition(b.output,D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE);
        } else b.transition(b.filtered,D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE);
        UINT index=b.swap->GetCurrentBackBufferIndex();
        D3D12_RESOURCE_BARRIER barrier{};barrier.Type=D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        barrier.Transition={b.buffers[index].Get(),D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES,D3D12_RESOURCE_STATE_PRESENT,D3D12_RESOURCE_STATE_RENDER_TARGET};b.cmd->ResourceBarrier(1,&barrier);
        auto target=b.rtv->GetCPUDescriptorHandleForHeapStart();target.ptr+=SIZE_T(index)*b.rtvStride;
        D3D12_VIEWPORT viewport{0,0,float(b.width),float(b.height),0,1};D3D12_RECT scissor{0,0,LONG(b.width),LONG(b.height)};
        // Vendor dispatch may replace all descriptor heaps and pipeline state.
        b.cmd->SetDescriptorHeaps(1,heaps);b.cmd->SetGraphicsRootSignature(b.displayRoot.Get());b.cmd->SetPipelineState(b.displayPipeline.Get());
        b.cmd->SetGraphicsRoot32BitConstants(0,1,&exposure,0);b.cmd->SetGraphicsRootDescriptorTable(1,b.gpu(11));
        b.cmd->RSSetViewports(1,&viewport);b.cmd->RSSetScissorRects(1,&scissor);b.cmd->OMSetRenderTargets(1,&target,FALSE,nullptr);
        b.cmd->IASetPrimitiveTopology(D3D_PRIMITIVE_TOPOLOGY_TRIANGLELIST);b.cmd->DrawInstanced(3,1,0,0);
        std::swap(barrier.Transition.StateBefore,barrier.Transition.StateAfter);b.cmd->ResourceBarrier(1,&barrier);
        check(b.cmd->Close(),"Close frame");ID3D12CommandList* lists[]={b.cmd.Get()};b.queue->ExecuteCommandLists(1,lists);
        check(b.swap->Present(vsync?1:0,0),"Present");b.historyReset=false;b.frame.frame++;b.frame.sequence++;
    }catch(const std::exception& e){fail(env,e);}
}

// Explicit smoke-test readback: check actual compute output, not just successful Present.
extern "C" JNIEXPORT void JNICALL JNI_NAME(validateFrame)(JNIEnv* env,jclass,jlong h) {
    try {
        auto& b=backend(h);b.wait();
        auto desc=b.color.resource->GetDesc();D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint{};UINT64 bytes=0;
        b.device->GetCopyableFootprints(&desc,0,1,0,&footprint,nullptr,nullptr,&bytes);
        D3D12_HEAP_PROPERTIES hp{};hp.Type=D3D12_HEAP_TYPE_READBACK;
        D3D12_RESOURCE_DESC rd{};rd.Dimension=D3D12_RESOURCE_DIMENSION_BUFFER;rd.Width=bytes;rd.Height=1;rd.DepthOrArraySize=1;
        rd.MipLevels=1;rd.SampleDesc.Count=1;rd.Layout=D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
        ComPtr<ID3D12Resource> readback;
        check(b.device->CreateCommittedResource(&hp,D3D12_HEAP_FLAG_NONE,&rd,D3D12_RESOURCE_STATE_COPY_DEST,nullptr,IID_PPV_ARGS(&readback)),"Create smoke readback");
        check(b.allocator->Reset(),"Reset readback allocator");check(b.cmd->Reset(b.allocator.Get(),nullptr),"Reset readback commands");
        auto before=b.color.state;b.transition(b.color,D3D12_RESOURCE_STATE_COPY_SOURCE);
        D3D12_TEXTURE_COPY_LOCATION src{},dst{};src.pResource=b.color.resource.Get();src.Type=D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
        dst.pResource=readback.Get();dst.Type=D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;dst.PlacedFootprint=footprint;
        b.cmd->CopyTextureRegion(&dst,0,0,0,&src,nullptr);b.transition(b.color,before);
        check(b.cmd->Close(),"Close readback");ID3D12CommandList* lists[]={b.cmd.Get()};b.queue->ExecuteCommandLists(1,lists);b.wait();
        void* mapped=nullptr;D3D12_RANGE range{0,SIZE_T(bytes)};check(readback->Map(0,&range,&mapped),"Map readback");
        bool finite=true;float minimum=1e30f,maximum=0;
        for(UINT y=0;y<b.frame.height;y++) {
            auto row=reinterpret_cast<const float*>(static_cast<const char*>(mapped)+footprint.Offset+y*footprint.Footprint.RowPitch);
            for(UINT x=0;x<b.frame.width;x++) for(UINT c=0;c<3;c++) {float v=row[x*4+c];finite&=std::isfinite(v)&&v>=0;minimum=std::min(minimum,v);maximum=std::max(maximum,v);}
        }
        D3D12_RANGE noWrite{0,0};readback->Unmap(0,&noWrite);
        if(!finite||maximum<=0||maximum-minimum<1e-6f) throw std::runtime_error("DX12 smoke image is invalid, empty or constant");
        ComPtr<ID3D12InfoQueue> info;
        if(SUCCEEDED(b.device.As(&info))) {
            for(UINT64 i=0;i<info->GetNumStoredMessagesAllowedByRetrievalFilter();i++) {
                SIZE_T size=0;check(info->GetMessage(i,nullptr,&size),"Debug message size");std::vector<char> memory(size);
                auto message=reinterpret_cast<D3D12_MESSAGE*>(memory.data());check(info->GetMessage(i,message,&size),"Debug message");
                if(message->Severity<=D3D12_MESSAGE_SEVERITY_ERROR) throw std::runtime_error(std::string("D3D12 validation: ")+message->pDescription);
            }
        }
    }catch(const std::exception& e){fail(env,e);}
}
