# Branch 4-gewinnt-dx12-fertig

Basis: `codex/dx12-rendering-settings`, Commit `7326b3f56682c51a07f92b0436ea3d29ea760b43`.
Spielablauf, DX12-Menü, HUD, Raum, Materialien, À-Trous, DXR und XeSS-FG bleiben Bestandteil dieses Ports.
Der Branchname ist ein gewünschter Name, keine Aussage über bestandene GPU-Abnahmetests.

## Erweiterungen

| Funktion | Implementierung |
|---|---|
| Hardware-Raytracing | Bestehendes DXR 1.1: Inline-RayQuery, BLAS/TLAS und Updates bei fallenden Coins. Animation lädt nur den bewegten Vertexbereich hoch. |
| FSR 4 | Offizielle FSR-4.1-Upscaling-API im AMD SDK 2.3.0. Der aktive Provider wird angezeigt; ein älterer Provider wird nicht als FSR 4 bezeichnet. |
| DLSS SR | NGX-Kontext, SDK-Abfrage der Rendergrößen, Feature-Erstellung und Evaluate pro Frame. RTX/NGX-Unterstützung wird abgefragt. |
| DLSS Ray Reconstruction | Eigenes NGX-Ray-Reconstruction-Feature mit ungefiltertem HDR, diffusem/specularem Albedo, Welt-Normalen und gepackter Roughness, D3D-Tiefe, Motion und Kameramatrizen. Ersetzt den eigenen Denoiser und TAA. |
| XeSS SR / FG | Bestehende SDK-Anbindung. SR-Skalierung wird auf die vom SDK abgefragten Grenzen beschränkt. FG bleibt die vorhandene Intel-Integration. |
| AMD Ray Regeneration | Denoiser-API 1.2 mit vier getrennten, demodulierten direkten/indirekten Diffus-/Specular-Signalen. Separate lineare Tiefe, UV-Motion inklusive Tiefendelta und octahedrale Normalen/Roughness. Remodulation vor Upscaling. |
| Radiance Caching | Eigener experimenteller räumlicher GPU-Hash-Cache. Kein AMD-/NVIDIA-Neural-Cache. Nur raue dielektrische Sekundärtreffer; acht Trainingssamples vor Verwendung. |
| Regler | Render-Skalierung 33–100 %, Schärfe 0–100 %. Mausziehen, Tastaturbedienung, automatische Speicherung und Migration alter Settings. |

## Bedienung

- ESC → **RT & Upscaling**: RT-Modus, FSR / XeSS / DLSS, Preset, vorhandene Frame Generation und Render-Skalierung.
- Der Skalierungsregler gilt auch ohne SDK-Upscaler. XeSS und DLSS begrenzen ihn auf ihre gültigen Größen; die tatsächliche Eingangsgröße steht im Rendererstatus.
- Ein neues Qualitätspreset setzt den individuellen Skalierungswert zurück. Native AA kann je nach SDK feste Abmessungen haben.
- ESC → **Rekonstruktion**: Aus / DLSS RR / FSR RR und experimenteller Radiance Cache. DLSS RR wählt DLSS automatisch.
- ESC → **Anzeige & Filter**: Schärferegler und vorhandene Anzeigeoptionen.
- Änderungen an Reglern werden beim Loslassen übernommen. Ziehen über den Panelrand begrenzt den Wert; Loslassen außerhalb übernimmt ihn ebenfalls.
- ESC verwirft einen noch gezogenen Regler. Der Wechsel weg von DLSS deaktiviert dessen Ray Reconstruction.
- Fehlende SDKs deaktivieren ihre Auswahl. Eine fehlgeschlagene Initialisierung zeigt einen Fallback im Status; ein Dispatch-Fehler bricht den Frame ab.

## Build und Paket

```powershell
./build-dx12.ps1
ctest --test-dir target/dx12 -C Release --output-on-failure
mvn test
./package-dx12.ps1 -SkipNativeBuild
```

