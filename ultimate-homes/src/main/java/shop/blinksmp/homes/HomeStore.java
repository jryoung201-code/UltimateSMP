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
    public record Home(String name,UUID world,String worldName,String dimension,double x,double y,double z,float yaw,float pitch) {
        public Home {
            name=HomeLimits.name(name);Objects.requireNonNull(world);Objects.requireNonNull(worldName);
            if(!Set.of("Overworld","Nether","End").contains(dimension))throw new IllegalArgumentException("Invalid home dimension");
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)) throw new IllegalArgumentException("Invalid home coordinates");
        }
        public Home(String name,UUID world,String worldName,double x,double y,double z,float yaw,float pitch) {
            this(name,world,worldName,legacyDimension(worldName),x,y,z,yaw,pitch);
        }
        private static String legacyDimension(String worldName) {
            if(worldName.endsWith("_nether"))return "Nether";
            if(worldName.endsWith("_the_end"))return "End";
            return "Overworld";
        }
    }
    private final Path directory;
    private final Map<UUID,SortedMap<String,Home>> cache=new HashMap<>();
    private final Map<UUID,Integer> overrides=new HashMap<>();
    public HomeStore(Path directory) throws IOException { this.directory=directory;Files.createDirectories(directory); }
    public SortedMap<String,Home> homes(UUID id) throws IOException {
        SortedMap<String,Home> map=cache.get(id);if(map!=null) return Collections.unmodifiableSortedMap(map);
        Integer override=null;
        TreeMap<String,Home> loaded=new TreeMap<>();Path path=directory.resolve(id+".yml");
        if(Files.exists(path)) {
            if(Files.size(path)>2_000_000) throw new IOException("Home data file is too large; file preserved.");
            YamlConfiguration yaml=new YamlConfiguration();
            try {
                yaml.loadFromString(Files.readString(path,StandardCharsets.UTF_8));
                if(yaml.getInt("version")!=1 || !id.toString().equals(yaml.getString("owner"))) throw new IllegalArgumentException("Wrong home file version/owner");
                override=readOverride(yaml);
                ConfigurationSection section=yaml.getConfigurationSection("homes");
                if(section==null || section.getKeys(false).size()>10000) throw new IllegalArgumentException("Invalid homes section");
                for(String key:section.getKeys(false)) {
                    ConfigurationSection node=Objects.requireNonNull(section.getConfigurationSection(key));
                    if(!HomeLimits.name(key).equals(key)) throw new IllegalArgumentException("Invalid stored name");
                    for(String number:List.of("x","y","z","yaw","pitch")) if(!node.isDouble(number) && !node.isInt(number) && !node.isLong(number)) throw new IllegalArgumentException("Invalid coordinate "+number);
                    Home home=new Home(key,UUID.fromString(node.getString("world","")),node.getString("world-name","Unknown"),node.getString("dimension",Home.legacyDimension(node.getString("world-name","Unknown"))),node.getDouble("x"),node.getDouble("y"),node.getDouble("z"),(float)node.getDouble("yaw"),(float)node.getDouble("pitch"));
                    loaded.put(key,home);
                }
            } catch(InvalidConfigurationException|RuntimeException ex) { throw new IOException("Cannot read home data; original file preserved",ex); }
        }
        if(override!=null)overrides.put(id,override);else overrides.remove(id);
        cache.put(id,loaded);return Collections.unmodifiableSortedMap(loaded);
    }
    private static Integer readOverride(YamlConfiguration yaml) {
        if(!yaml.contains("max-homes-override"))return null;
        if(!yaml.isInt("max-homes-override") || yaml.getInt("max-homes-override")<0)
            throw new IllegalArgumentException("Invalid home limit override");
        return yaml.getInt("max-homes-override");
    }
    public Integer override(UUID id) throws IOException { homes(id);return overrides.get(id); }
    public void setOverride(UUID id,Integer limit) throws IOException {
        if(limit!=null && limit<0)throw new IllegalArgumentException("Home limit cannot be negative");
        save(id,homes(id),limit);
    }
    public Home get(UUID id,String name) throws IOException { return homes(id).get(HomeLimits.name(name)); }
    public void put(UUID id,Home home,int limit) throws IOException {
        TreeMap<String,Home> copy=new TreeMap<>(homes(id));
        if(!copy.containsKey(home.name()) && copy.size()>=limit) throw new IllegalArgumentException("Home limit reached ("+copy.size()+"/"+HomeLimits.label(limit)+"). Delete a home or ask staff for a higher maxhomes permission.");
        copy.put(home.name(),home);save(id,copy);
    }
    public boolean delete(UUID id,String name) throws IOException {
        TreeMap<String,Home> copy=new TreeMap<>(homes(id));if(copy.remove(HomeLimits.name(name))==null) return false;save(id,copy);return true;
    }
    private void save(UUID id,SortedMap<String,Home> homes) throws IOException { save(id,homes,overrides.get(id)); }
    private void save(UUID id,SortedMap<String,Home> homes,Integer override) throws IOException {
        if(homes.size()>10000)throw new IOException("Home storage exceeds the safe file entry limit");
        YamlConfiguration yaml=new YamlConfiguration();yaml.set("version",1);yaml.set("owner",id.toString());yaml.createSection("homes");
        if(override!=null)yaml.set("max-homes-override",override);
        for(Home h:homes.values()) {
            String key="homes."+h.name()+".";yaml.set(key+"world",h.world().toString());yaml.set(key+"world-name",h.worldName());yaml.set(key+"dimension",h.dimension());
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
            if(override!=null)overrides.put(id,override);else overrides.remove(id);
        } finally { Files.deleteIfExists(temp); }
    }
    public String exportNetworkModule(UUID id) throws IOException {
        YamlConfiguration y=new YamlConfiguration();
        y.set("version",1);y.set("owner",id.toString());y.createSection("homes");
        SortedMap<String,Home> saved=homes(id);
        if(overrides.containsKey(id))y.set("max-homes-override",overrides.get(id));
        for(Home h:saved.values()) {
            String key="homes."+h.name()+".";
            y.set(key+"world",h.world().toString());y.set(key+"world-name",h.worldName());y.set(key+"dimension",h.dimension());
            y.set(key+"x",h.x());y.set(key+"y",h.y());y.set(key+"z",h.z());y.set(key+"yaw",h.yaw());y.set(key+"pitch",h.pitch());
        }
        return y.saveToString();
    }
    public void importNetworkModule(UUID id,String payload) throws IOException {
        if(payload.length()>2_000_000)throw new IOException("Home profile too large");
        TreeMap<String,Home> copy=new TreeMap<>();Integer override;
        try {
            YamlConfiguration y=new YamlConfiguration();y.loadFromString(payload);
            if(y.getInt("version")!=1 || !id.toString().equals(y.getString("owner")))throw new IllegalArgumentException("Invalid home owner/version");
            override=readOverride(y);
            ConfigurationSection section=y.getConfigurationSection("homes");
            if(section==null || section.getKeys(false).size()>10000)throw new IllegalArgumentException("Invalid homes");
            for(String name:section.getKeys(false)){
                if(!name.equals(HomeLimits.name(name)))throw new IllegalArgumentException("Invalid home name");
                ConfigurationSection n=Objects.requireNonNull(section.getConfigurationSection(name));
                for(String number:List.of("x","y","z","yaw","pitch"))
                    if(!n.isDouble(number)&&!n.isInt(number)&&!n.isLong(number))throw new IllegalArgumentException("Invalid "+number);
                String worldName=n.getString("world-name","Unknown");
                Home h=new Home(name,UUID.fromString(n.getString("world","")),worldName,
                    n.getString("dimension",Home.legacyDimension(worldName)),n.getDouble("x"),n.getDouble("y"),n.getDouble("z"),
                    (float)n.getDouble("yaw"),(float)n.getDouble("pitch"));
                copy.put(name,h);
            }
        }catch(InvalidConfigurationException|RuntimeException ex){throw new IOException("Invalid home profile; local homes preserved",ex);}
        save(id,copy,override);
    }
    public void unload(UUID id) { cache.remove(id);overrides.remove(id); }
}

