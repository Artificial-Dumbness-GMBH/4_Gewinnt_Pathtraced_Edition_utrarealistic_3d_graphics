# 4 Gewinnt – Compute Pathtracing

Die vorhandenen `Board`-/`Player`-Klassen bleiben unverändert. `Main` erzeugt wie zuvor
sein Beispielbrett und öffnet danach den Renderer. `--console` führt nur das bisherige
Konsolenbeispiel aus. `Window.create()` bleibt nutzbar; `create(Board)` übernimmt ein Brett.

## Start

JDK 25 und Maven installieren. Das Projekt kompiliert Java-25-Quellcode.
Ein OpenGL-4.6-Kontext wird bevorzugt, mit Fallback auf 4.3 (Compute/SSBO-Mindestanforderung).
Windows-Natives sind wie bisher voreingestellt.

```sh
mvn test
mvn compile exec:java
mvn compile exec:java -Dexec.args=--console
# Linux x86_64: vorhandene Native-Auswahl überschreiben
mvn compile exec:java -Dlwjgl.natives=natives-linux
```

WASD bewegt die Kamera; rechte Maustaste halten und Maus bewegen dreht sie.
1–7 lässt abwechselnd rote/blaue Steine in die gewählte Spalte fallen; Escape beendet.
Die existierende Spiellogik enthält noch keine Gewinnererkennung.

## Implementierter Kern

Scene/Camera/Mesh → CPU-BVH (binäre 12-Bin-SAH) → einmaliger SSBO-Upload →
Compute-Pathtracing → progressiver Mittelwert → Reinhard-Tonemapping → sRGB-Ausgabe.

- Standard: maximal 960×540, 8×8 Workgroup, 3 Bounces, 1 Sample/Frame, RGBA32F.
- Dreiecksgeometrie für Brett, Boden, Lichtfläche und zylindrische Spielsteine.
- Diffuse Lambert-Materialien, Emission, Himmel, Cosinus-Hemisphere-Sampling;
  Russian Roulette ab dem dritten Treffer für längere Pfade.
- Kameraänderung, neue Szene und interne Auflösungsänderung setzen die Accumulation zurück.
- Resize verwendet Framebuffer-Pixel, hält das Seitenverhältnis und pausiert bei Minimierung.
- Fullscreen Triangle skaliert bilinear auf die Fenstergröße; FPS und spp im Fenstertitel.
- Shaderfehler enthalten Ressourcennamen und Compilerlog; GL-Ressourcen werden freigegeben.
- Szenendaten bleiben zwischen Zügen auf der GPU. Kamera-/Frame-Uniforms ändern sich pro Frame.

SSBO-Vertrag (`std430`, native Byte-Reihenfolge):

| Binding | Inhalt | Stride |
|---|---|---|
| 0 | Vertex: `vec4(position, 0)` | 16 Bytes |
| 1 | Triangle: `uvec4(a,b,c,material)` | 16 Bytes |
| 2 | Material: `vec4(baseColor,0)`, `vec4(emission,0)` | 32 Bytes |
| 3 | Node: `vec4(min,0)`, `vec4(max,0)`, `ivec4(left,right,first,count)` | 48 Bytes |

Blätter haben `count > 0`; innere Knoten `count == 0`. Blattbereiche zeigen auf die
**BVH-sortierten** Dreiecke. Maximale CPU-Baumtiefe 30 passt in den 32er Shader-Stack.
Parallele Strahlen werden beim AABB-Test explizit behandelt. Der erste Frame liest
keinen alten/undefinierten Texturinhalt. Image-/Texture-Barrieren synchronisieren Compute und Ausgabe.

## Diagnose und Intel-UHD-Messungen

```sh
# Compute-/Texture-/Ausgabe-Test ohne Traversierung
mvn compile exec:java -Dpt.gradient=true
# Referenzpfad für Vergleich mit BVH (langsam)
mvn compile exec:java -Dpt.bruteForce=true
# VSync aus, FPS/spp zusätzlich auf stdout
mvn compile exec:java -Dpt.benchmark=true -Dpt.groupX=16 -Dpt.groupY=8 -Dpt.bounces=4
# Auflösungsobergrenze und experimentelles Half-Float
mvn compile exec:java -Dpt.width=1920 -Dpt.height=1080 -Dpt.half=true
# Nach 8 Frames beenden; GL-Fehler führen zum Fehlschlag
mvn compile exec:java -Dpt.smokeFrames=8
```

Auf derselben GPU mit derselben Szene, Fenstergröße und unbewegter Kamera vergleichen:
Workgroups 8×8 / 16×8 / 16×16; Obergrenzen 960×540 / 1280×720 / 1920×1080;
Bounces 1 / 2 / 4 / 8; RGBA32F / RGBA16F. Das Fenster muss groß genug für die
gewählte interne Auflösung sein. Nach Aufwärmen mindestens 30 Sekunden messen.
FPS sind End-to-End-Werte, keine isolierten GPU-Zeitmessungen. Hardware, Treiber,
Auflösung und Parameter zusammen mit dem Ergebnis protokollieren.
RGBA16F kann durch Quantisierung bei hohen Samplezahlen stagnieren; RGBA32F bleibt Standard.
Es werden keine ungemessenen Intel-UHD-Frameraten zugesichert.

## Tests und bewusst spätere Ausbaustufen

`mvn test` führt einen CPU-Regressionstest aus: BVH-Blattabdeckung, Bounds, maximale
Tiefe, degenerierte Geometrie, leere Szene und 6000 deterministische Vergleiche
von BVH gegen unabhängige Brute-Force-Treffertests. Ein optionaler Linux/EGL-Smoke-Test prüft echte LWJGL-Uploads, Rendering, Reset,
Resize, endliche/nichtleere Ausgabe und pixelweisen BVH-/Brute-Force-Vergleich:

```sh
EGL_PLATFORM=surfaceless mvn test-compile exec:java -Dlwjgl.natives=natives-linux -Dexec.mainClass=de.viergewinnt.renderer.RendererSmokeTest -Dexec.classpathScope=test
```

Validierung dieses Stands: Maven `test` erfolgreich; alle Java-Klassen mit `--release 25`
kompiliert; sechs Compute-Varianten und die Ausgabe-Shader auf Mesa 4.5 kompiliert/gelinkt;
LWJGL/EGL-Smoke-Test auf llvmpipe erfolgreich. Interaktive Eingabe, Windows-Treiber und
Intel-UHD-Leistung müssen zusätzlich auf der Zielhardware geprüft werden.

Dieser Stand setzt das im Auftrag priorisierte Zwischenziel um. Noch nicht enthalten:
OBJ/glTF-Dateiimport (Mesh-Daten lassen sich programmatisch übergeben), separate Kugel-Demo,
Metal/Glass/PBR-Texturen, Next Event Estimation/MIS, adaptives Sampling, Denoising,
Motion Vectors und zeitliche Reprojektion. Das Licht wird derzeit durch zufällige
Pfade getroffen; entsprechend kann die Konvergenz langsam sein. Accumulation mittelt
bei ruhender Kamera, sie ersetzt keine Reprojektion bei Bewegung.
