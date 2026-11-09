package shop.blinksmp.homes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Main-thread store; changes reach disk before the in-memory map is published. */
public final class HomeStore {
    public record Home(String name,UUID world,String worldName,double x,double y,double z,float yaw,float pitch) {
        public Home {
            name=HomeLimits.name(name);Objects.requireNonNull(world);Objects.requireNonNull(worldName);
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)) throw new IllegalArgumentException("Invalid home coordinates");
        }
    }
    private final Path directory;
    private final Map<UUID,SortedMap<String,Home>> cache=new HashMap<>();
    public HomeStore(Path directory) throws IOException { this.directory=directory;Files.createDirectories(directory); }
    public SortedMap<String,Home> homes(UUID id) throws IOException {
        SortedMap<String,Home> map=cache.get(id);if(map!=null) return Collections.unmodifiableSortedMap(map);
        TreeMap<String,Home> loaded=new TreeMap<>();Path path=directory.resolve(id+".yml");
        if(Files.exists(path)) {
            if(Files.size(path)>2_000_000) throw new IOException("Home data file is too large; file preserved.");
            YamlConfiguration yaml=new YamlConfiguration();
            try {
                yaml.loadFromString(Files.readString(path,StandardCharsets.UTF_8));
                if(yaml.getInt("version")!=1 || !id.toString().equals(yaml.getString("owner"))) throw new IllegalArgumentException("Wrong home file version/owner");
                ConfigurationSection section=yaml.getConfigurationSection("homes");
                if(section==null || section.getKeys(false).size()>10000) throw new IllegalArgumentException("Invalid homes section");
                for(String key:section.getKeys(false)) {
                    ConfigurationSection node=Objects.requireNonNull(section.getConfigurationSection(key));
                    if(!HomeLimits.name(key).equals(key)) throw new IllegalArgumentException("Invalid stored name");
                    for(String number:List.of("x","y","z","yaw","pitch")) if(!node.isDouble(number) && !node.isInt(number) && !node.isLong(number)) throw new IllegalArgumentException("Invalid coordinate "+number);
                    Home home=new Home(key,UUID.fromString(node.getString("world","")),node.getString("world-name","Unknown"),node.getDouble("x"),node.getDouble("y"),node.getDouble("z"),(float)node.getDouble("yaw"),(float)node.getDouble("pitch"));
                    loaded.put(key,home);
                }
            } catch(InvalidConfigurationException|RuntimeException ex) { throw new IOException("Cannot read home data; original file preserved",ex); }
        }
        cache.put(id,loaded);return Collections.unmodifiableSortedMap(loaded);
    }
    public Home get(UUID id,String name) throws IOException { return homes(id).get(HomeLimits.name(name)); }
    public void put(UUID id,Home home,int limit) throws IOException {
        TreeMap<String,Home> copy=new TreeMap<>(homes(id));
        if(!copy.containsKey(home.name()) && copy.size()>=limit) throw new IllegalArgumentException("Home limit reached ("+copy.size()+"/"+limit+"). Delete a home or ask staff for a higher maxhomes permission.");
        copy.put(home.name(),home);save(id,copy);
    }
    public boolean delete(UUID id,String name) throws IOException {
        TreeMap<String,Home> copy=new TreeMap<>(homes(id));if(copy.remove(HomeLimits.name(name))==null) return false;save(id,copy);return true;
    }
    private void save(UUID id,SortedMap<String,Home> homes) throws IOException {
        YamlConfiguration yaml=new YamlConfiguration();yaml.set("version",1);yaml.set("owner",id.toString());yaml.createSection("homes");
        for(Home h:homes.values()) {
            String key="homes."+h.name()+".";yaml.set(key+"world",h.world().toString());yaml.set(key+"world-name",h.worldName());
            yaml.set(key+"x",h.x());yaml.set(key+"y",h.y());yaml.set(key+"z",h.z());yaml.set(key+"yaw",h.yaw());yaml.set(key+"pitch",h.pitch());
        }
        byte[] bytes=yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>2_000_000) throw new IOException("Home data exceeds file size limit");
        Path destination=directory.resolve(id+".yml"),temp=Files.createTempFile(directory,id+"-",".tmp");
        try {
            try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                ByteBuffer data=ByteBuffer.wrap(bytes);while(data.hasRemaining()) channel.write(data);channel.force(true);
            }
            try { Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { throw new IOException("Atomic home-data replacement is unsupported; previous file preserved",ex); }
            cache.put(id,new TreeMap<>(homes));
        } finally { Files.deleteIfExists(temp); }
    }
    public void unload(UUID id) { cache.remove(id); }
}
