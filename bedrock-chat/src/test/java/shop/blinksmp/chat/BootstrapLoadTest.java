package shop.blinksmp.chat;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;

/** Exercise the real bootstrap class in a loader where PacketEvents is unavailable. */
public final class BootstrapLoadTest {
    public static void main(String[] args) throws Exception {
        List<URL> urls=new ArrayList<>();
        for(String path:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if(!Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT).contains("packetevents")) urls.add(Path.of(path).toUri().toURL());
        }
        AtomicInteger attempts=new AtomicInteger();
        try(URLClassLoader isolated=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.startsWith("com.github.retrooper.packetevents")||name.startsWith("io.github.retrooper.packetevents")) {
                    attempts.incrementAndGet(); throw new ClassNotFoundException("PacketEvents intentionally absent: "+name);
                }
                return super.loadClass(name,resolve);
            }
        }) {
            Class<?> bootstrap=Class.forName("shop.blinksmp.chat.ChatBootstrap",true,isolated);
            bootstrap.getDeclaredMethods(); bootstrap.getDeclaredConstructors(); bootstrap.getDeclaredFields();
            Class.forName("shop.blinksmp.chat.AdminInstaller",true,isolated).getDeclaredMethods();
            if(attempts.get()!=0) throw new AssertionError("Bootstrap attempted to resolve PacketEvents before installation");
            try(var in=isolated.getResourceAsStream("plugin.yml")) {
                String yaml=new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                if(yaml.lines().anyMatch(line -> line.startsWith("depend:") && line.toLowerCase(Locale.ROOT).contains("packetevents"))) throw new AssertionError("PacketEvents must not be a hard dependency");
                if(!yaml.contains("main: shop.blinksmp.chat.ChatBootstrap")) throw new AssertionError("Wrong bootstrap entry point");
            }
        }
        System.out.println("PASS: real bootstrap loads and resolves without PacketEvents classes");
    }
}
