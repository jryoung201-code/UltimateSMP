package shop.blinksmp.network.paper;

import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Bridges modules through explicit plugin APIs. No reflective access to private plugin fields. */
final class ProfileSync {
    private static final List<String> ORDER=List.of("ranks","sanctions","homes","settings","statistics","advancements");
    private final Set<String> enabled;
    ProfileSync(JavaPlugin network) {
        if(!network.getConfig().getBoolean("profile-sync.enabled",false)){enabled=Set.of();return;}
        Set<String> chosen=new LinkedHashSet<>();
        for(String kind:ORDER)if(network.getConfig().getBoolean("profile-sync."+kind, false))chosen.add(kind);
        enabled=Set.copyOf(chosen);
    }
    boolean active(){return !enabled.isEmpty();}
    boolean includes(String kind){return enabled.contains(kind);}
    Set<String> kinds(){return enabled;}
    private static String pluginName(String kind) {
        return switch(kind) {
            case "ranks","sanctions" -> "UltimateRanks";
            case "homes" -> "UltimateHomes";
            case "settings" -> "UltimateSettings";
            default -> throw new IllegalArgumentException("Not a plugin category");
        };
    }
    private static Plugin getPlugin(String kind) {
        Plugin plugin=Bukkit.getPluginManager().getPlugin(pluginName(kind));
        if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("Profile sync requires enabled "+pluginName(kind));
        return plugin;
    }
    String capture(UUID player,String kind) throws Exception {
        if(!includes(kind))throw new IllegalArgumentException("Module not configured");
        if(kind.equals("statistics")||kind.equals("advancements")){
            Player p=Bukkit.getPlayer(player);if(p==null||!p.isOnline())throw new IllegalStateException("Player is offline");
            return VanillaProfiles.export(p,kind);
        }
        Plugin plugin=getPlugin(kind);
        try{return (String)plugin.getClass().getMethod("exportNetworkModule",UUID.class,String.class).invoke(plugin,player,kind);}
        catch(InvocationTargetException ex){throw new IOException("Profile export failed: "+kind,ex.getCause());}
    }
    /** The two rank modules may be restored during AsyncPlayerPreLoginEvent before rank ban checks. */
    void importRank(UUID player,String kind,String payload) throws Exception {
        if(!includes(kind))return;
        if(!kind.equals("ranks")&&!kind.equals("sanctions"))throw new IllegalArgumentException("Not a rank module");
        importPlugin(player,kind,payload);
    }
    void apply(Player player,String kind,String payload) throws Exception {
        if(!includes(kind))return;
        if(kind.equals("ranks")||kind.equals("sanctions"))return; // Applied before the rank login event.
        if(kind.equals("statistics")||kind.equals("advancements"))VanillaProfiles.apply(player,kind,payload);
        else importPlugin(player.getUniqueId(),kind,payload);
    }
    private static void importPlugin(UUID player,String kind,String payload) throws Exception {
        Plugin plugin=getPlugin(kind);
        try{plugin.getClass().getMethod("importNetworkModule",UUID.class,String.class,String.class).invoke(plugin,player,kind,payload);}
        catch(InvocationTargetException ex){throw new IOException("Profile import failed: "+kind,ex.getCause());}
    }
    static String empty(UUID player,String kind) {
        return switch(kind) {
            case "ranks"->"version: 1\nname: '"+player+"'\nrank: MEMBER\n";
            case "sanctions"->"version: 1\nwarnings: []\n";
            case "homes"->"version: 1\nowner: '"+player+"'\nhomes: {}\n";
            case "settings"->"version: 1\nbooster: false\nrank-tags: true\nplayers: true\nmessages: true\njoin-messages: true\ncoordinates: false\nexclude-monsters: false\n";
            case "statistics"->"version: 1\nstatistics: {}\n";
            case "advancements"->"version: 1\nadvancements: {}\n";
            default->throw new IllegalArgumentException("Invalid category");
        };
    }
    static byte[] bytes(String data) {
        byte[] result=data.getBytes(StandardCharsets.UTF_8);
        if(result.length>2_097_152)throw new IllegalArgumentException("Module exceeds 2 MiB");
        return result;
    }
    static String text(byte[] data) {return new String(data,StandardCharsets.UTF_8);}
}


