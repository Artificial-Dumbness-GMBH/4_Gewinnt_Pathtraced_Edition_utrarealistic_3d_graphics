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
$fsrDirectory = Join-Path $sdkDirectory "fsr-v2.2.0"
$xessDirectory = Join-Path $sdkDirectory "xess-v3.0.2"
if (!$WithoutVendorSDKs) {
    $fsrBase = "https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/v2.2.0"
    foreach ($file in @("Kits/FidelityFX/api/include/ffx_api.h", "Kits/FidelityFX/api/include/ffx_api_types.h",
        "Kits/FidelityFX/api/include/dx12/ffx_api_dx12.h", "Kits/FidelityFX/upscalers/include/ffx_upscale.h",
        "Kits/FidelityFX/signedbin/amd_fidelityfx_upscaler_dx12.dll", "docs/license.md")) {
        Download "$fsrBase/$file" "$fsrDirectory/$file"
    }
    $xessBase = "https://raw.githubusercontent.com/intel/xess/v3.0.2"
    foreach ($file in @("inc/xess/xess.h", "inc/xess/xess_d3d12.h", "bin/libxess.dll", "LICENSE.txt")) {
        Download "$xessBase/$file" "$xessDirectory/$file"
    }
    $configure += @("-DFSR_ROOT=$fsrDirectory", "-DXESS_ROOT=$xessDirectory")
} else {
    # Explicitly clear cached SDK roots when reusing an existing build directory.
    $configure += @("-DFSR_ROOT=", "-DXESS_ROOT=")
}
& cmake @configure
if ($LASTEXITCODE -ne 0) { throw "CMake configuration failed" }
& cmake --build $BuildDirectory --config Release --parallel
if ($LASTEXITCODE -ne 0) { throw "DX12 build failed" }
if (!$WithoutVendorSDKs) {
    Copy-Item "$fsrDirectory/docs/license.md" "$BuildDirectory/Release/FSR-LICENSE.md"
    Copy-Item "$xessDirectory/LICENSE.txt" "$BuildDirectory/Release/XESS-LICENSE.txt"
}
Write-Host "DX12 bridge built in $BuildDirectory/Release"