Der Helper verwendet AMD SDK `v2.3.0`, Intel SDK `v3.0.2` und NVIDIA DLSS
Commit `374959484e79a640feaba44c93ac8cfb0a03f5b5`. Er lädt die benötigten offiziellen
Header, NGX-Bibliotheken, Release-DLLs und Lizenztexte. Das Windows-Paket enthält
auch `amd_fidelityfx_denoiser_dx12.dll`, `nvngx_dlss.dll` und `nvngx_dlssd.dll`.
DLSS ist bei manueller Konfiguration über `DLSS_ROOT` optional und benötigt MSVC.
`-WithoutVendorSDKs` baut Software-BVH/DXR einschließlich des eigenen Cache ohne Hersteller-SDKs.

## Performance und Grenzen

Der Renderer vermeidet unveränderte HUD-/Menü-Uploads. SDK-Upscaling überspringt
eigene temporale und History-Pässe; Ray Reconstruction/Ray Regeneration überspringen
zusätzlich die eigenen räumlichen Denoiser. Jitternde Primärtreffer werden zwischen
Samples eines Pixels wiederverwendet. Die DXR-Animation kopiert nur den bewegten Coin.

Der Cache verwendet zwei je 512-KiB-Buffers: ein unveränderliches Snapshot zum
Lesen und ein atomar aktualisiertes Trainingsbuffer. Ein Achtel der Samples trainiert
ohne Cache-Abbruch; maximal 128 Trainingswerte je Slot. Position, Normalen und
Material bestimmen den Schlüssel. Hash-Kollisionen werden verworfen. Szene,
Animation und relevante Settings verwerfen den Cache. Es werden höchstens 64
Radiance-Einheiten pro Farbkanal gespeichert. Das ist eine bewusst approximative,
potenziell verzerrte experimentelle Beleuchtungsschätzung; Standard ist **Aus**.
Sie ersetzt keine Integration von AMD FSR Radiance Caching (ML Technical Preview).

Der Specular-Albedo-Guide verwendet zunächst eine Schlick-Näherung. Eine integrierte
GGX-BRDF-LUT und getrennte diffuse/speculare DLSS-Hit-Distance-Guides sind weitere
Qualitätsverbesserungen. Es gibt weiterhin einen konservativen Fence pro Frame;
mehrere Frames gleichzeitig und DLSS Frame Generation sind nicht hinzugefügt.
OpenGL-Welthologramme bleiben eine bekannte Paritätslücke des Ausgangsstands.

## Validierung

Der Branch-Workflow prüft Java-Regressionen, sämtliche HLSL-Entrypoints, den nativen
Windows-Build mit allen drei SDKs sowie den Build ohne SDKs, Kameramatrizen und
das portable EXE-Paket. Ein erfolgreicher Build bestätigt keine GPU-Laufzeitfunktion.

Am 6. Oktober 2026 bestanden alle genannten Schritte für Commit
`950993ed8ead87936e2de8881ceda83bf7dfc10b`
([Windows-CI](https://github.com/Artificial-Dumbness-GMBH/4_Gewinnt_Pathtraced_Edition_utrarealistic_3d_graphics/actions/runs/37423839641)).
Weitere Commits werden vom selben Workflow erneut geprüft.

Auf Windows mit D3D12-Debug-Layer abnehmen: DXR ↔ Software-Bildvergleich,
HDR-/Bewegungsdaten, alle Upscaler/Presets/Regler, RR/Ray Regeneration, Cache ein/aus,
Kamerafahrten, bewegte Coins, Resize, Minimieren, Neustart, DLL-Ausfall und Providerwahl.
FPS, GPU-Zeit, VRAM und Bildfehler vor/nach der Änderung messen. In dieser Arbeitsumgebung
stehen weder eine Windows-GPU noch ein funktionierender Java-/DXC-Toolchain bereit.
