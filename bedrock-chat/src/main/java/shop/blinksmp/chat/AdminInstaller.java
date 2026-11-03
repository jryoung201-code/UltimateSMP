package shop.blinksmp.chat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.zip.*;

/** Copies the disclosed bundled admin plugin; never hot-loads or overwrites an active JAR. */
public final class AdminInstaller {
    private static final String NAME="UltimateGUI", MAIN="shop.blinksmp.gui.UltimateGUI";
    public record Result(String message,boolean restartRequired) {}
    public record Spec(String name,String main,String version) {}
    private record Info(String name,String main,String version) {}
    private AdminInstaller() {}
    private static String field(String yaml,String key) throws IOException {
        Matcher m=Pattern.compile("(?m)^"+Pattern.quote(key)+":\\s*['\"]?([^'\"\\r\\n#]+?)['\"]?\\s*(?:#.*)?$").matcher(yaml);
        if(!m.find()) throw new IOException("Missing descriptor field "+key);
        return m.group(1).strip();
    }
    private static Info descriptor(byte[] yaml) throws IOException {
        String s=new String(yaml,StandardCharsets.UTF_8);
        return new Info(field(s,"name"),field(s,"main"),field(s,"version"));
    }
    private static Info inspect(Path jar) throws IOException {
        try(JarFile zip=new JarFile(jar.toFile())) {
            JarEntry e=zip.getJarEntry("plugin.yml"); if(e==null) return null;
            try(InputStream in=zip.getInputStream(e)) { return descriptor(in.readNBytes(65536)); }
        }
    }
    private static boolean ours(Info info) { return info!=null && NAME.equals(info.name()) && MAIN.equals(info.main()); }
    private static int compare(String a,String b) throws IOException {
        if(!a.matches("[0-9]+(?:\\.[0-9]+){1,3}") || !b.matches("[0-9]+(?:\\.[0-9]+){1,3}"))
            throw new IOException("Cannot automatically compare GUI version "+a+" with "+b);
        String[] left=a.split("\\."),right=b.split("\\.");
        for(int i=0;i<Math.max(left.length,right.length);i++) {
            int x,y; try { x=i<left.length?Integer.parseInt(left[i]):0; y=i<right.length?Integer.parseInt(right[i]):0; }
            catch(NumberFormatException ex) { throw new IOException("Invalid GUI version",ex); }
            int c=Integer.compare(x,y); if(c!=0) return c;
        }
        return 0;
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static Result install(Path plugins,Path updates,byte[] payload,String hash,String version) throws IOException {
        return installPlugin(plugins,updates,payload,hash,new Spec(NAME,MAIN,version));
    }
    public static Result installPlugin(Path plugins,Path updates,byte[] payload,String hash,Spec spec) throws IOException {
        String name=spec.name(),main=spec.main(),version=spec.version();
        if(!sha256(payload).equalsIgnoreCase(hash)) throw new IOException("Bundled "+name+" checksum mismatch");
        Info incoming=null; boolean mainClass=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(payload))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(entry.getName().equals("plugin.yml")) incoming=descriptor(zip.readNBytes(65536));
                if(entry.getName().equals(main.replace('.','/')+".class")) mainClass=true;
            }
        }
        if(incoming==null||!name.equals(incoming.name())||!main.equals(incoming.main())||!version.equals(incoming.version())||!mainClass)
            throw new IOException("Invalid bundled "+name+" plugin");
        Files.createDirectories(plugins);
        List<Path> found=new ArrayList<>();
        try(DirectoryStream<Path> jars=Files.newDirectoryStream(plugins,"*.jar")) {
            for(Path jar:jars) {
                Info info;
                try { info=inspect(jar); } catch(IOException ex) { continue; }
                if(info!=null && name.equals(info.name())) {
                    if(!main.equals(info.main())) throw new IOException("Conflicting "+name+" plugin identity; no files changed");
                    found.add(jar);
                }
            }
        }
        if(found.size()>1) throw new IOException("Multiple "+name+" JARs found; remove duplicate versions first");
        if(found.isEmpty()) {
            Path target=plugins.resolve(name+"-"+version+".jar");
            if(Files.exists(target)) throw new IOException("GUI target filename is already occupied; no overwrite performed");
            atomicWrite(target,payload,false);
            return new Result("Installed "+target.getFileName()+". Restart once to load it.",true);
        }
        Path original=found.get(0); Info installed=inspect(original);
        if(compare(installed.version(),version)>=0) return new Result(name+" "+installed.version()+" already installed; no downgrade or duplicate created.",false);
        // Paper's update folder uses the EXISTING filename, so restart replaces it instead of adding a duplicate.
        Files.createDirectories(updates); Path staged=updates.resolve(original.getFileName());
        if(Files.exists(staged)) {
            Info previous=inspect(staged);
            if(previous==null||!name.equals(previous.name())||!main.equals(previous.main())) throw new IOException("Conflicting staged plugin update; no overwrite performed");
            if(compare(previous.version(),version)>=0) return new Result("GUI update already staged. Restart once to apply it.",true);
        }
        atomicWrite(staged,payload,true);
        return new Result("Staged "+name+" "+version+" in Paper's update folder. Restart once to apply; running JAR was left untouched.",true);
    }
    private static void atomicWrite(Path destination,byte[] bytes,boolean replace) throws IOException {
        Path temp=Files.createTempFile(destination.getParent(),"gui-install-",".tmp");
        try {
            Files.write(temp,bytes);
            CopyOption[] atomic=replace?new CopyOption[]{StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING}:new CopyOption[]{StandardCopyOption.ATOMIC_MOVE};
            CopyOption[] plain=replace?new CopyOption[]{StandardCopyOption.REPLACE_EXISTING}:new CopyOption[]{};
            try { Files.move(temp,destination,atomic); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temp,destination,plain); }
        } finally { Files.deleteIfExists(temp); }
    }
}
