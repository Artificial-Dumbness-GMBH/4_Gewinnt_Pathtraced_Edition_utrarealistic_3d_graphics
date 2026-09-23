#pragma once
#define NOMINMAX
#include <windows.h>
#include <d3d12.h>
#include <dxgi1_6.h>
#include <wrl.h>
#include <algorithm>
#include <array>
#include <cmath>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include "vendor_effects.h"
#include "trace_sw.h"
#include "trace_hw.h"
#include "denoise.h"
#include "temporal.h"
#include "history.h"
#include "tonemap.h"
#include "composite.h"
using Microsoft::WRL::ComPtr;
inline void checked(HRESULT hr,const char* operation) {
    if(FAILED(hr)) throw std::runtime_error(std::string(operation)+" (HRESULT "+std::to_string(static_cast<unsigned long>(hr))+")");
}
struct Constants {
    float camera[16]{},previous[16]{};
    float lightPosition[4]{0,13.8f,0,0},lightSize[4]{6,0,5,0},lightRadiance[4]{18,17.2f,16,0};
    UINT counts[4]{},geometry[4]{};
    float motion[4]{},dimensions[4]{},options[4]{};
    UINT post[4]{};
};
static_assert(sizeof(Constants)==272,"HLSL constant layout");
struct Texture { ComPtr<ID3D12Resource> resource;D3D12_RESOURCE_STATES state=D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE; };
struct Backend {
    ComPtr<IDXGIFactory6> factory;
    ComPtr<IDXGIAdapter1> adapter;
    ComPtr<ID3D12Device5> device;
    ComPtr<ID3D12CommandQueue> queue;
    ComPtr<IDXGISwapChain4> swap;
    ComPtr<ID3D12CommandAllocator> allocator;
    ComPtr<ID3D12GraphicsCommandList4> list;
    ComPtr<ID3D12Fence> fence;
    ComPtr<ID3D12DescriptorHeap> heap;
    ComPtr<ID3D12RootSignature> root;
    std::array<ComPtr<ID3D12PipelineState>,7> pipelines;
    std::array<ComPtr<ID3D12Resource>,2> back;
    std::array<ComPtr<ID3D12Resource>,4> sceneBuffers;
    std::array<Texture,14> textures;
    ComPtr<ID3D12Resource> constants,uiUpload,rtVertices,rtIndices,blas,tlas,scratch,instance;
    D3D12_PLACED_SUBRESOURCE_FOOTPRINT uiFootprint{};
    std::vector<float> baseVertices;
    UINT triangleCount=0,width=0,height=0,renderWidth=0,renderHeight=0,stride=0,constantOffset=0,accumulation=0,sequence=0;
    int movingStart=-1;
    float previousLift=0,previousCamera[16]{};
    bool dxr=false,tearing=false,sceneDirty=true,historyValid=false,configured=false,accelerationBuilt=false;
    HANDLE event=nullptr;
    UINT64 fenceValue=0;
    int settings[12]{0,0,1,2,3,4,960,540,4,1,1,1};
    float display[3]{1,1,.2f};
    VendorEffects vendor;
    std::string info="DX12";
    ~Backend() {
        try { wait(); } catch(...) { }
        back={};swap.Reset();vendor.destroy();if(event) CloseHandle(event);
    }
    void wait() {
        if(!queue||!fence||!event) return;
        UINT64 value=++fenceValue;checked(queue->Signal(fence.Get(),value),"Signal");
        UINT64 done=fence->GetCompletedValue();if(done==UINT64_MAX) throw std::runtime_error("DirectX device removed");
        if(done<value) {
            checked(fence->SetEventOnCompletion(value,event),"SetEventOnCompletion");
            if(WaitForSingleObject(event,10000)!=WAIT_OBJECT_0) throw std::runtime_error("GPU fence timed out");
        }
    }
    ComPtr<ID3D12Resource> buffer(UINT64 bytes,D3D12_HEAP_TYPE type=D3D12_HEAP_TYPE_UPLOAD,
            D3D12_RESOURCE_STATES state=D3D12_RESOURCE_STATE_GENERIC_READ,D3D12_RESOURCE_FLAGS flags=D3D12_RESOURCE_FLAG_NONE) {
        D3D12_HEAP_PROPERTIES hp{};hp.Type=type;
        D3D12_RESOURCE_DESC d{};d.Dimension=D3D12_RESOURCE_DIMENSION_BUFFER;d.Width=std::max<UINT64>(bytes,256);
        d.Height=1;d.DepthOrArraySize=1;d.MipLevels=1;d.SampleDesc.Count=1;d.Layout=D3D12_TEXTURE_LAYOUT_ROW_MAJOR;d.Flags=flags;
        ComPtr<ID3D12Resource> r;checked(device->CreateCommittedResource(&hp,D3D12_HEAP_FLAG_NONE,&d,state,nullptr,IID_PPV_ARGS(&r)),"Create buffer");return r;
    }
    static void upload(ID3D12Resource* r,const void* data,size_t bytes,size_t offset=0) {
        void* mapped=nullptr;D3D12_RANGE empty{0,0};checked(r->Map(0,&empty,&mapped),"Map");
        std::memcpy(static_cast<char*>(mapped)+offset,data,bytes);D3D12_RANGE written{offset,offset+bytes};r->Unmap(0,&written);
    }
    void init(bool debug) {
        if(debug) { ComPtr<ID3D12Debug> d;if(SUCCEEDED(D3D12GetDebugInterface(IID_PPV_ARGS(&d)))) d->EnableDebugLayer(); }
        checked(CreateDXGIFactory2(0,IID_PPV_ARGS(&factory)),"Create factory");
        for(UINT i=0;;i++) {
            ComPtr<IDXGIAdapter1> a;HRESULT hr=factory->EnumAdapterByGpuPreference(i,DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,IID_PPV_ARGS(&a));
            if(hr==DXGI_ERROR_NOT_FOUND) break;checked(hr,"Enumerate adapter");
            DXGI_ADAPTER_DESC1 desc{};checked(a->GetDesc1(&desc),"Adapter description");if(desc.Flags&DXGI_ADAPTER_FLAG_SOFTWARE) continue;
            if(SUCCEEDED(D3D12CreateDevice(a.Get(),D3D_FEATURE_LEVEL_12_0,IID_PPV_ARGS(&device)))) { adapter=a;break; }
        }
        if(!device) throw std::runtime_error("No DirectX 12 device");
        D3D12_FEATURE_DATA_SHADER_MODEL sm{D3D_SHADER_MODEL_6_0};checked(device->CheckFeatureSupport(D3D12_FEATURE_SHADER_MODEL,&sm,sizeof(sm)),"Shader model 6.0");
        if(sm.HighestShaderModel<D3D_SHADER_MODEL_6_0) throw std::runtime_error("Shader model 6.0 required");
        D3D12_FEATURE_DATA_D3D12_OPTIONS5 opt{};sm.HighestShaderModel=D3D_SHADER_MODEL_6_5;
        dxr=SUCCEEDED(device->CheckFeatureSupport(D3D12_FEATURE_D3D12_OPTIONS5,&opt,sizeof(opt)))&&opt.RaytracingTier>=D3D12_RAYTRACING_TIER_1_1
            &&SUCCEEDED(device->CheckFeatureSupport(D3D12_FEATURE_SHADER_MODEL,&sm,sizeof(sm)))&&sm.HighestShaderModel>=D3D_SHADER_MODEL_6_5;
        BOOL allow=FALSE;tearing=SUCCEEDED(factory->CheckFeatureSupport(DXGI_FEATURE_PRESENT_ALLOW_TEARING,&allow,sizeof(allow)))&&allow;
        D3D12_COMMAND_QUEUE_DESC q{};q.Type=D3D12_COMMAND_LIST_TYPE_DIRECT;checked(device->CreateCommandQueue(&q,IID_PPV_ARGS(&queue)),"Create queue");
        checked(device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT,IID_PPV_ARGS(&allocator)),"Create allocator");
        checked(device->CreateCommandList(0,D3D12_COMMAND_LIST_TYPE_DIRECT,allocator.Get(),nullptr,IID_PPV_ARGS(&list)),"Create command list");checked(list->Close(),"Close initial list");
        checked(device->CreateFence(0,D3D12_FENCE_FLAG_NONE,IID_PPV_ARGS(&fence)),"Create fence");
        event=CreateEventW(nullptr,FALSE,FALSE,nullptr);if(!event) throw std::runtime_error("Create fence event");
        D3D12_DESCRIPTOR_HEAP_DESC hd{};hd.Type=D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV;hd.NumDescriptors=32;hd.Flags=D3D12_DESCRIPTOR_HEAP_FLAG_SHADER_VISIBLE;
        checked(device->CreateDescriptorHeap(&hd,IID_PPV_ARGS(&heap)),"Create descriptor heap");stride=device->GetDescriptorHandleIncrementSize(hd.Type);
        D3D12_DESCRIPTOR_RANGE ranges[2]{};
        ranges[0].RangeType=D3D12_DESCRIPTOR_RANGE_TYPE_SRV;ranges[0].NumDescriptors=16;ranges[0].BaseShaderRegister=5;
        ranges[1].RangeType=D3D12_DESCRIPTOR_RANGE_TYPE_UAV;ranges[1].NumDescriptors=16;
        D3D12_ROOT_PARAMETER params[8]{};params[0].ParameterType=D3D12_ROOT_PARAMETER_TYPE_CBV;
        for(int i=1;i<6;i++) { params[i].ParameterType=D3D12_ROOT_PARAMETER_TYPE_SRV;params[i].Descriptor.ShaderRegister=i-1; }
        for(int i=6;i<8;i++) { params[i].ParameterType=D3D12_ROOT_PARAMETER_TYPE_DESCRIPTOR_TABLE;params[i].DescriptorTable={1,&ranges[i-6]}; }
        D3D12_STATIC_SAMPLER_DESC sampler{};sampler.Filter=D3D12_FILTER_MIN_MAG_MIP_LINEAR;
        sampler.AddressU=sampler.AddressV=sampler.AddressW=D3D12_TEXTURE_ADDRESS_MODE_CLAMP;sampler.MaxLOD=D3D12_FLOAT32_MAX;sampler.MaxAnisotropy=1;
        D3D12_ROOT_SIGNATURE_DESC rd{};rd.NumParameters=8;rd.pParameters=params;rd.NumStaticSamplers=1;rd.pStaticSamplers=&sampler;
        ComPtr<ID3DBlob> serialized,error;checked(D3D12SerializeRootSignature(&rd,D3D_ROOT_SIGNATURE_VERSION_1,&serialized,&error),"Serialize root");
        checked(device->CreateRootSignature(0,serialized->GetBufferPointer(),serialized->GetBufferSize(),IID_PPV_ARGS(&root)),"Create root");
        const void* shaders[]={trace_sw,trace_hw,denoise,temporal,history,tonemap,composite};
        const size_t lengths[]={sizeof(trace_sw),sizeof(trace_hw),sizeof(denoise),sizeof(temporal),sizeof(history),sizeof(tonemap),sizeof(composite)};
        for(int i=0;i<7;i++) {
            if(i==1&&!dxr) continue;
            D3D12_COMPUTE_PIPELINE_STATE_DESC pd{};pd.pRootSignature=root.Get();pd.CS={shaders[i],lengths[i]};
            HRESULT hr=device->CreateComputePipelineState(&pd,IID_PPV_ARGS(&pipelines[i]));
            if(i==1&&FAILED(hr)) { dxr=false;continue; }checked(hr,"Create pipeline");
        }
        constants=buffer(16384);vendor.init(device.Get());
    }
    void targets() { for(UINT i=0;i<2;i++) checked(swap->GetBuffer(i,IID_PPV_ARGS(&back[i])),"Get backbuffer"); }
    void attach(HWND window,UINT w,UINT h) {
        DXGI_SWAP_CHAIN_DESC1 desc{};desc.Width=w;desc.Height=h;desc.Format=DXGI_FORMAT_R8G8B8A8_UNORM;desc.SampleDesc.Count=1;
        desc.BufferCount=2;desc.BufferUsage=DXGI_USAGE_RENDER_TARGET_OUTPUT;desc.SwapEffect=DXGI_SWAP_EFFECT_FLIP_DISCARD;
        desc.Flags=tearing?DXGI_SWAP_CHAIN_FLAG_ALLOW_TEARING:0;
        ComPtr<IDXGISwapChain1> s;checked(factory->CreateSwapChainForHwnd(queue.Get(),window,&desc,nullptr,nullptr,&s),"Create swapchain");
        checked(s.As(&swap),"Query swapchain");checked(factory->MakeWindowAssociation(window,DXGI_MWA_NO_ALT_ENTER),"Window association");
        width=w;height=h;targets();
    }
    void resize(UINT w,UINT h) {
        if(w==width&&h==height) return;wait();back={};
        checked(swap->ResizeBuffers(2,w,h,DXGI_FORMAT_UNKNOWN,tearing?DXGI_SWAP_CHAIN_FLAG_ALLOW_TEARING:0),"Resize swapchain");
        width=w;height=h;targets();configured=false;historyValid=false;accumulation=0;
    }
    void texture(int slot,UINT w,UINT h,DXGI_FORMAT format) {
        auto& t=textures[slot];t.resource.Reset();t.state=D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE;
        D3D12_HEAP_PROPERTIES hp{};hp.Type=D3D12_HEAP_TYPE_DEFAULT;
        D3D12_RESOURCE_DESC d{};d.Dimension=D3D12_RESOURCE_DIMENSION_TEXTURE2D;d.Width=w;d.Height=h;d.DepthOrArraySize=1;d.MipLevels=1;
        d.Format=format;d.SampleDesc.Count=1;d.Flags=D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS;
        checked(device->CreateCommittedResource(&hp,D3D12_HEAP_FLAG_NONE,&d,t.state,nullptr,IID_PPV_ARGS(&t.resource)),"Create texture");
        D3D12_SHADER_RESOURCE_VIEW_DESC srv{};srv.Format=format;srv.ViewDimension=D3D12_SRV_DIMENSION_TEXTURE2D;srv.Shader4ComponentMapping=D3D12_DEFAULT_SHADER_4_COMPONENT_MAPPING;srv.Texture2D.MipLevels=1;
        auto cpu=heap->GetCPUDescriptorHandleForHeapStart();cpu.ptr+=slot*stride;device->CreateShaderResourceView(t.resource.Get(),&srv,cpu);
        D3D12_UNORDERED_ACCESS_VIEW_DESC uav{};uav.Format=format;uav.ViewDimension=D3D12_UAV_DIMENSION_TEXTURE2D;
        cpu.ptr+=16*stride;device->CreateUnorderedAccessView(t.resource.Get(),nullptr,&uav,cpu);
    }
    void configure(const int* values,const float* floats) {
        bool effectChanged=!configured||settings[1]!=values[1]||settings[2]!=values[2]||settings[6]!=values[6]||settings[7]!=values[7];
        bool changed=std::memcmp(settings,values,sizeof(settings))!=0||std::memcmp(display,floats,sizeof(display))!=0;
        if(effectChanged) {
            wait();float scale=std::min(1.f,std::min(float(values[6])/width,float(values[7])/height));
            renderWidth=std::max(1u,UINT(width*scale));renderHeight=std::max(1u,UINT(height*scale));
            vendor.configure(device.Get(),values[1],values[2],width,height,renderWidth,renderHeight);
            for(int i=0;i<14;i++) {
                UINT w=i==13?960:i>=10?width:renderWidth,h=i==13?660:i>=10?height:renderHeight;
                DXGI_FORMAT f=i==0||i==1||i==8?DXGI_FORMAT_R32G32B32A32_FLOAT:i==3?DXGI_FORMAT_R32_FLOAT:
                    i==4?DXGI_FORMAT_R16G16_FLOAT:i>=11?DXGI_FORMAT_R8G8B8A8_UNORM:DXGI_FORMAT_R16G16B16A16_FLOAT;
                texture(i,w,h,f);
            }
            for(UINT i=14;i<16;i++) {
                D3D12_SHADER_RESOURCE_VIEW_DESC s{};s.Format=DXGI_FORMAT_R16G16B16A16_FLOAT;s.ViewDimension=D3D12_SRV_DIMENSION_TEXTURE2D;s.Shader4ComponentMapping=D3D12_DEFAULT_SHADER_4_COMPONENT_MAPPING;s.Texture2D.MipLevels=1;
                auto cpu=heap->GetCPUDescriptorHandleForHeapStart();cpu.ptr+=i*stride;device->CreateShaderResourceView(nullptr,&s,cpu);
                D3D12_UNORDERED_ACCESS_VIEW_DESC u{};u.Format=s.Format;u.ViewDimension=D3D12_UAV_DIMENSION_TEXTURE2D;cpu.ptr+=16*stride;device->CreateUnorderedAccessView(nullptr,nullptr,&u,cpu);
            }
            UINT64 total=0;auto d=textures[13].resource->GetDesc();device->GetCopyableFootprints(&d,0,1,0,&uiFootprint,nullptr,nullptr,&total);uiUpload=buffer(total);
        }
        if(changed||effectChanged) { historyValid=false;accumulation=0; }
        if(settings[0]!=values[0]) accelerationBuilt=false;
        std::memcpy(settings,values,sizeof(settings));std::memcpy(display,floats,sizeof(display));configured=true;
        info=std::string(dxr&&settings[0]!=1?"DXR 1.1":"Software BVH")+" | "+vendor.status();
    }
    void scene(const std::array<const void*,4>& data,const std::array<size_t,4>& sizes,int moving) {
        wait();for(int i=0;i<4;i++) { sceneBuffers[i]=buffer(sizes[i]);upload(sceneBuffers[i].Get(),data[i],sizes[i]); }
        triangleCount=UINT(sizes[1]/16);movingStart=moving;baseVertices.resize(sizes[0]/sizeof(float));std::memcpy(baseVertices.data(),data[0],sizes[0]);
        if(dxr) {
            rtVertices=buffer(sizes[0]);upload(rtVertices.Get(),data[0],sizes[0]);
            std::vector<UINT> indices(triangleCount*3);auto tri=static_cast<const UINT*>(data[1]);
            for(UINT i=0;i<triangleCount;i++) for(UINT j=0;j<3;j++) indices[i*3+j]=tri[i*4+j];
            rtIndices=buffer(indices.size()*4);upload(rtIndices.Get(),indices.data(),indices.size()*4);
        }
        accelerationBuilt=false;sceneDirty=true;historyValid=false;accumulation=0;
    }
    void barrier(ID3D12Resource* r) { D3D12_RESOURCE_BARRIER b{};b.Type=D3D12_RESOURCE_BARRIER_TYPE_UAV;b.UAV.pResource=r;list->ResourceBarrier(1,&b); }
    void transition(int index,D3D12_RESOURCE_STATES state) {
        auto& t=textures[index];if(t.state==state) return;
        D3D12_RESOURCE_BARRIER b{};b.Type=D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;b.Transition.pResource=t.resource.Get();
        b.Transition.Subresource=D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;b.Transition.StateBefore=t.state;b.Transition.StateAfter=state;list->ResourceBarrier(1,&b);t.state=state;
    }
    void acceleration(float lift);
    void render(const float* camera,float lift,float milliseconds,const void* ui,int uiMode);
    void dispatch(int pipeline,Constants& c,UINT w,UINT h) {
        if(constantOffset+512>16384) throw std::runtime_error("Constant upload overflow");
        upload(constants.Get(),&c,sizeof(c),constantOffset);
        ID3D12DescriptorHeap* heaps[]={heap.Get()};list->SetDescriptorHeaps(1,heaps);list->SetComputeRootSignature(root.Get());
        list->SetComputeRootConstantBufferView(0,constants->GetGPUVirtualAddress()+constantOffset);constantOffset+=512;
        for(int i=0;i<4;i++) list->SetComputeRootShaderResourceView(i+1,sceneBuffers[i]->GetGPUVirtualAddress());
        list->SetComputeRootShaderResourceView(5,tlas?tlas->GetGPUVirtualAddress():0);
        auto gpu=heap->GetGPUDescriptorHandleForHeapStart();list->SetComputeRootDescriptorTable(6,gpu);gpu.ptr+=16*stride;list->SetComputeRootDescriptorTable(7,gpu);
        list->SetPipelineState(pipelines[pipeline].Get());list->Dispatch((w+7)/8,(h+7)/8,1);
    }
    static float halton(UINT index,UINT base) { float f=1,value=0;while(index) { f/=base;value+=f*(index%base);index/=base; }return value-.5f; }
};
