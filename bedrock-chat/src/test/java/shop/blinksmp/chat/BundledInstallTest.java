package shop.blinksmp.chat;

import java.nio.file.*;
import java.util.*;

/** Exercise installation using the actual bundled binaries, not only fixture JARs. */
public final class BundledInstallTest {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("bundled-plugins-test-");
        try {
            check(root,"UltimateGUI","admin",new AdminInstaller.Spec("UltimateGUI","shop.blinksmp.gui.UltimateGUI","1.0.2"));
            check(root,"PacketEvents","packetevents",new AdminInstaller.Spec("packetevents","io.github.retrooper.packetevents.PacketEventsPlugin","2.14.0"));
            System.out.println("PASS: actual bundled PacketEvents and UltimateGUI install and verify in Java");
        } finally { try(var paths=Files.walk(root)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
    }
    private static void check(Path root,String jar,String manifest,AdminInstaller.Spec spec) throws Exception {
        try(var bytes=BundledInstallTest.class.getResourceAsStream("/bundled/"+jar+".jar");
            var meta=BundledInstallTest.class.getResourceAsStream("/bundled/"+manifest+".properties")) {
            Properties properties=new Properties(); properties.load(Objects.requireNonNull(meta));
            byte[] payload=Objects.requireNonNull(bytes).readAllBytes();
            var result=AdminInstaller.installPlugin(root.resolve("plugins"),root.resolve("update"),payload,properties.getProperty("sha256"),spec);
            if(!result.restartRequired()) throw new AssertionError("Fresh bundle must be installed");
            Path actual=root.resolve("plugins").resolve(spec.name()+"-"+spec.version()+".jar");
            if(!Arrays.equals(Files.readAllBytes(actual),payload)) throw new AssertionError("Installed bytes differ from bundle");
        }
    }
}

