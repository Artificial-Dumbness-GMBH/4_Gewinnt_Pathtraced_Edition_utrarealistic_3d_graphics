#include <windows.h>
#include <d3d12.h>
#include <d3dcompiler.h>
#include <dxgi1_6.h>
#include <jni.h>
#include <wrl.h>

#include <string>
#include <vector>
#include <cstring>

using Microsoft::WRL::ComPtr;

struct Backend {
    ComPtr<IDXGIAdapter1> adapter;
    ComPtr<ID3D12Device> device;
    ComPtr<ID3D12CommandQueue> queue;
    ComPtr<IDXGISwapChain4> swapChain;
    ComPtr<ID3D12DescriptorHeap> rtvHeap;
    ComPtr<ID3D12RootSignature> rootSignature;
    ComPtr<ID3D12PipelineState> pipeline;
    ComPtr<ID3D12CommandAllocator> allocator;
    ComPtr<ID3D12GraphicsCommandList> commandList;
    ComPtr<ID3D12Fence> fence;
    std::vector<ComPtr<ID3D12Resource>> buffers;
    HANDLE fenceEvent=nullptr;
    UINT64 fenceValue=0;
    UINT rtvStride=0;
    UINT bufferCount=0;
    UINT width=0;
    UINT height=0;
    bool raytracingSupported=false;
};

static bool create_test_pipeline(Backend& backend);

static bool wait_for_gpu(Backend& backend) {
    if(!backend.queue||!backend.fence||!backend.fenceEvent) return false;
    const UINT64 value=++backend.fenceValue;
    if(FAILED(backend.queue->Signal(backend.fence.Get(),value))) return false;
    const UINT64 completed=backend.fence->GetCompletedValue();
    if(completed==UINT64_MAX) return false; // Device removed, not successful completion.
    if(completed<value) {
        if(FAILED(backend.fence->SetEventOnCompletion(value,backend.fenceEvent))) return false;
        if(WaitForSingleObject(backend.fenceEvent,10000)!=WAIT_OBJECT_0) return false;
    }
    return true;
}

static bool create_targets(Backend& backend) {
    if(!backend.device||!backend.swapChain||!backend.rtvHeap) return false;
    backend.buffers.clear();backend.buffers.resize(backend.bufferCount);
    auto handle=backend.rtvHeap->GetCPUDescriptorHandleForHeapStart();
    for(UINT index=0;index<backend.bufferCount;++index) {
        if(FAILED(backend.swapChain->GetBuffer(index,IID_PPV_ARGS(&backend.buffers[index])))) return false;
        backend.device->CreateRenderTargetView(backend.buffers[index].Get(),nullptr,handle);
        handle.ptr+=backend.rtvStride;
    }
    return true;
}

