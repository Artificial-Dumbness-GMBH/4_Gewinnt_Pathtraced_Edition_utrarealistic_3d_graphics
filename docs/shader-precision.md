# Shader-Präzision und AMD Packed FP16

## Auswahl

| Einstellung | Verhalten |
| --- | --- |
| `pt.precision=auto` (Standard) | Auf AMD FP16 anfordern, sonst FP32 |
| `pt.precision=fp32` | Keine FP16-Arithmetik im Pathtracing-Shader |
| `pt.precision=fp16` | FP16 unabhängig vom Hersteller anfordern |

FP16 setzt die tatsächlich gemeldete Erweiterung `GL_AMD_gpu_shader_half_float`
oder alternativ `GL_NV_gpu_shader5` voraus. Der Erweiterungsname allein gilt nicht
als AMD-Geräteerkennung. Vendor und Renderer werden separat geprüft (auch ATI- und
Mesa/Radeon-Bezeichnungen). Nicht unterstützte Anforderungen verwenden FP32.
Scheitert der optionale Shader beim Kompilieren oder Linken, wird dessen Programm
freigegeben und genau einmal der FP32-Shader übersetzt. Fehler auch im Basispfad
werden weiterhin gemeldet. Die Startmeldung zeigt den real gewählten Pfad.

## Geltungsbereich

Die Materialfarbauswertung verwendet explizite `f16vec4`-Operationen für F0-Mischung,
Fresnel-Farbmischung und den diffusen Farbanteil. Die AMD-Erweiterung stellt dafür
Half-FMA bereit. Der NV-Pfad verwendet Half-Multiplikation und -Addition, ohne einen
von dieser Erweiterung nicht zugesicherten Half-FMA-Overload vorauszusetzen.

Numerisch empfindliche Berechnungen bleiben FP32: Strahl-/Dreiecksschnitt,
BVH-Grenzen, Weltpositionen, Normalen, GGX-Verteilung und Maskierung, PDFs,
Lichtgewichte, Pfaddurchsatz, HDR-Summen und temporale Reprojektion.
Der vorhandene Schalter `pt.half` ändert nur das Akkumulationstexturformat;
RGBA16F allein bedeutet keine FP16-Rechnung. Standard bleibt RGBA32F.

AMD beschreibt Rapid Packed Math als Möglichkeit zur erhöhten FP16-Rechenrate.
Explizite Vektortypen geben dem Compiler die dafür geeigneten Operationen.
Sie erzwingen weder eine konkrete Instruktionspaarung noch Dual-Issue oder einen
bestimmten Geschwindigkeitsgewinn des gesamten Pathtracers. Konvertierungskosten,
Strahltests und Speicherzugriffe können den Nutzen begrenzen.

Primärquellen:
- [AMD RDNA Performance Guide, 16-bit math](https://gpuopen.com/learn/rdna-performance-guide/#16-bit-math)
- [Khronos: AMD_gpu_shader_half_float](https://registry.khronos.org/OpenGL/extensions/AMD/AMD_gpu_shader_half_float.txt)
- [Khronos: NV_gpu_shader5](https://registry.khronos.org/OpenGL/extensions/NV/NV_gpu_shader5.txt)

## Validierung

Alle Java-Quellen wurden mit ECJ/Java 17 gegen LWJGL 3.4.3 kompiliert.

`ShaderPrecisionTest` (auch in `mvn test` registriert): AMD-/ATI-/Mesa-Erkennung,
Nicht-AMD-Standard, echte Erweiterungsprüfung, beide manuellen Modi, unbekannte
Optionen und ein injizierter Compilerfehler mit FP32-Fallback. Ein Fehler im
FP32-Basispfad wird nicht verschluckt.

`RendererSmokeTest` enthält `PrecisionRenderTest`: gleiche Szene und Samples mit
FP32 und angefordertem FP16, endliche HDR-Ausgabe, unveränderte Tiefen/Normalen
und Vergleich der Farbwerte. Auf Treibern mit einer unterstützten Erweiterung
muss der FP16-Shader tatsächlich übersetzen und darf höchstens 1,5 % relative
HDR-RMSE in dieser Testszene verursachen. Ohne Erweiterung muss der Fallback
exakt mit FP32 übereinstimmen.

**Hier ausgeführt:** EGL auf Mesa llvmpipe, keine der beiden FP16-Erweiterungen.
Beide Aufrufe liefen deshalb in FP32; relative HDR-RMSE 0 %, identische Guides.
Das prüft den Fallback, nicht native FP16-Arithmetik. Die vorhandenen Tests für
BVH, bewegte Coins, TAA, Denoiser, Resize/Pause und sechs Speicherformat-/Workgroup-
Kombinationen bestanden ebenfalls.

**Noch offen auf Zielhardware:** native AMD-/NVIDIA-Shaderkompilierung, Bildvergleich,
Windows-Eingabe, Maven/JDK 25 und Leistungs-/Instruktionsanalyse. Kein AMD-Speedup
oder Dual-Issue-Nachweis wird aus dem Software-Test abgeleitet.

Zum realen Vergleich zwei identische Starts mit `-Dpt.precision=fp32` bzw.
`-Dpt.precision=fp16` und `-Dpt.benchmark=true` verwenden. Kamera, Fenstergröße,
Brett und Einstellungen identisch halten, Aufwärmzeit berücksichtigen und die
Startmeldung kontrollieren: ein FP32-Fallback ist kein FP16-Benchmark.
Der separate EGL-`RendererBenchmark` meldet ebenfalls den aktiven Pfad; er
schaltet Denoiser und TAA aus, um den Pathtracer zu isolieren.
