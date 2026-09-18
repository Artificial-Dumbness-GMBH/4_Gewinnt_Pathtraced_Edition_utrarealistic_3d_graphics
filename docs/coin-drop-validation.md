# Fallanimation und gelochtes Brett

- Schwerkraftbasierter Fall mit kurzem Aufsetzer; der Spielzug wird erst danach
  übernommen. Weitere Einwürfe sind währenddessen gesperrt. Pause hält die
  Animationszeit an, Neustart verwirft den ausstehenden Zug.
- Zwei massive, gelochte Seitenplatten mit 42 Sichtöffnungen je Seite. Dazwischen
  verlaufen sieben freie Schächte. Die geteilte obere Leiste lässt die Einwürfe offen.
- Steinradius 0,48, vertikaler Abstand 0,96: benachbarte Steine berühren sich.
  Bodenoberfläche bei y=0,05, unterste Steinmitte bei y=0,53.
- Die Szene enthält einen beweglichen Stein. Dessen Höhenversatz wird per Uniform
  auf sämtliche Primär-, Schatten- und indirekten Strahlen angewendet. Der BVH
  erweitert gemischte Knoten einmal für den gesamten Fallweg. Teilbäume nur für
  den bewegten Stein behalten enge lokale Grenzen und prüfen den entsprechend
  verschobenen Strahl. Während des Falls sind weder
  erneute BVH-Konstruktion noch Mesh-Uploads nötig. Positionswechsel verwerfen
  alte Samples und erneuern die Tiefen-/Normalen-/Albedo-Guides.

## Prüfungen

Alle Java-Quellen wurden mit ECJ/Java 17 gegen LWJGL 3.4.3 kompiliert.

`SceneRegressionTest` führt auch `DropRegressionTest` aus. Geprüft wurden
Beschleunigung, Eingabesperre, Landung genau einmal, verzögerter Zugwechsel/Sieg,
volle Spalten, Neustart während des Falls sowie Kontakt von Boden und Stein-Stapel.
Geometrische Strahltests prüfen das komplette Querschnittsrechteck des Steins in
allen sieben Schächten, 42 freie Sichtöffnungen und ihre massiven Ränder.
Erweiterte BVH-Blatt- und Elternbegrenzungen enthalten beide Endpositionen.

`RendererSmokeTest` führt zusätzlich `DropRenderTest` aus: echte OpenGL-Ausgabe
bei Start, halber Fallhöhe und Landung. Beschleunigte BVH-Abfragen und vollständige
Dreiecksprüfung liefern übereinstimmende HDR-Bilder (Toleranz 1e-4); die Position
ändert sichtbar das Bild. Akkumulation wird bei Bewegung verworfen und bei
Stillstand wieder fortgesetzt. Bestehende GPU-, Denoiser- und Hologrammprüfungen
sowie 6000 CPU-BVH-Strahlvergleiche bestanden.

GPU-Tests liefen auf Mesa llvmpipe/EGL. Interaktive Windows-Eingabe und der
konfigurierte Maven-/JDK-25-Build sind auf Zielhardware noch zu prüfen.

## Vorschau

`coin-drop.jpg`: echtes OpenGL-Rendering bei nativen 960×540 Pixeln, 64 spp.
`coin-drop.mp4`: echte Renderbilder bei nativen 512×288 Pixeln, mindestens 8 spp,
mit 12 festen Animationsschritten pro Sekunde abgespielt. Offline erstellt;
die Abspielrate ist keine Messung der Echtzeit-GPU-Leistung. Kein Upscaler.
