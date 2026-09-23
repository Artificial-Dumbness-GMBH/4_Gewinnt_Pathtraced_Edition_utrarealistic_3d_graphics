# DX12-Renderer: Build, Funktionen und Prüfstatus

## Startbare Windows-EXE

Der Windows-Workflow erstellt zusätzlich das Download-Artefakt
**4Gewinnt-DX12-Windows-x64**. Das ZIP vollständig entpacken und
**4Gewinnt-DX12.exe** starten. Die Ordner `app` und `runtime` müssen neben der
EXE bleiben; Java und Maven werden auf dem Spielrechner nicht benötigt.
Der Launcher wählt DX12 ausdrücklich und zeigt Startfehler statt eines stillen
OpenGL-Fallbacks. Das Protokoll liegt unter `%USERPROFILE%/.viergewinnt/dx12.log`.

Lokal erstellt `./package-dx12.ps1` dasselbe portable Anwendungspaket mittels
JDK-`jpackage`. Die EXE ist nicht digital signiert. Der Pakettest startet die EXE
aus einem verschobenen Ordner mit Leerzeichen, prüft die mitgelieferte JVM,
Java-Abhängigkeiten, Ressourcen und das Laden der nativen DLLs. Er erzeugt
keinen Grafikadapter und bestätigt deshalb keine DX12-GPU-Funktion.

## Windows-Build

Benötigt werden Windows 10/11 x64, JDK 25, Maven, CMake und Visual Studio 2022
Build Tools mit **Desktopentwicklung mit C++** und Windows SDK.
Der PowerShell-Helper verwendet den Visual-Studio-x64-Generator.

```powershell
mvn test
./build-dx12.ps1
mvn compile exec:java "-Dpt.backend=dx12" "-Dpt.dx12.library=target/dx12/Release/viergewinnt_dx12.dll"
```

Der Helper lädt ausschließlich offizielle, fest versionierte Veröffentlichungen:

