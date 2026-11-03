package shop.blinksmp.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PreferenceStore {
    public static final List<String> KEYS=List.of("booster","rank-tags","players","messages","join-messages","coordinates");
    private final Path path;
    private volatile Map<UUID,Preferences> state=Map.of();
    public PreferenceStore(Path path) throws Exception {
        this.path=path;
        if(!Files.exists(path)) return;
        YamlConfiguration y=new YamlConfiguration(); y.load(path.toFile());
        var section=y.getConfigurationSection("players");
        if(section==null) {
            if(y.contains("players")) throw new IllegalArgumentException("players must be a YAML section");
            return;
        }
        Map<UUID,Preferences> loaded=new HashMap<>();
        for(String id:section.getKeys(false)) {
            String base="players."+id+".";
            for(String key:KEYS) if(y.contains(base+key) && !y.isBoolean(base+key)) throw new IllegalArgumentException("Non-boolean preference: "+base+key);
            loaded.put(UUID.fromString(id),new Preferences(
                    y.getBoolean(base+"booster",false),y.getBoolean(base+"rank-tags",true),
                    y.getBoolean(base+"players",true),y.getBoolean(base+"messages",true),
                    y.getBoolean(base+"join-messages",true),y.getBoolean(base+"coordinates",false)));
        }
        state=Map.copyOf(loaded);
    }
    public Preferences get(UUID id) { return state.getOrDefault(id,Preferences.DEFAULT); }
    public synchronized void put(UUID id,Preferences preferences) throws IOException {
        Map<UUID,Preferences> next=new HashMap<>(state); next.put(id,preferences);
        YamlConfiguration y=new YamlConfiguration();
        for(var row:next.entrySet()) for(String key:KEYS) y.set("players."+row.getKey()+"."+key,row.getValue().value(key));
        Files.createDirectories(path.getParent()); Path temp=Files.createTempFile(path.getParent(),"settings-",".tmp");
        try {
            Files.writeString(temp,y.saveToString(),StandardCharsets.UTF_8);
            try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
            state=Map.copyOf(next);
        } finally { Files.deleteIfExists(temp); }
    }
}
