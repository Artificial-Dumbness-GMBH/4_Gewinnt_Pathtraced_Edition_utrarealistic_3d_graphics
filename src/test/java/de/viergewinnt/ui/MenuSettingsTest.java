package de.viergewinnt.ui;

import de.viergewinnt.renderer.*;
import de.viergewinnt.scene.Camera;
import java.nio.file.*;

public final class MenuSettingsTest {
    private static void check(boolean v,String message) { if(!v) throw new AssertionError(message); }
    private static PauseMenu.Action click(PauseMenu menu,String id) {
        var control=menu.controls().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();
        return menu.click(control.x()+control.width()/2.0,control.y()+control.height()/2.0);
    }
    public static void main(String[] args) throws Exception {
        RenderSettings defaults=RenderSettings.defaults();PauseMenu menu=new PauseMenu(defaults);menu.open("Rot ist am Zug");
        check(click(menu,"graphics")==PauseMenu.Action.NONE&&menu.isGraphics(),"graphics navigation");
        for(RenderSettings.Denoiser mode:new RenderSettings.Denoiser[]{RenderSettings.Denoiser.OFF,RenderSettings.Denoiser.OWN,RenderSettings.Denoiser.ATROUS}) {
            check(click(menu,"denoiser")==PauseMenu.Action.SETTINGS&&menu.settings().denoiser()==mode,"denoiser cycle");
        }
        check(click(menu,"taa")==PauseMenu.Action.SETTINGS&&!menu.settings().taa(),"TAA off");
        check(click(menu,"taa")==PauseMenu.Action.SETTINGS&&menu.settings().taa(),"TAA on");
        check(defaults.sameSampling(defaults.withTaa(false)),"TAA discarded raw samples");
        for(int i=0;i<20;i++) click(menu,"bounces+");check(menu.settings().bounces()==12,"bounce upper bound");
        check(PauseMenu.scale(1,1)>0,"minimized panel scale");
        var minus=menu.controls().stream().filter(c->c.id().equals("bounces-")).findFirst().orElseThrow();
        menu.hover(minus.x()+2,minus.y()+2);
        for(int i=0;i<11;i++) menu.activateFocused();
        check(menu.settings().bounces()==1&&menu.activateFocused()==PauseMenu.Action.NONE,"stale disabled keyboard focus");
        for(int i=0;i<20;i++) click(menu,"bounces-");check(menu.settings().bounces()==1,"bounce lower bound");
        click(menu,"samples+");check(menu.settings().samplesPerFrame()==8,"sample step");
        click(menu,"resolution+");check(menu.settings().maxWidth()==1280,"resolution step");
        click(menu,"strength+");click(menu,"exposure+");
        check(menu.settings().denoiseStrength()==1.25f&&menu.settings().exposure()==1.25f,"display controls");
        check(defaults.sameSampling(defaults.withExposure(2).withDenoiser(RenderSettings.Denoiser.OWN)),"display changes discard samples");
        check(!defaults.sameSampling(defaults.withSamples(8)),"sampling changes not detected");
        check(click(menu,"defaults")==PauseMenu.Action.SETTINGS&&menu.settings().equals(defaults),"defaults");
        click(menu,"resolution-");check(menu.settings().maxHeight()==360,"360p step");
        click(menu,"resolution-");check(menu.settings().maxWidth()==213&&menu.settings().maxHeight()==120,"120p step");
        click(menu,"resolution-");check(menu.settings().maxWidth()==124&&menu.settings().maxHeight()==70,"70p step");
        check(click(menu,"resolution-")==PauseMenu.Action.NONE&&menu.settings().maxHeight()==70,"minimum resolution");
        click(menu,"resolution+");check(menu.settings().maxHeight()==120,"leave minimum resolution");
        click(menu,"defaults");
        // The exact same panel transform is used at 1x, HiDPI, portrait and ultrawide.
        for(int[] dims:new int[][]{{1280,720,1280,720},{1280,720,2560,1440},{600,900,1200,1800},{2560,1080,2560,1080}}) {
            for(var c:menu.controls()) {
                double scale=PauseMenu.scale(dims[2],dims[3]),x=c.x()+c.width()*.5,y=c.y()+c.height()*.5;
                double cursorX=(x*scale+(dims[2]-PauseMenu.WIDTH*scale)*.5)*dims[0]/dims[2];
                double cursorY=(y*scale+(dims[3]-PauseMenu.HEIGHT*scale)*.5)*dims[1]/dims[3];
                double[] point=PauseMenu.panelPoint(cursorX,cursorY,dims[0],dims[1],dims[2],dims[3]);
                check(Math.abs(point[0]-x)<.01&&Math.abs(point[1]-y)<.01,"HiDPI hit coordinates");
            }
        }
        check(menu.back()&&!menu.isGraphics()&&!menu.back(),"escape navigation");
        check(click(menu,"resume")==PauseMenu.Action.RESUME&&click(menu,"restart")==PauseMenu.Action.RESTART,"game actions");
        check(click(menu,"quit")==PauseMenu.Action.QUIT,"quit action");
        menu.open("Rot");check(menu.activateFocused()==PauseMenu.Action.RESUME,"keyboard focus");
        menu.focusNext(1);check(menu.activateFocused()==PauseMenu.Action.RESTART,"keyboard traversal");
        check(menu.image(1).getWidth()==PauseMenu.WIDTH,"menu rasterization");
        check(Camera.isWalkable(10,10)&&!Camera.isWalkable(0,0)&&!Camera.isWalkable(22,0)&&!Camera.isWalkable(0,26),"room/table collisions");
        Files.createDirectories(Path.of("target"));Path directory=Files.createTempDirectory(Path.of("target"),"settings-test-");Path file=directory.resolve("render.properties");
        try {
            RenderSettings custom=defaults.withDenoiser(RenderSettings.Denoiser.OWN).withBounces(6).withSamples(8).withExposure(1.5f).withTaa(false);
            SettingsStore.save(file,custom);check(SettingsStore.load(file).equals(custom),"settings round trip");
            for(int[] resolution:new int[][]{{124,70},{213,120}}) {
                RenderSettings low=custom.withResolution(resolution[0],resolution[1]);SettingsStore.save(file,low);
                check(SettingsStore.load(file).equals(low),"low resolution persistence");
            }
            SettingsStore.save(file,custom);
            String saved=Files.readString(file);Files.writeString(file,saved.replace("taa=false\n",""));
            check(SettingsStore.load(file).equals(custom.withTaa(true)),"legacy settings migration");
            Files.writeString(file,"bounces=garbage\n");check(SettingsStore.load(file).equals(defaults),"corrupt settings recovery");
            check(SettingsStore.load(directory.resolve("absent")).equals(defaults),"first run");
        } finally { Files.deleteIfExists(file);Files.deleteIfExists(directory); }
        System.out.println("Menu/settings checks passed: all controls, bounds, keyboard, HiDPI transforms, persistence, fallback and room collision.");
    }
}
