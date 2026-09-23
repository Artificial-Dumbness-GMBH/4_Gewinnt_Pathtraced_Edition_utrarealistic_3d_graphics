# Windows-Download

**4Gewinnt-DX12-Windows-x64.zip** herunterladen, vollständig entpacken und **4Gewinnt-DX12.exe** starten.
Die Ordner `app` und `runtime` müssen neben der EXE bleiben. Java, Spielcode, DX12-Bridge, FSR-/XeSS-DLLs und Lizenztexte sind enthalten.
Java und Maven müssen auf dem Spielrechner nicht installiert sein.

## Enthalten
- Hardware-Raytracing (DXR 1.1) und GPU-Software-BVH.
- FSR-4.1-API-Anbindung mit Anzeige des tatsächlichen Providers.
- XeSS-SR und XeSS-3-Frame-Generation nach GPU-/SDK-Unterstützung.
- Bilateral-/À-Trous-Denoising, TAA und erweitertes Grafikmenü.
- VSync, FPS-Limit, Filterqualität, Nachschärfen und gespeicherte Einstellungen.

## Start und Prüfstatus
Windows 10/11 x64 und ein aktueller DX12-Grafiktreiber werden benötigt.
WASD/Maus: Kamera; 1–7: Stein einwerfen; ESC: Menü.

Windows-Builds mit/ohne SDKs, Java-Tests sowie der Start-/DLL-Ladetest des verschobenen EXE-Pakets sind bestanden.
**Echte DX12-GPU-Ausführung und Bildqualität sind noch nicht hardwarevalidiert.**
Dies ist eine nicht digital signierte Vorabversion; die DX12-HUD ist derzeit bildschirmgebunden.

Bei Startfehlern: `%USERPROFILE%/.viergewinnt/dx12.log`.

Unverändertes, SHA-256-geprüftes ZIP aus [Windows-Build 35906905308](https://github.com/Artificial-Dumbness-GMBH/4_Gewinnt_Pathtraced_Edition_utrarealistic_3d_graphics/actions/runs/35906905308), Quellstand `10dd01f92febc5b15b87cba847920d29fe85baa1`.
