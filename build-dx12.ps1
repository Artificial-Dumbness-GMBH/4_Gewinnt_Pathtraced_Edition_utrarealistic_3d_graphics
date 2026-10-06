param([switch]$WithoutVendorSDKs, [string]$BuildDirectory = "target/dx12")
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Download([string]$Url, [string]$Destination) {
    if (Test-Path $Destination) { return }
    New-Item -ItemType Directory -Force (Split-Path $Destination) | Out-Null
    $temporary = "$Destination.download"
    try {
        Invoke-WebRequest -Uri $Url -OutFile $temporary
        Move-Item $temporary $Destination
    } finally {
        if (Test-Path $temporary) { Remove-Item $temporary }
    }
}

$sdkDirectory = Join-Path $PSScriptRoot "target/sdk"
$dxcDirectory = Join-Path $sdkDirectory "dxc-v1.9.2607"
$archive = Join-Path $sdkDirectory "dxc_2026_07_29.zip"
Download "https://github.com/microsoft/DirectXShaderCompiler/releases/download/v1.9.2607/dxc_2026_07_29.zip" $archive
if (!(Test-Path "$dxcDirectory/bin/x64/dxc.exe")) { Expand-Archive $archive $dxcDirectory -Force }
$configure = @("-S", "src/main/native", "-B", $BuildDirectory, "-A", "x64", "-DDXC_EXECUTABLE=$dxcDirectory/bin/x64/dxc.exe")
$fsrDirectory = Join-Path $sdkDirectory "fsr-v2.3.0"
$dlssDirectory = Join-Path $sdkDirectory "dlss-374959484e79a640feaba44c93ac8cfb0a03f5b5"
$xessDirectory = Join-Path $sdkDirectory "xess-v3.0.2"
if (!$WithoutVendorSDKs) {
    $fsrBase = "https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/v2.3.0"
    foreach ($file in @("Kits/FidelityFX/api/include/ffx_api.h", "Kits/FidelityFX/api/include/ffx_api_types.h",
        "Kits/FidelityFX/api/include/dx12/ffx_api_dx12.h", "Kits/FidelityFX/upscalers/include/ffx_upscale.h",
        "Kits/FidelityFX/denoisers/include/ffx_denoiser.h", "Kits/FidelityFX/signedbin/amd_fidelityfx_denoiser_dx12.dll",
        "Kits/FidelityFX/signedbin/amd_fidelityfx_upscaler_dx12.dll", "docs/license.md")) {
        Download "$fsrBase/$file" "$fsrDirectory/$file"
    }
    $dlssBase = "https://raw.githubusercontent.com/NVIDIA/DLSS/374959484e79a640feaba44c93ac8cfb0a03f5b5"
    foreach ($file in @("include/nvsdk_ngx.h", "include/nvsdk_ngx_defs.h", "include/nvsdk_ngx_params.h", "include/nvsdk_ngx_helpers.h",
        "include/nvsdk_ngx_helpers_d3d.h", "include/nvsdk_ngx_helpers_dlssd_d3d.h", "include/nvsdk_ngx_defs_dlssd.h", "include/nvsdk_ngx_params_dlssd.h",
        "lib/Windows_x86_64/x64/nvsdk_ngx_s.lib", "lib/Windows_x86_64/x64/nvsdk_ngx_s_dbg.lib",
        "lib/Windows_x86_64/rel/nvngx_dlss.dll", "lib/Windows_x86_64/rel/nvngx_dlssd.dll", "LICENSE.txt")) {
        Download "$dlssBase/$file" "$dlssDirectory/$file"
    }
    $xessBase = "https://raw.githubusercontent.com/intel/xess/v3.0.2"
    foreach ($file in @("inc/xess/xess.h", "inc/xess/xess_d3d12.h", "inc/xess_fg/xefg_swapchain.h", "inc/xess_fg/xefg_swapchain_d3d12.h",
        "inc/xell/xell.h", "inc/xell/xell_d3d12.h", "bin/libxess.dll", "bin/libxess_fg.dll", "bin/libxell.dll", "LICENSE.txt")) {
        Download "$xessBase/$file" "$xessDirectory/$file"
    }
    $configure += @("-DFSR_ROOT=$fsrDirectory", "-DXESS_ROOT=$xessDirectory", "-DDLSS_ROOT=$dlssDirectory")
} else {
    # Explicitly clear cached SDK roots when reusing an existing build directory.
    $configure += @("-DFSR_ROOT=", "-DXESS_ROOT=", "-DDLSS_ROOT=")
}
& cmake @configure
if ($LASTEXITCODE -ne 0) { throw "CMake configuration failed" }
& cmake --build $BuildDirectory --config Release --parallel
if ($LASTEXITCODE -ne 0) { throw "DX12 build failed" }
if (!$WithoutVendorSDKs) {
    Copy-Item "$fsrDirectory/docs/license.md" "$BuildDirectory/Release/FSR-LICENSE.md"
    Copy-Item "$dlssDirectory/LICENSE.txt" "$BuildDirectory/Release/DLSS-LICENSE.txt"
    Copy-Item "$xessDirectory/LICENSE.txt" "$BuildDirectory/Release/XESS-LICENSE.txt"
}
Write-Host "DX12 bridge built in $BuildDirectory/Release"

