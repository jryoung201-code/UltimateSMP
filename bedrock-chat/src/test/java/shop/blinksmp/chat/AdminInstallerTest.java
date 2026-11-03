package shop.blinksmp.chat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class AdminInstallerTest {
    private static int checks;
    private static void check(boolean c,String message) { checks++; if(!c) throw new AssertionError(message); }
    private static byte[] jar(String name,String version) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(bytes)) {
            z.putNextEntry(new ZipEntry("plugin.yml"));
            z.write(("name: "+name+"\nmain: shop.blinksmp.gui.UltimateGUI\nversion: "+version+"\n").getBytes(StandardCharsets.UTF_8)); z.closeEntry();
            z.putNextEntry(new ZipEntry("shop/blinksmp/gui/UltimateGUI.class")); z.write(new byte[]{1,2,3}); z.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static AdminInstaller.Result install(Path root,byte[] bytes) throws IOException {
        return AdminInstaller.install(root.resolve("plugins"),root.resolve("updates"),bytes,AdminInstaller.sha256(bytes),"1.0.1");
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("gui-installer-test-"); byte[] payload=jar("UltimateGUI","1.0.1");
        try {
            Path fresh=root.resolve("fresh");
            check(install(fresh,payload).restartRequired(),"Fresh installation needs restart");
            Path installed=fresh.resolve("plugins/UltimateGUI-1.0.1.jar");
            check(Arrays.equals(Files.readAllBytes(installed),payload),"Payload copy must be exact");
            check(!install(fresh,payload).restartRequired(),"Already-current installation must be idempotent");
            Path old=root.resolve("old"); Files.createDirectories(old.resolve("plugins"));
            byte[] previous=jar("UltimateGUI","1.0.0"); Path running=old.resolve("plugins/custom-admin-name.jar"); Files.write(running,previous);
            check(install(old,payload).restartRequired(),"Older plugin update requires restart");
            check(Arrays.equals(Files.readAllBytes(running),previous),"Running JAR must remain untouched");
            check(Arrays.equals(Files.readAllBytes(old.resolve("updates/custom-admin-name.jar")),payload),"Stage using existing filename");
            check(!Files.exists(old.resolve("plugins/UltimateGUI-1.0.1.jar")),"Do not create duplicate plugin JARs");
            check(install(old,payload).restartRequired(),"Already-staged update still needs restart");
            Path newer=root.resolve("newer"); Files.createDirectories(newer.resolve("plugins"));
            byte[] newerJar=jar("UltimateGUI","1.0.2"); Path newFile=newer.resolve("plugins/UltimateGUI.jar"); Files.write(newFile,newerJar);
            check(!install(newer,payload).restartRequired(),"Do not downgrade newer version");
            check(Arrays.equals(Files.readAllBytes(newFile),newerJar),"Newer file unchanged");
            boolean failed=false;
            try { AdminInstaller.install(root.resolve("bad/plugins"),root.resolve("bad/update"),payload,"wrong","1.0.1"); }
            catch(IOException ex) { failed=true; }
            check(failed && !Files.exists(root.resolve("bad/plugins")),"Checksum failure must happen before writes");
            Path conflict=root.resolve("conflict"); Files.createDirectories(conflict.resolve("plugins"));
            Files.write(conflict.resolve("plugins/UltimateGUI-1.0.1.jar"),jar("DifferentPlugin","1.0.0")); failed=false;
            try { install(conflict,payload); } catch(IOException ex) { failed=true; }
            check(failed,"Occupied target filename must not be overwritten");
            Path duplicates=root.resolve("duplicates"); Files.createDirectories(duplicates.resolve("plugins"));
            Files.write(duplicates.resolve("plugins/a.jar"),previous); Files.write(duplicates.resolve("plugins/b.jar"),previous); failed=false;
            try { install(duplicates,payload); } catch(IOException ex) { failed=true; }
            check(failed,"Conflicting duplicate GUI plugins must be rejected");
            System.out.println("PASS: "+checks+" auto-install, staging, checksum, downgrade and conflict checks");
        } finally { try(var paths=Files.walk(root)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
    }
}
