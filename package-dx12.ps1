param([switch]$SkipNativeBuild)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (!$IsWindows -and $env:OS -ne "Windows_NT") { throw "Windows x64 is required to package the EXE." }
if (!$SkipNativeBuild) { & "$PSScriptRoot/build-dx12.ps1" }

$packagingRoot = Join-Path $PSScriptRoot "target/windows-package"
# Only clear this script's generated staging/output directories.
if (Test-Path $packagingRoot) { Remove-Item -Recurse -Force $packagingRoot }
$inputDirectory = Join-Path $packagingRoot "input"
$outputDirectory = Join-Path $packagingRoot "output"
$nativeDirectory = Join-Path $inputDirectory "native"
New-Item -ItemType Directory -Force $nativeDirectory, $outputDirectory | Out-Null

& mvn --batch-mode package "-DskipTests"
if ($LASTEXITCODE -ne 0) { throw "Java package build failed" }
$dependencies = @("--batch-mode", "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies",
    "-DincludeScope=runtime", "-DincludeArtifactIds=lwjgl,lwjgl-glfw,lwjgl-opengl,lwjgl-egl,joml",
    "-DoutputDirectory=$inputDirectory")
& mvn @dependencies
if ($LASTEXITCODE -ne 0) { throw "Runtime dependency collection failed" }
Copy-Item "target/viergewinnt-1.0-SNAPSHOT.jar" "$inputDirectory/game.jar"
foreach ($file in @("viergewinnt_dx12.dll", "amd_fidelityfx_upscaler_dx12.dll", "libxess.dll", "libxess_fg.dll", "libxell.dll", "FSR-LICENSE.md", "XESS-LICENSE.txt")) {
    Copy-Item "target/dx12/Release/$file" $nativeDirectory
}
# Preserve project notices and third-party notices embedded in jars/runtime.
Copy-Item "docs/dx12-rendering.md" "$inputDirectory/DX12-INFO.md"
if (Test-Path "THIRD_PARTY_NOTICES.md") { Copy-Item "THIRD_PARTY_NOTICES.md" $inputDirectory }
Get-ChildItem -File -Filter "LICENSE*" | Copy-Item -Destination $inputDirectory

$jpackage = Join-Path $env:JAVA_HOME "bin/jpackage.exe"
$options = @("--type", "app-image", "--name", "4Gewinnt-DX12", "--app-version", "1.0.0",
    "--input", $inputDirectory, "--dest", $outputDirectory, "--main-jar", "game.jar",
    "--main-class", "de.viergewinnt.Dx12Launcher", "--vendor", "Artificial-Dumbness-GMBH",
    "--description", "4 Gewinnt - DirectX 12 Pathtracing Edition",
    "--add-modules", "java.desktop,java.logging,java.management,jdk.unsupported",
    "--java-options", "--enable-native-access=ALL-UNNAMED")
& $jpackage @options
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

$app = Join-Path $outputDirectory "4Gewinnt-DX12"
$exe = Join-Path $app "4Gewinnt-DX12.exe"
if (!(Test-Path $exe)) { throw "Packaged EXE missing" }
@"
4 GEWINNT - DX12

1. Das komplette ZIP in einen Ordner entpacken.
2. 4Gewinnt-DX12.exe starten. Java/Maven muessen nicht installiert sein.
3. ESC oeffnet das Grafikmenue; WASD/Maus bewegen die Kamera, 1-7 werfen Steine.

app und runtime muessen neben der EXE bleiben.
Windows 10/11 x64 und ein aktueller DirectX-12-Grafiktreiber werden benoetigt.
Die EXE ist nicht digital signiert.
FSR/XeSS/DXR sind von GPU und Treiber abhaengig.
Bei Fehlern: %USERPROFILE%/.viergewinnt/dx12.log

Diese Version ist ein Testbuild. Build- und Paketpruefungen ersetzen keinen GPU-Test.
"@ | Set-Content -Encoding utf8 (Join-Path $app "START-HIER.txt")

# Verify relocation, spaces in the path, and a working directory outside the app.
$relocated = Join-Path $packagingRoot "Relocated package test"
Copy-Item -Recurse $app $relocated
$report = Join-Path $packagingRoot "package-verification.txt"
$quotedReport = '"' + $report + '"'
$process = Start-Process -FilePath (Join-Path $relocated "4Gewinnt-DX12.exe") -ArgumentList @("--verify-package", $quotedReport) -WorkingDirectory $env:TEMP -PassThru
if (!$process.WaitForExit(120000)) { $process.Kill();throw "Packaged EXE verification timed out" }
if ($process.ExitCode -ne 0 -or !(Test-Path $report)) {
    if (Test-Path $report) { Get-Content $report }
    throw "Packaged EXE verification failed: exit $($process.ExitCode)"
}
Get-Content $report
if (!(Select-String -Path $report -Pattern "^PASS:" -Quiet)) { throw "Package verification did not pass" }
Copy-Item $report (Join-Path $app "package-verification.txt")
Write-Host "Portable Windows EXE ready: $exe"