- [Microsoft DXC v1.9.2607](https://github.com/microsoft/DirectXShaderCompiler/releases/tag/v1.9.2607)
- [AMD FidelityFX SDK v2.2.0 / FSR Upscaling 4.1](https://github.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/tree/v2.2.0)
- [Intel XeSS SDK v3.0.2](https://github.com/intel/xess/tree/v3.0.2)

Header, DLLs und Lizenzen werden nach `target/sdk` geladen; die benötigten Laufzeit-DLLs
und Lizenztexte landen neben der gebauten Bridge. Downloads werden zwischengespeichert.
Für Offline-Builds müssen die Dateien dort bereits vorhanden sein. Die Hersteller-DLLs
werden nur aus dem Verzeichnis der Bridge geladen, nicht aus dem aktuellen Arbeitsverzeichnis.
Die Lizenztexte müssen bei Weitergabe der DLLs erhalten bleiben.

```powershell
# Build ohne optionale Hersteller-Abhängigkeiten
./build-dx12.ps1 -WithoutVendorSDKs -BuildDirectory target/dx12-basic
# D3D12-Debug-Layer, wenn Graphics Tools installiert sind
mvn compile exec:java "-Dpt.backend=dx12" "-Dpt.dx12.debug=true" "-Dpt.dx12.library=target/dx12/Release/viergewinnt_dx12.dll"
# Automatisches Ende nach 120 echten Frames; kein Leistungsnachweis
mvn compile exec:java "-Dpt.backend=dx12" "-Dpt.dx12SmokeFrames=120" "-Dpt.dx12.library=target/dx12/Release/viergewinnt_dx12.dll"
```

## Implementierte Pfade

| Auswahl | Implementierung / Bedingung |
|---|---|
| Auto-Raytracing | DXR 1.1 / Shader Model 6.5, falls unterstützt; sonst Software-BVH |
| Software-Raytracing | GPU-Compute mit SAH-BVH, Shader Model 6.0, keine RT-Einheiten erforderlich |
| Hardware-Raytracing | Inline-RayQuery, BLAS/TLAS, bewegte Coins mit BLAS-Update und aktualisierter TLAS |
| FSR | Echte SDK-Dispatch-Anbindung, API-Version 4.1, HDR/Farbbild, Tiefe, Motion-Vektoren, Jitter und Reset |
| XeSS-SR | Echte SDK-Dispatch-Anbindung aus XeSS SDK 3.0.2; Eingangsauflösung wird beim SDK abgefragt |
| XeSS-FG | Proxy-Swapchain, XeLL-Marker, HUD-loses Bild, Tiefe und Motion; 2× bis maximal 4× nach SDK-Abfrage |
| Denoiser | Geometrie-/Material-gestützter Bilateralfilter sowie mehrstufiger À-Trous-Filter, 1–5 Durchgänge |
| Temporal | Rückprojektion, Tiefen-/Normalenprüfung und Nachbarschafts-Clipping; bei Upscaling übernimmt das SDK |

**FSR-4.1-API-Anbindung bedeutet nicht, dass jede GPU den FSR-4.1-ML-Provider verwendet.**
AMD kann je nach GPU/Treiber einen anderen Provider wählen. Der tatsächlich gemeldete
Provider steht im Fenstertitel und auf der Menüseite RT & Upscaling; es wird kein
FSR-3-Fallback als aktives FSR 4.1 ausgegeben.

Der DX12-Pfad übernimmt Brett, Raum, PBR-Materialien, Flächenlicht, Schatten,
Mehrfachreflexionen, Eingabe, fallende Steine, Pause und Neustart.
Er benutzt eine Bildschirm-HUD für Status und Tastenhinweise. Die bestehenden
weltverankerten OpenGL-Hologramme sind noch **nicht** nach DX12 portiert.

## Menü und Fallback

- **ESC → Grafik:** Denoiser, TAA, Filterstärke, 1–12 Bounces, Samples, Auflösungsgrenze, Belichtung.
- **ESC → RT & Upscaling:** RT-Modus, FSR/XeSS, Qualitätsstufe und Frame Generation.
- **ESC → Anzeige & Filter:** VSync, echte Render-FPS begrenzen, Filterdurchgänge, Nachschärfen.
- Mit aktivem SDK-Upscaler gelten dessen Auflösung und temporale Rekonstruktion; separate TAA-/Auflösungsschalter sind deaktiviert.
- Nicht verfügbare Funktionen sind deaktiviert. Gespeicherte, inzwischen nicht verfügbare Upscaling-/FG-Wünsche lassen sich ausschalten.
- Fehlende SDK-DLLs verhindern weder Software-DX12 noch DXR. Fehler bei Upscaler-Initialisierung führen zu nativem Rendering mit sichtbarer Meldung.
- Fehler beim DX12-Start erlauben OpenGL-Fallback. Ein Fehler nach Spielstart beendet den DX12-Lauf mit Fehlermeldung, statt das laufende Spiel still neu zu starten.
- Ein SDK-Dispatch-Fehler bricht den Frame ab, damit keine halbfertigen Ressourcen als gültiges Ergebnis angezeigt werden.
- VSync und FPS-Limit beziehen sich auf echte Frames. Der Titel nennt bei FG separat angeforderte Multiplikation und die zuletzt vom SDK gemeldeten präsentierten Frames.

## Architektur und Grenzen

CPU-Szenen werden in vier direkte ByteBuffer serialisiert. Software-BVH und DXR
nutzen dieselbe umsortierte Dreiecksfolge, damit Primitive-IDs und Materialien übereinstimmen.
Die Pipeline erzeugt lineares HDR, Normalen/Entfernung, Albedo/Material, D3D-Tiefe
und unjitterte aktuelle→vorherige Pixel-Motion-Vektoren; bewegte Coins werden berücksichtigt.
Tonemapping und sRGB-Konvertierung erfolgen nach Rekonstruktion. UI kommt zuletzt.

Konstanten besitzen pro Dispatch einen eigenen 512-Byte-ausgerichteten Uploadbereich.
Transitions/UAV-Barrieren trennen die Pässe. Ein Fence schützt Uploads und Allocator;
GPU-Wartezeiten haben ein 10-Sekunden-Limit und erkennen Device Removal.
Diese erste Integration wartet konservativ nach jedem echten Frame. Sie ist
noch nicht auf mehrere parallel laufende Frames oder maximale FG-Leistung optimiert.
DX12 rechnet FP32 und verwendet keinen der OpenGL-spezifischen Mixed-FP16-Pfade.

## Verifikation

Die Rekonstruktion wird unter Linux mit folgenden getrennten Prüfungen validiert:

- Java-Compiler und CPU-Regressionen einschließlich Menü, Settings-Migration und NativeScene-ABI.
- OpenGL-EGL-Smoke-Test auf Mesa llvmpipe: Software-Ausführung, kein Hardware-Leistungsnachweis.
- Microsoft DXC: Software-/Hardware-Trace und fünf Postprocessing-Entry-Points.
- Windows-x64-Cross-Build/Link mit LLVM-MinGW, mit und ohne optionale SDK-Header.

Der GitHub-Workflow ergänzt einen Windows/MSVC-Build und Java-25-Tests.
**Ein Build ist kein bestandener DX12-GPU-Test.** Windows-/GPU-Tests, echte FSR-/XeSS-Ausführung,
Bildvergleich DXR ↔ Software, Frame-Generation-Qualität, Ghosting und Latenz sind
in dieser Umgebung nicht ausführbar. Der PR bleibt deshalb ein Draft.

Vor Freigabe auf Windows prüfen: beide RT-Modi; alle Denoiser; Upscaler aus/Native-AA/
Quality/Balanced/Performance; FG aus/2×/3×/4× nach Verfügbarkeit; Resize/Minimieren;
Pause/Neustart; fallende Steine; alte Settings; fehlende SDK-DLLs; mehrere GPUs;
Debug-Layer ohne Ressourcen-, Descriptor- oder Fence-Fehler. Keine pauschalen FPS-
oder Qualitätsversprechen aus dem erfolgreichen Cross-Build ableiten.
