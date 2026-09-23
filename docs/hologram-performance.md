# Hologramm und Optimierung: Validierung

Referenz: Commit `49c7a2567d81942ba3c798809d3fe803dee755e3`.
Optimierter Stand: der Commit, der diesen Bericht enthält.

## Reproduzierbarer Renderer-Vergleich

- Mesa llvmpipe (LLVM 20.1.2, 256 bits), EGL ohne Fensteroberfläche.
- 320×180 **native interne und Ausgabeauflösung**, RGBA32F, 2 Samples/Frame,
  3 Bounces, 14 vorgegebene Spielzüge; keine Änderung an Licht, Materialien oder Szene.
- Denoising aus; das neue Hologramm ist in diesem isolierten Renderer-Vergleich
  nicht enthalten. Keine dynamische Auflösung, kein Upscaler.
- 4 Aufwärmframes; anschließend 5 Messblöcke à 4 Frames, synchronisiert mit
  `glFinish()`. Angegeben ist der Median der mittleren Framezeiten je Block.

| Stand | Zeit pro Frame |
|---|---:|
| Vorher | 164,653 ms |
| Optimiert | 136,806 ms |

Das sind **16,9 % weniger Framezeit** in dieser lokalen Software-Renderer-Messung.
Dies ist keine Frameratenzusage für Windows oder eine dedizierte GPU; das neue
Hologramm verursacht zusätzlich einen kleinen, separat ausgeführten Zeichenpass.

Nach 48 Samples/Pixel waren die linearen RGBA32F-Akkumulationsdaten exakt gleich:
maximale Kanalabweichung **0,0**, byteidentische Dateien (921600 Bytes).
SHA-256 beider Dateien: `e7e85c7f8a98ae3f2c6a60cc9e942cfdf677491386ade88551a7b1759b81552e`.

Der Test ist als `de.viergewinnt.renderer.RendererBenchmark` enthalten. Ein
optionales erstes Argument speichert RGBA-Float32-Daten in Little-Endian-Reihenfolge.
Auf Linux mit JDK/Maven:

```sh
EGL_PLATFORM=surfaceless mvn test-compile exec:java -Dlwjgl.natives=natives-linux -Dexec.mainClass=de.viergewinnt.renderer.RendererBenchmark -Dexec.classpathScope=test -Dexec.args=frame.f32
```

## Änderungen ohne Reduktion der Bildqualität

- Deterministische Normalen-/Tiefen-/Albedo-Guides nur im ersten Frame nach Reset
  statt pro Frame berechnen. Kamera, Szene und Größe lösen weiterhin Reset aus.
- Schattenstrahlen auf das Lichtsegment begrenzen und beim ersten Blocker abbrechen.
- Dreiecksnormalen nur für den endgültigen nächsten Treffer auswerten.
- Bei reiner Belichtungsänderung das vorhandene Denoiser-Ergebnis wiederverwenden.
- Während des Einregelns der Pause nur einmal präsentieren statt zweimal pro Frame.
- Kamerabasis zwischenspeichern und Cursorpuffer wiederverwenden.

## Hologramm und Regressionen

`HUDTest` prüft Rot/Blau am Zug, beide Gewinner, Unentschieden, abgewiesene Züge,
Neustart und Abstand zum Spielfeld. `HologramSmokeTest` prüft im echten OpenGL-Kontext
räumliche Projektion, Text-/Farbwechsel und vollständige Verdeckung durch eine
vorgelagerte Oberfläche. Der vorhandene EGL-Test mit BVH-/Brute-Force-Vergleich,
Denoiserwechsel, Settings und sechs Format-/Workgroup-Varianten ist erfolgreich.

Quellen wurden mit ECJ/Java 17 gegen LWJGL 3.4.3 kompiliert. Der konfigurierte
Maven-/JDK-25-Build und interaktive Windows-Zielhardware sind weiterhin separat
zu prüfen. Die Vorschauen zeigen echte native 960×540-Ausgaben mit 64 spp.
