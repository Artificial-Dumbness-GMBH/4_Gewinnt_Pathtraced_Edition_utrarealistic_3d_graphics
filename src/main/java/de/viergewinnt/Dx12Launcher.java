package de.viergewinnt;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.JOptionPane;

/** Entry point for the self-contained Windows application image. */
public final class Dx12Launcher {
    private Dx12Launcher() {}
    public static void main(String[] args) {
        boolean verify=args.length>0&&args[0].equals("--verify-package");
        Path report=verify&&args.length==2?Path.of(args[1]).toAbsolutePath():null;
        Path log=Path.of(System.getProperty("user.home"),".viergewinnt","dx12.log");
        try {
            Path jar=Path.of(Dx12Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path nativeDirectory=jar.getParent().resolve("native");
            Path bridge=nativeDirectory.resolve("viergewinnt_dx12.dll");
            if(!Files.isRegularFile(bridge)) throw new IllegalStateException("DX12-DLL fehlt. Bitte das gesamte ZIP entpacken, nicht nur die EXE kopieren.");
            System.setProperty("pt.backend","dx12");
            System.setProperty("pt.requireDx12","true");
            System.setProperty("pt.dx12.library",bridge.toString());
            if(verify) {
                if(report==null) throw new IllegalArgumentException("--verify-package requires a report path");
                // Validate the package/loader without creating a GPU device.
                for(String dll:new String[]{"libxell.dll","libxess.dll","libxess_fg.dll","amd_fidelityfx_upscaler_dx12.dll","viergewinnt_dx12.dll"}) {
                    System.load(nativeDirectory.resolve(dll).toString());
                }
                Class.forName("org.lwjgl.system.Library");
                Class.forName("org.lwjgl.glfw.GLFW");
                Class.forName("org.lwjgl.opengl.GL",false,Dx12Launcher.class.getClassLoader());
                Class.forName("org.lwjgl.egl.EGL",false,Dx12Launcher.class.getClassLoader());
                Class.forName("org.joml.Matrix4f");
                try(var shader=Dx12Launcher.class.getResourceAsStream("/shaders/pathtrace.comp")) {
                    if(shader==null||shader.read()<0) throw new IllegalStateException("Shader resources missing");
                }
                Files.writeString(report,"PASS: bundled JVM, classpath, LWJGL/GLFW native libraries and DX12/FSR/XeSS DLL loading. No GPU rendering tested.\n");
                return;
            }
            Files.createDirectories(log.getParent());
            PrintStream output=new PrintStream(Files.newOutputStream(log,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING),true,StandardCharsets.UTF_8);
            System.setOut(output);System.setErr(output);
            System.out.println("4 Gewinnt DX12 | Java "+System.getProperty("java.version"));
            Main.main(args);
        } catch(Exception|LinkageError failure) {
            failure.printStackTrace();
            if(verify) {
                if(report!=null) try { Files.writeString(report,"FAIL: "+failure+"\n"); } catch(Exception ignored) { }
            } else {
                JOptionPane.showMessageDialog(null,"DX12 konnte nicht gestartet werden.\n"+failure.getMessage()
                    +"\n\nProtokoll: "+log,"4 Gewinnt DX12",JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
    }
}
