package shop.blinksmp.ranks;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
/** Preserve custom grants/weights while migrating legacy donor configuration keys. */
public final class RankConfig {
    private RankConfig(){}
    public static void migrate(FileConfiguration config) {
        for(String suffix:List.of("PLUS","PLUS_PLUS","PLUS_PLUS_PLUS")) {
            for(String section:List.of("weights","prefixes","extra-permissions")) {
                String old=section+".BLINK_"+suffix,next=section+".ULTIMATE_"+suffix;
                if(config.contains(old,true)) {
                    if(!config.contains(next,true))config.set(next,config.get(old));
                    config.set(old,null);
                }
            }
            String path="prefixes.ULTIMATE_"+suffix;
            if(config.contains(path,true))config.set(path,config.getString(path,"").replace("Blink+","Ultimate+"));
        }
    }
}
