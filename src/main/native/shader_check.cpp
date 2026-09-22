#include <d3dcompiler.h>
#include <wrl.h>
#include <cstring>
#include <iostream>
#include "shaders.h"
int main() {
    struct Shader {const char* source;const char* entry;const char* profile;};
    const Shader shaders[]={{pathtraceSource,"main","cs_5_1"},{denoiseSource,"main","cs_5_1"},{presentSource,"vs","vs_5_1"},{presentSource,"ps","ps_5_1"}};
    for(const auto& shader:shaders) {
        Microsoft::WRL::ComPtr<ID3DBlob> code,error;
        HRESULT hr=D3DCompile(shader.source,strlen(shader.source),"embedded.hlsl",nullptr,nullptr,shader.entry,shader.profile,D3DCOMPILE_OPTIMIZATION_LEVEL3,0,&code,&error);
        if(FAILED(hr)) {if(error) std::cerr.write(static_cast<char*>(error->GetBufferPointer()),error->GetBufferSize());return 1;}
    }
    std::cout<<"All four DX12 shader entry points compiled.\n";
}
