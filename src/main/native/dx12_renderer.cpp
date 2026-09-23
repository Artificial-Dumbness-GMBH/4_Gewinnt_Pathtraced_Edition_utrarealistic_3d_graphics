#include "dx12_renderer.h"

void Backend::acceleration(float lift) {
    if(!dxr||settings[0]==1) return;
    if(accelerationBuilt&&!sceneDirty&&lift==previousLift) return;
    auto vertices=baseVertices;
    if(movingStart>=0) for(size_t i=size_t(movingStart)*4+1;i<vertices.size();i+=4) vertices[i]+=lift;
    upload(rtVertices.Get(),vertices.data(),vertices.size()*4);
    D3D12_RAYTRACING_GEOMETRY_DESC geo{};geo.Type=D3D12_RAYTRACING_GEOMETRY_TYPE_TRIANGLES;geo.Flags=D3D12_RAYTRACING_GEOMETRY_FLAG_OPAQUE;
    geo.Triangles.VertexBuffer={rtVertices->GetGPUVirtualAddress(),16};geo.Triangles.VertexCount=UINT(vertices.size()/4);geo.Triangles.VertexFormat=DXGI_FORMAT_R32G32B32_FLOAT;
    geo.Triangles.IndexBuffer=rtIndices->GetGPUVirtualAddress();geo.Triangles.IndexCount=triangleCount*3;geo.Triangles.IndexFormat=DXGI_FORMAT_R32_UINT;
    D3D12_BUILD_RAYTRACING_ACCELERATION_STRUCTURE_INPUTS input{};input.Type=D3D12_RAYTRACING_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL;
    input.Flags=D3D12_RAYTRACING_ACCELERATION_STRUCTURE_BUILD_FLAG_ALLOW_UPDATE|D3D12_RAYTRACING_ACCELERATION_STRUCTURE_BUILD_FLAG_PREFER_FAST_TRACE;
    input.NumDescs=1;input.DescsLayout=D3D12_ELEMENTS_LAYOUT_ARRAY;input.pGeometryDescs=&geo;
    D3D12_RAYTRACING_ACCELERATION_STRUCTURE_PREBUILD_INFO bi{};device->GetRaytracingAccelerationStructurePrebuildInfo(&input,&bi);
    auto topInput=input;topInput.Type=D3D12_RAYTRACING_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL;topInput.pGeometryDescs=nullptr;topInput.Flags=D3D12_RAYTRACING_ACCELERATION_STRUCTURE_BUILD_FLAG_PREFER_FAST_TRACE;
    D3D12_RAYTRACING_ACCELERATION_STRUCTURE_PREBUILD_INFO ti{};device->GetRaytracingAccelerationStructurePrebuildInfo(&topInput,&ti);
    if(!accelerationBuilt) {
        if(!bi.ResultDataMaxSizeInBytes||!ti.ResultDataMaxSizeInBytes) throw std::runtime_error("Invalid DXR prebuild sizes");
        blas=buffer(bi.ResultDataMaxSizeInBytes,D3D12_HEAP_TYPE_DEFAULT,D3D12_RESOURCE_STATE_RAYTRACING_ACCELERATION_STRUCTURE,D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS);
        tlas=buffer(ti.ResultDataMaxSizeInBytes,D3D12_HEAP_TYPE_DEFAULT,D3D12_RESOURCE_STATE_RAYTRACING_ACCELERATION_STRUCTURE,D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS);
        scratch=buffer(std::max({bi.ScratchDataSizeInBytes,bi.UpdateScratchDataSizeInBytes,ti.ScratchDataSizeInBytes}),D3D12_HEAP_TYPE_DEFAULT,D3D12_RESOURCE_STATE_UNORDERED_ACCESS,D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS);
        D3D12_RAYTRACING_INSTANCE_DESC inst{};inst.Transform[0][0]=inst.Transform[1][1]=inst.Transform[2][2]=1;inst.InstanceMask=255;inst.AccelerationStructure=blas->GetGPUVirtualAddress();
        instance=buffer(sizeof(inst));upload(instance.Get(),&inst,sizeof(inst));
    }
    D3D12_BUILD_RAYTRACING_ACCELERATION_STRUCTURE_DESC b{};b.Inputs=input;b.DestAccelerationStructureData=blas->GetGPUVirtualAddress();b.ScratchAccelerationStructureData=scratch->GetGPUVirtualAddress();
    if(accelerationBuilt) { b.Inputs.Flags|=D3D12_RAYTRACING_ACCELERATION_STRUCTURE_BUILD_FLAG_PERFORM_UPDATE;b.SourceAccelerationStructureData=b.DestAccelerationStructureData; }
    list->BuildRaytracingAccelerationStructure(&b,0,nullptr);barrier(blas.Get());barrier(scratch.Get());
    // Refresh TLAS bounds after a moving BLAS update.
    b={};b.Inputs=topInput;b.Inputs.InstanceDescs=instance->GetGPUVirtualAddress();b.DestAccelerationStructureData=tlas->GetGPUVirtualAddress();b.ScratchAccelerationStructureData=scratch->GetGPUVirtualAddress();
    list->BuildRaytracingAccelerationStructure(&b,0,nullptr);barrier(tlas.Get());accelerationBuilt=true;
}
void Backend::render(const float* camera,float lift,float milliseconds,const void* ui,int uiMode) {
    if(!configured||!triangleCount) throw std::runtime_error("Scene/settings not initialized");
    frameGeneration.marker(1);
    bool moved=lift!=previousLift||sceneDirty;
    // PreviousCamera.w stores jitter; compare basis xyz only.
    for(int v=0;v<4;v++) for(int i=0;i<3;i++) moved|=previousCamera[v*4+i]!=camera[v*4+i];
    if(moved) accumulation=0;
    float distance2=0,directionDot=0;for(int i=0;i<3;i++) { float d=camera[i]-previousCamera[i];distance2+=d*d;directionDot+=camera[4+i]*previousCamera[4+i]; }
    bool reset=!historyValid||sceneDirty||distance2>4||directionDot<.7f;
    checked(allocator->Reset(),"Reset allocator");checked(list->Reset(allocator.Get(),nullptr),"Reset command list");constantOffset=0;
    acceleration(lift);
    Constants c{};std::memcpy(c.camera,camera,sizeof(c.camera));std::memcpy(c.previous,previousCamera,sizeof(c.previous));
    c.counts[0]=vendor.active()?0:accumulation;c.counts[1]=sequence++;c.counts[2]=settings[5];c.counts[3]=settings[4];
    c.geometry[0]=triangleCount;c.geometry[1]=UINT(movingStart);c.geometry[2]=dxr&&settings[0]!=1;c.geometry[3]=settings[9]||vendor.active();
    UINT phase=std::max(1u,UINT(std::ceil(8.f*width*width/(float(renderWidth)*renderWidth))));
    float jx=c.geometry[3]?halton(c.counts[1]%phase+1,2):0,jy=c.geometry[3]?halton(c.counts[1]%phase+1,3):0;
    c.motion[0]=lift;c.motion[1]=previousLift;c.motion[2]=jx;c.motion[3]=jy;
    c.dimensions[0]=float(renderWidth);c.dimensions[1]=float(renderHeight);c.dimensions[2]=float(width);c.dimensions[3]=float(height);
    c.options[0]=display[0];c.options[1]=display[1];c.options[2]=display[2];
    c.options[3]=settings[9]&&!vendor.active()&&lift==previousLift?.85f/(1+.15f*accumulation):0;
    c.post[2]=!reset;c.post[3]=uiMode;
    for(int i=0;i<5;i++) transition(i,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);
    dispatch(c.geometry[2]?1:0,c,renderWidth,renderHeight);
    for(int i=0;i<5;i++) { barrier(textures[i].resource.Get());transition(i,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE); }
    UINT source=0;
    for(int i=0;i<(settings[3]==0?0:settings[3]==1?1:settings[8]);i++) {
        int dest=5+i%2;transition(dest,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);
        c.post[0]=i;c.post[1]=source;dispatch(2,c,renderWidth,renderHeight);
        transition(dest,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);source=dest;
    }
    c.post[1]=source;transition(9,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);dispatch(3,c,renderWidth,renderHeight);transition(9,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
    transition(7,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);transition(8,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);dispatch(4,c,renderWidth,renderHeight);
    transition(7,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);transition(8,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
    source=9;
    if(vendor.active()) {
        transition(10,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);
        vendor.execute(list.Get(),textures[9].resource.Get(),textures[3].resource.Get(),textures[4].resource.Get(),textures[10].resource.Get(),renderWidth,renderHeight,width,height,jx,jy,milliseconds,reset);
        barrier(textures[10].resource.Get());transition(10,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);source=10;
    }
    c.post[1]=source;transition(11,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);dispatch(5,c,width,height);transition(11,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
    if(uiMode!=0&&ui) {
        void* mapped=nullptr;D3D12_RANGE empty{0,0};checked(uiUpload->Map(0,&empty,&mapped),"Map UI upload");
        for(UINT y=0;y<660;y++) std::memcpy(static_cast<char*>(mapped)+uiFootprint.Offset+y*uiFootprint.Footprint.RowPitch,static_cast<const char*>(ui)+y*960*4,960*4);
        uiUpload->Unmap(0,nullptr);transition(13,D3D12_RESOURCE_STATE_COPY_DEST);
        D3D12_TEXTURE_COPY_LOCATION from{},to{};from.pResource=uiUpload.Get();from.Type=D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;from.PlacedFootprint=uiFootprint;
        to.pResource=textures[13].resource.Get();to.Type=D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;list->CopyTextureRegion(&to,0,0,0,&from,nullptr);transition(13,D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
    }
    transition(12,D3D12_RESOURCE_STATE_UNORDERED_ACCESS);dispatch(6,c,width,height);transition(12,D3D12_RESOURCE_STATE_COPY_SOURCE);
    frameGeneration.tag(list.Get(),textures[11].resource.Get(),textures[3].resource.Get(),textures[4].resource.Get(),camera,renderWidth,renderHeight,jx,jy,milliseconds,reset);
    auto target=back[swap->GetCurrentBackBufferIndex()].Get();
    D3D12_RESOURCE_BARRIER b{};b.Type=D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;b.Transition.pResource=target;b.Transition.Subresource=D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    b.Transition.StateBefore=D3D12_RESOURCE_STATE_PRESENT;b.Transition.StateAfter=D3D12_RESOURCE_STATE_COPY_DEST;list->ResourceBarrier(1,&b);
    list->CopyResource(target,textures[12].resource.Get());std::swap(b.Transition.StateBefore,b.Transition.StateAfter);list->ResourceBarrier(1,&b);
    checked(list->Close(),"Close frame");ID3D12CommandList* lists[]={list.Get()};frameGeneration.marker(2);queue->ExecuteCommandLists(1,lists);frameGeneration.marker(3);
    frameGeneration.marker(4);
    HRESULT presented=swap->Present(settings[10]?1:0,!settings[10]&&tearing?DXGI_PRESENT_ALLOW_TEARING:0);
    frameGeneration.marker(5);wait();checked(presented,"Present");frameGeneration.presented();
    std::memcpy(previousCamera,camera,16*sizeof(float));previousCamera[3]=jx;previousCamera[7]=jy;
    previousLift=lift;historyValid=true;sceneDirty=false;accumulation=std::min(accumulation+1,1000000u);
}