static bool create_backend(Backend& backend) {
    ComPtr<IDXGIFactory6> factory;
    if(FAILED(CreateDXGIFactory2(0,IID_PPV_ARGS(&factory)))) return false;
    for(UINT index=0;;++index) {
        ComPtr<IDXGIAdapter1> candidate;
        const HRESULT enumerated=factory->EnumAdapterByGpuPreference(index,DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,
                IID_PPV_ARGS(&candidate));
        if(enumerated==DXGI_ERROR_NOT_FOUND) break;
        if(FAILED(enumerated)||!candidate) return false;
        DXGI_ADAPTER_DESC1 description{};
        if(FAILED(candidate->GetDesc1(&description))) return false;
        if(description.Flags&DXGI_ADAPTER_FLAG_SOFTWARE) continue;
        if(SUCCEEDED(D3D12CreateDevice(candidate.Get(),D3D_FEATURE_LEVEL_12_0,
                IID_PPV_ARGS(&backend.device)))) {
            D3D12_COMMAND_QUEUE_DESC queueDescription{};
            queueDescription.Type=D3D12_COMMAND_LIST_TYPE_DIRECT;
            if(FAILED(backend.device->CreateCommandQueue(&queueDescription,
                IID_PPV_ARGS(&backend.queue)))) return false;
            D3D12_FEATURE_DATA_D3D12_OPTIONS5 options5{};
            if(SUCCEEDED(backend.device->CheckFeatureSupport(D3D12_FEATURE_D3D12_OPTIONS5,
                &options5,sizeof(options5))))
            backend.raytracingSupported=options5.RaytracingTier>=D3D12_RAYTRACING_TIER_1_0;
            backend.adapter=candidate;
            return true;
        }
    }
    return false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_available(JNIEnv*,jclass) {
    Backend backend;
    return create_backend(backend) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_create(JNIEnv*,jclass) {
    auto* backend=new Backend();
    if(!create_backend(*backend)) { delete backend;return 0; }
    return reinterpret_cast<jlong>(backend);
}

extern "C" JNIEXPORT jstring JNICALL
Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_adapterName(JNIEnv* env,jclass,jlong handle) {
    auto* backend=reinterpret_cast<Backend*>(handle);
    if(!backend||!backend->adapter) return env->NewStringUTF("");
    DXGI_ADAPTER_DESC1 description{};
    if(FAILED(backend->adapter->GetDesc1(&description))) return env->NewStringUTF("");
    return env->NewString(reinterpret_cast<const jchar*>(description.Description),
        static_cast<jsize>(wcslen(description.Description)));
}

extern "C" JNIEXPORT void JNICALL
Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_destroy(JNIEnv*,jclass,jlong handle) {
    auto* backend=reinterpret_cast<Backend*>(handle);
    if(backend) { if(backend->fenceEvent) wait_for_gpu(*backend);if(backend->fenceEvent) CloseHandle(backend->fenceEvent);delete backend; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_raytracingSupported(JNIEnv*,jclass,jlong handle) {
    auto* backend=reinterpret_cast<Backend*>(handle);
    return backend&&backend->raytracingSupported ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_createSwapChain(JNIEnv*,jclass,jlong handle,
        jlong windowHandle,jint width,jint height) {
    auto* backend=reinterpret_cast<Backend*>(handle);
    if(!backend||!backend->queue||windowHandle==0||width<=0||height<=0) return JNI_FALSE;
    ComPtr<IDXGIFactory4> factory;
    if(FAILED(CreateDXGIFactory2(0,IID_PPV_ARGS(&factory)))) return JNI_FALSE;
    DXGI_SWAP_CHAIN_DESC1 description{};
    description.Width=static_cast<UINT>(width);description.Height=static_cast<UINT>(height);
    description.Format=DXGI_FORMAT_R8G8B8A8_UNORM;description.BufferCount=2;
    description.BufferUsage=DXGI_USAGE_RENDER_TARGET_OUTPUT;description.SwapEffect=DXGI_SWAP_EFFECT_FLIP_DISCARD;
    description.SampleDesc.Count=1;
    ComPtr<IDXGISwapChain1> swapChain;
    if(FAILED(factory->CreateSwapChainForHwnd(backend->queue.Get(),reinterpret_cast<HWND>(windowHandle),
            &description,nullptr,nullptr,&swapChain))) return JNI_FALSE;
    if(FAILED(swapChain.As(&backend->swapChain))) return JNI_FALSE;
    DXGI_SWAP_CHAIN_DESC1 actual{};backend->swapChain->GetDesc1(&actual);
    D3D12_DESCRIPTOR_HEAP_DESC heapDescription{};
    heapDescription.NumDescriptors=actual.BufferCount;heapDescription.Type=D3D12_DESCRIPTOR_HEAP_TYPE_RTV;
    if(FAILED(backend->device->CreateDescriptorHeap(&heapDescription,IID_PPV_ARGS(&backend->rtvHeap)))) return JNI_FALSE;
    backend->rtvStride=backend->device->GetDescriptorHandleIncrementSize(D3D12_DESCRIPTOR_HEAP_TYPE_RTV);
    backend->bufferCount=actual.BufferCount;
    backend->width=actual.Width;backend->height=actual.Height;
        if(FAILED(backend->device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT,
            IID_PPV_ARGS(&backend->allocator)))) return JNI_FALSE;
        if(FAILED(backend->device->CreateCommandList(0,D3D12_COMMAND_LIST_TYPE_DIRECT,backend->allocator.Get(),
            nullptr,IID_PPV_ARGS(&backend->commandList)))) return JNI_FALSE;
        if(FAILED(backend->commandList->Close())) return JNI_FALSE;
        if(FAILED(backend->device->CreateFence(0,D3D12_FENCE_FLAG_NONE,IID_PPV_ARGS(&backend->fence)))) return JNI_FALSE;
        backend->fenceEvent=CreateEventW(nullptr,FALSE,FALSE,nullptr);
        if(!backend->fenceEvent||!create_targets(*backend)||!create_test_pipeline(*backend)) return JNI_FALSE;
        return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_resizeSwapChain(JNIEnv*,jclass,jlong handle,
        jint width,jint height) {
    auto* backend=reinterpret_cast<Backend*>(handle);
    if(!backend||!backend->swapChain||width<=0||height<=0) return JNI_FALSE;
    if(!wait_for_gpu(*backend)) return JNI_FALSE;
    backend->buffers.clear();
    backend->rtvHeap.Reset();
    if(FAILED(backend->swapChain->ResizeBuffers(0,static_cast<UINT>(width),static_cast<UINT>(height),
            DXGI_FORMAT_UNKNOWN,0))) return JNI_FALSE;
    DXGI_SWAP_CHAIN_DESC1 actual{};backend->swapChain->GetDesc1(&actual);backend->bufferCount=actual.BufferCount;
    backend->width=actual.Width;backend->height=actual.Height;
    D3D12_DESCRIPTOR_HEAP_DESC heapDescription{};
    heapDescription.NumDescriptors=backend->bufferCount;heapDescription.Type=D3D12_DESCRIPTOR_HEAP_TYPE_RTV;
    if(FAILED(backend->device->CreateDescriptorHeap(&heapDescription,IID_PPV_ARGS(&backend->rtvHeap)))) return JNI_FALSE;
    return create_targets(*backend) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_clear(JNIEnv*,jclass,jlong handle,
        jfloat red,jfloat green,jfloat blue,jfloat alpha) {
    auto* backend=reinterpret_cast<Backend*>(handle);
    if(!backend||!backend->swapChain||!backend->allocator||!backend->commandList) return JNI_FALSE;
    const UINT index=backend->swapChain->GetCurrentBackBufferIndex();
    if(index>=backend->buffers.size()||FAILED(backend->allocator->Reset())) return JNI_FALSE;
    if(FAILED(backend->commandList->Reset(backend->allocator.Get(),nullptr))) return JNI_FALSE;
    D3D12_RESOURCE_BARRIER barrier{};barrier.Type=D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
    barrier.Transition.pResource=backend->buffers[index].Get();barrier.Transition.Subresource=D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    barrier.Transition.StateBefore=D3D12_RESOURCE_STATE_PRESENT;barrier.Transition.StateAfter=D3D12_RESOURCE_STATE_RENDER_TARGET;
    backend->commandList->ResourceBarrier(1,&barrier);
    auto target=backend->rtvHeap->GetCPUDescriptorHandleForHeapStart();target.ptr+=index*backend->rtvStride;
    const FLOAT color[]={red,green,blue,alpha};backend->commandList->ClearRenderTargetView(target,color,0,nullptr);
    D3D12_VIEWPORT viewport{0,0,static_cast<FLOAT>(backend->width),static_cast<FLOAT>(backend->height),0,1};
    D3D12_RECT scissor{0,0,static_cast<LONG>(backend->width),static_cast<LONG>(backend->height)};
    backend->commandList->RSSetViewports(1,&viewport);backend->commandList->RSSetScissorRects(1,&scissor);
    backend->commandList->SetPipelineState(backend->pipeline.Get());backend->commandList->SetGraphicsRootSignature(backend->rootSignature.Get());
    backend->commandList->OMSetRenderTargets(1,&target,FALSE,nullptr);
    backend->commandList->IASetPrimitiveTopology(D3D_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    backend->commandList->DrawInstanced(3,1,0,0);
    barrier.Transition.StateBefore=D3D12_RESOURCE_STATE_RENDER_TARGET;barrier.Transition.StateAfter=D3D12_RESOURCE_STATE_PRESENT;
    backend->commandList->ResourceBarrier(1,&barrier);
    if(FAILED(backend->commandList->Close())) return JNI_FALSE;
    ID3D12CommandList* lists[]={backend->commandList.Get()};backend->queue->ExecuteCommandLists(1,lists);
    return wait_for_gpu(*backend) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_present(JNIEnv*,jclass,jlong handle) {
    auto* backend=reinterpret_cast<Backend*>(handle);
    return backend&&backend->swapChain&&SUCCEEDED(backend->swapChain->Present(1,0)) ? JNI_TRUE : JNI_FALSE;
}

static bool create_test_pipeline(Backend& backend) {
    static const char* source=R"(
struct Output { float4 position : SV_POSITION; float2 uv : TEXCOORD0; };
Output vs(uint id : SV_VertexID) {
    float2 positions[3] = { float2(-1,-1), float2(-1,3), float2(3,-1) };
    Output output; output.position=float4(positions[id],0,1); output.uv=positions[id]*0.5+0.5; return output;
}
float4 ps(Output input) : SV_TARGET {
    float3 top=float3(0.04,0.22,0.58), bottom=float3(0.01,0.03,0.10);
    float3 color=lerp(bottom,top,input.uv.y);
    float grid=(step(0.98,frac(input.uv.x*12))+step(0.98,frac(input.uv.y*8)))*0.12;
    return float4(color+grid,1);
}
)";
    ComPtr<ID3DBlob> vertex,fragment,errors;
    if(FAILED(D3DCompile(source,strlen(source),"dx12_test.hlsl",nullptr,nullptr,"vs","vs_5_0",
            D3DCOMPILE_OPTIMIZATION_LEVEL3,0,&vertex,&errors))) return false;
    if(FAILED(D3DCompile(source,strlen(source),"dx12_test.hlsl",nullptr,nullptr,"ps","ps_5_0",
            D3DCOMPILE_OPTIMIZATION_LEVEL3,0,&fragment,&errors))) return false;
    D3D12_ROOT_SIGNATURE_DESC rootDescription{};
    rootDescription.Flags=D3D12_ROOT_SIGNATURE_FLAG_ALLOW_INPUT_ASSEMBLER_INPUT_LAYOUT;
    ComPtr<ID3DBlob> serializedRoot;
    if(FAILED(D3D12SerializeRootSignature(&rootDescription,D3D_ROOT_SIGNATURE_VERSION_1,
            &serializedRoot,&errors))) return false;
    if(FAILED(backend.device->CreateRootSignature(0,serializedRoot->GetBufferPointer(),serializedRoot->GetBufferSize(),
            IID_PPV_ARGS(&backend.rootSignature)))) return false;
    D3D12_RASTERIZER_DESC rasterizer{};rasterizer.FillMode=D3D12_FILL_MODE_SOLID;rasterizer.CullMode=D3D12_CULL_MODE_NONE;rasterizer.DepthClipEnable=TRUE;
    D3D12_BLEND_DESC blend{};blend.RenderTarget[0].RenderTargetWriteMask=D3D12_COLOR_WRITE_ENABLE_ALL;
    D3D12_GRAPHICS_PIPELINE_STATE_DESC pipeline{};pipeline.pRootSignature=backend.rootSignature.Get();
    pipeline.VS={vertex->GetBufferPointer(),vertex->GetBufferSize()};pipeline.PS={fragment->GetBufferPointer(),fragment->GetBufferSize()};
    pipeline.RasterizerState=rasterizer;pipeline.BlendState=blend;pipeline.PrimitiveTopologyType=D3D12_PRIMITIVE_TOPOLOGY_TYPE_TRIANGLE;
    pipeline.NumRenderTargets=1;pipeline.RTVFormats[0]=DXGI_FORMAT_R8G8B8A8_UNORM;pipeline.SampleDesc.Count=1;
    return SUCCEEDED(backend.device->CreateGraphicsPipelineState(&pipeline,IID_PPV_ARGS(&backend.pipeline)));
}
