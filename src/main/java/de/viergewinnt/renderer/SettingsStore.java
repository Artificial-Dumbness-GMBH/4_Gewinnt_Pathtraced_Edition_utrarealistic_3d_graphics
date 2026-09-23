package de.viergewinnt.renderer;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Stored outside the game checkout; callers report write failures without losing session settings. */
public final class SettingsStore {
    private SettingsStore() {}
    public static Path defaultPath() {
        return Path.of(System.getProperty("pt.settingsFile",Path.of(System.getProperty("user.home"),".viergewinnt","render.properties").toString()));
    }
    public static RenderSettings load(Path path) {
        if(!Files.isRegularFile(path)) return RenderSettings.defaults();
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(path)) {
            p.load(in);
            return new RenderSettings(RenderSettings.Denoiser.valueOf(p.getProperty("denoiser")),
                Integer.parseInt(p.getProperty("bounces")),Integer.parseInt(p.getProperty("samples")),
                Integer.parseInt(p.getProperty("width")),Integer.parseInt(p.getProperty("height")),
                Float.parseFloat(p.getProperty("strength")),Float.parseFloat(p.getProperty("exposure")),
                Boolean.parseBoolean(p.getProperty("taa","true")),new GraphicsOptions(
                    GraphicsOptions.Raytracing.valueOf(p.getProperty("raytracing","AUTO")),
                    GraphicsOptions.Upscaler.valueOf(p.getProperty("upscaler","OFF")),
                    GraphicsOptions.Quality.valueOf(p.getProperty("quality","QUALITY")),
                    Integer.parseInt(p.getProperty("denoisePasses","4")),Float.parseFloat(p.getProperty("sharpness","0.2")),
                    Boolean.parseBoolean(p.getProperty("vsync","true")),Integer.parseInt(p.getProperty("frameGeneration","1")),
                    Integer.parseInt(p.getProperty("frameLimit","0"))));
        } catch(IOException|IllegalArgumentException|NullPointerException e) {
            System.err.println("Grafikeinstellungen konnten nicht geladen werden; verwende Standardwerte: "+e.getMessage());
            return RenderSettings.defaults();
        }
    }
    public static void save(Path path,RenderSettings s) throws IOException {
        path=path.toAbsolutePath();Files.createDirectories(path.getParent());
        Properties p=new Properties();p.setProperty("denoiser",s.denoiser().name());p.setProperty("bounces",Integer.toString(s.bounces()));
        p.setProperty("samples",Integer.toString(s.samplesPerFrame()));p.setProperty("width",Integer.toString(s.maxWidth()));
        p.setProperty("height",Integer.toString(s.maxHeight()));p.setProperty("strength",Float.toString(s.denoiseStrength()));
        p.setProperty("exposure",Float.toString(s.exposure()));p.setProperty("taa",Boolean.toString(s.taa()));
        GraphicsOptions g=s.graphics();
        p.setProperty("raytracing",g.raytracing().name());p.setProperty("upscaler",g.upscaler().name());p.setProperty("quality",g.quality().name());
        p.setProperty("denoisePasses",Integer.toString(g.denoisePasses()));p.setProperty("sharpness",Float.toString(g.sharpness()));
        p.setProperty("vsync",Boolean.toString(g.vsync()));p.setProperty("frameGeneration",Integer.toString(g.frameGeneration()));
        p.setProperty("frameLimit",Integer.toString(g.frameLimit()));
        Path temp=Files.createTempFile(path.getParent(),"render-",".tmp");
        try {
            try(OutputStream out=Files.newOutputStream(temp)) { p.store(out,"4 Gewinnt graphics settings"); }
            try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
