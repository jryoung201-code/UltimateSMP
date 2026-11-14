package shop.blinksmp.ranks;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Immutable snapshots keep asynchronous chat/login checks free of disk and Bukkit calls. */
public final class RankStore {
    public record Sanction(long until, String reason, String actor, int issuerLevel) {
        public boolean active(long now) { return until==0 || until>now; }
    }
    public record Entry(String name, Set<Rank> ranks, Sanction mute, Sanction ban, List<String> warnings) {
        public Entry {
            ranks=Set.copyOf(ranks.isEmpty()?Set.of(Rank.MEMBER):ranks);
            warnings=List.copyOf(warnings);
        }
        public Entry(String name,Rank rank,Sanction mute,Sanction ban,List<String> warnings) { this(name,Set.of(rank),mute,ban,warnings); }
        /** Highest staff authority, independent of display weights. */
        public Rank rank() { return ranks.stream().max(Comparator.<Rank>comparingInt(r->r.level).thenComparingInt(Enum::ordinal)).orElse(Rank.MEMBER); }
        public Entry named(String n) { return new Entry(n,ranks,mute,ban,warnings); }
        public Entry ranked(Rank r) { return new Entry(name,r,mute,ban,warnings); }
        public Entry added(Rank r) { Set<Rank> next=EnumSet.copyOf(ranks);next.add(r);return new Entry(name,next,mute,ban,warnings); }
        public Entry removed(Rank r) { Set<Rank> next=EnumSet.copyOf(ranks);next.remove(r);return new Entry(name,next,mute,ban,warnings); }
        public Entry muted(Sanction s) { return new Entry(name,ranks,s,ban,warnings); }
        public Entry banned(Sanction s) { return new Entry(name,ranks,mute,s,warnings); }
    }
    private final Path path;
    private volatile Map<UUID,Entry> entries=Map.of();
    public RankStore(Path path) throws Exception {
        this.path=path;
        if(!Files.exists(path)) return;
        YamlConfiguration y=new YamlConfiguration(); y.load(path.toFile());
        var section=y.getConfigurationSection("players");
        if(section==null) {
            if(y.contains("players")) throw new IllegalArgumentException("players must be a YAML section");
            return;
        }
        Map<UUID,Entry> loaded=new HashMap<>();
        for(String key:section.getKeys(false)) {
            UUID id=UUID.fromString(key); String p="players."+key;
            loaded.put(id,new Entry(y.getString(p+".name",key),readRanks(y,p+"."),
                    read(y,p+".mute"),read(y,p+".ban"),y.getStringList(p+".warnings")));
        }
        entries=Map.copyOf(loaded);
    }
    private static Set<Rank> readRanks(YamlConfiguration y,String prefix) {
        if(!y.contains(prefix+"ranks"))return Set.of(Rank.parse(y.getString(prefix+"rank","MEMBER")));
        if(!y.isList(prefix+"ranks"))throw new IllegalArgumentException("ranks must be a list");
        List<?> values=y.getList(prefix+"ranks");
        if(values==null || values.isEmpty() || values.size()>Rank.values().length)throw new IllegalArgumentException("Invalid rank list");
        Set<Rank> result=EnumSet.noneOf(Rank.class);
        for(Object value:values){if(!(value instanceof String text))throw new IllegalArgumentException("Invalid rank");result.add(Rank.parse(text));}
        return result;
    }
    private static List<String> rankNames(Entry e) { return e.ranks().stream().sorted().map(Enum::name).toList(); }
    private static Sanction read(YamlConfiguration y,String p) {
        if(!y.isConfigurationSection(p)) return null;
        long until=y.getLong(p+".until"); int issuer=y.getInt(p+".issuer-level",6);
        if(until<0 || issuer<0 || issuer>7) throw new IllegalArgumentException("Invalid sanction data: "+p);
        return new Sanction(until,y.getString(p+".reason","Staff action"),y.getString(p+".actor","Console"),issuer);
    }
    public Entry get(UUID id) { return entries.getOrDefault(id,new Entry(id.toString(),Rank.MEMBER,null,null,List.of())); }
    public Map<UUID,Entry> snapshot() { return entries; }
    /** Serialize only one player's public rank or sanction data for BlinkNetwork. */
    public String exportNetworkModule(UUID id,String kind) {
        Entry e=get(id);YamlConfiguration y=new YamlConfiguration();y.set("version",1);
        if(kind.equals("ranks")) {
            y.set("name",e.name());y.set("rank",e.rank().name());y.set("ranks",rankNames(e));
        } else if(kind.equals("sanctions")) {
            write(y,"mute",e.mute());write(y,"ban",e.ban());y.set("warnings",e.warnings());
        } else throw new IllegalArgumentException("Unsupported rank module");
        return y.saveToString();
    }
    /** May be called on an async pre-login thread, before rank ban enforcement. */
    public synchronized void importNetworkModule(UUID id,String kind,String data) throws IOException {
        if(data.length()>131072)throw new IOException("Rank profile too large");
        YamlConfiguration y=new YamlConfiguration();
        try {
            y.loadFromString(data);
            if(y.getInt("version")!=1)throw new IllegalArgumentException("Invalid rank profile version");
            Entry old=get(id),next;
            if(kind.equals("ranks")) {
                String name=y.getString("name",old.name());
                if(name.length()>64)throw new IllegalArgumentException("Name too large");
                next=new Entry(name,readRanks(y,""),old.mute(),old.ban(),old.warnings());
            } else if(kind.equals("sanctions")) {
                List<String> warnings=y.getStringList("warnings");
                if(warnings.size()>500)throw new IllegalArgumentException("Too many warnings");
                next=new Entry(old.name(),old.ranks(),read(y,"mute"),read(y,"ban"),warnings);
            } else throw new IllegalArgumentException("Unsupported rank module");
            put(id,next);
        } catch(org.bukkit.configuration.InvalidConfigurationException | RuntimeException ex) {
            throw new IOException("Invalid rank profile; local state preserved",ex);
        }
    }
    public synchronized void put(UUID id,Entry entry) throws IOException {
        Map<UUID,Entry> next=new HashMap<>(entries); next.put(id,entry);
        YamlConfiguration y=new YamlConfiguration();
        for(var row:next.entrySet()) {
            String p="players."+row.getKey(); Entry e=row.getValue();
            y.set(p+".name",e.name()); y.set(p+".rank",e.rank().name());y.set(p+".ranks",rankNames(e));
            write(y,p+".mute",e.mute()); write(y,p+".ban",e.ban());
            y.set(p+".warnings",e.warnings());
        }
        Files.createDirectories(path.getParent());
        Path tmp=Files.createTempFile(path.getParent(),"players-",".tmp");
        try {
            Files.writeString(tmp,y.saveToString(),StandardCharsets.UTF_8);
            try(FileChannel channel=FileChannel.open(tmp,StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING); }
            entries=Map.copyOf(next);
        } finally { Files.deleteIfExists(tmp); }
    }
    private static void write(YamlConfiguration y,String p,Sanction s) {
        if(s==null) return;
        y.set(p+".until",s.until()); y.set(p+".reason",s.reason());
        y.set(p+".actor",s.actor()); y.set(p+".issuer-level",s.issuerLevel());
    }
}

