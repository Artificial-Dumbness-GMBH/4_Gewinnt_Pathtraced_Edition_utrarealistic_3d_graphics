# TAA: Implementierung und Validierung

`TemporalAA` ergänzt einen GPU-Compute-Pass in der bestehenden Render-Auflösung,
vor Tonemapping und den UI-Overlays. À-Trous wird vorher ausgeführt; der eigene
Bilateralfilter bleibt im Tonemapping-Pass. Es wird kein temporaler Upscaler verwendet.

Der Verlauf besteht aus zwei HDR-Farb- und zwei Normalen-/Tiefentexturen im
Ping-Pong-Verfahren. Aus radialer Tiefe und Kamera-Basis wird die Weltposition
rekonstruiert und in das vorherige Bild projiziert. Jeder bilineare History-Tap wird
getrennt anhand von Material, Normalen, Entfernung und Oberflächenebene geprüft.
Eine 3×3-Nachbarschaft begrenzt überholte Farben und Glanzlichter. Der Verlaufseinfluss
nimmt bei fortschreitender statischer Akkumulation ab.

Kamerabewegung setzt nur die rohe progressive Akkumulation zurück. Szenenwechsel,
bewegte Coins, Render-Auflösung, Sampling-Einstellungen, Denoiserwechsel sowie
TAA-Umschaltung verwerfen zusätzlich den temporalen Verlauf. TAA-Umschaltung erhält
die Rohsamples; Belichtungsänderung zeichnet das vorhandene Ergebnis erneut.
Ein separater Sample-Zähler verhindert identische Subpixel-Samples nach jedem
Kamera-Reset. Pause verwendet das beruhigte Bild weiter, ohne den Verlauf erneut
zu mischen. Bei Coin-Bewegung wird der gesamte Verlauf konservativ verworfen;
das verhindert auch alte Schatten/Reflexionen ohne separate Bewegungsvektoren.

## Ausgeführte Prüfungen

Alle Java-Quellen mit ECJ/Java 17 gegen LWJGL 3.4.3 kompiliert.
`RendererSmokeTest` lief unter EGL/Mesa llvmpipe einschließlich `TemporalAATest`:

- Synthetische jitternde Diagonalkante, 64 Frames, Vergleich mit 32×32-Subpixel-
  Flächenabdeckung: 97,0 % weniger mittlerer quadratischer Kantenfehler nach der
  Einlaufphase. Das ist ein kontrollierter Shader-Test, keine pauschale Aussage
  über jedes Spielbild oder GPU-Leistung.
- Kamera um exakt einen Pixel verschoben: Verlauf wird aus dem richtigen
  benachbarten Pixel reprojiziert.
- Geänderte Tiefe, Normale oder Material sowie expliziter Reset verwerfen alte Daten.
- Aktuelle Nachbarschaft begrenzt überholte Glanzlichter.
- TAA an/aus im bestehenden Renderer bewahrt die Rohsamples.
- Resize, Szenenwechsel, Pause, sechs Format-/Workgroup-Varianten, bewegte Coins,
  BVH-/Brute-Force-Vergleich, Denoiser und Hologramm weiterhin bestanden.
- `MenuSettingsTest`: Schalter, Tastatur/HiDPI, persistierte Auswahl, Migration alter
  Einstellungen ohne TAA-Feld und Standardwerte bestanden.

Das Grafikmenü wurde als echte OpenGL-Ausgabe gerendert und visuell geprüft.
Interaktive Kamerafahrten auf Windows-Zielhardware und der Maven-/JDK-25-Build
sind noch nicht vor Ort geprüft. Die zeitliche Glättung kann Details etwas weicher
machen; deshalb ist sie unabhängig vom Denoiser abschaltbar.
