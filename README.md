# 4 Gewinnt – Compute Pathtracing

`Main` startet ein leeres Brett mit Rot am Zug. `--console` führt nur das bisherige
Konsolenbeispiel aus. `Window.create()` bleibt nutzbar; `create(Board)` übernimmt ein Brett
und setzt mit dem nächsten Spieler nach dem letzten erfolgreichen Zug fort.

## Start

JDK 17 oder neuer und Maven installieren. Das Projekt kompiliert mit `--release 17`;
Java 25 ist nicht erforderlich.
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
Der Fenstertitel zeigt Spieler, Gewinner oder Unentschieden. Vier gleiche Steine
horizontal, vertikal oder diagonal beenden die Partie; weitere Züge sind gesperrt.
R startet ein neues Spiel. Ungültige Züge und volle Spalten wechseln den Spieler nicht.
`Board.dropPiece(column, player)` erlaubt weiterhin explizite Farben für Szenenaufbau
und Tests; die interaktive Eingabe verwendet `getNextPlayer()`.

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

`mvn test` prüft Spiellogik (ungültige Züge, Schwerkraft, Spielerwechsel, beide Farben
in allen Gewinnrichtungen, Spielende, Unentschieden und Neustart) sowie BVH-Blattabdeckung, Bounds, maximale
Tiefe, degenerierte Geometrie, leere Szene und 6000 deterministische Vergleiche
von BVH gegen unabhängige Brute-Force-Treffertests. Ein optionaler Linux/EGL-Smoke-Test prüft echte LWJGL-Uploads, Rendering, Reset,
Resize, endliche/nichtleere Ausgabe und pixelweisen BVH-/Brute-Force-Vergleich:

```sh
EGL_PLATFORM=surfaceless mvn test-compile exec:java -Dlwjgl.natives=natives-linux -Dexec.mainClass=de.viergewinnt.renderer.RendererSmokeTest -Dexec.classpathScope=test
```

Validierung der Fehlerkorrekturen: alle Haupt- und Testklassen direkt mit dem
Java-17-Compiler und den echten LWJGL-3.4.3-Bibliotheken kompiliert; BoardTest und
BVHTest erfolgreich. LWJGL/EGL-Smoke-Test auf llvmpipe erfolgreich, einschließlich
BVH-/Brute-Force-Bildvergleich für alle sechs Kombinationen aus 8×8, 16×8,
16×16 und RGBA32F/RGBA16F. Der Maven-Lauf konnte in der Prüfungsumgebung wegen
DNS-Problemen beim Abhängigkeitsdownload nicht abgeschlossen werden.
Die direkte Kompilierung mit `--release 17`
war erfolgreich. Interaktive Eingabe, Windows-Treiber und
Intel-UHD-Leistung müssen zusätzlich auf der Zielhardware geprüft werden.

Dieser Stand setzt das im Auftrag priorisierte Zwischenziel um. Noch nicht enthalten:
OBJ/glTF-Dateiimport (Mesh-Daten lassen sich programmatisch übergeben), separate Kugel-Demo,
Metal/Glass/PBR-Texturen, Next Event Estimation/MIS, adaptives Sampling, Denoising,
Motion Vectors und zeitliche Reprojektion. Das Licht wird derzeit durch zufällige
Pfade getroffen; entsprechend kann die Konvergenz langsam sein. Accumulation mittelt
bei ruhender Kamera, sie ersetzt keine Reprojektion bei Bewegung.
