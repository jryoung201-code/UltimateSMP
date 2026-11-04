package shop.blinksmp.tpr;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CommandAliasesTest {
    public static void main(String[] args) throws Exception {
        YamlConfiguration yaml=new YamlConfiguration();
        try(var input=CommandAliasesTest.class.getResourceAsStream("/plugin.yml")) {
            yaml.loadFromString(new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8));
        }
        Map<String,String> names=Map.of("tpr","teleportRequest","tprhere","teleportRequestHere","tpraccept","teleportRequestAccept","tprcancel","teleportRequestCancel","tprdeny","teleportRequestDeny");
        for(var entry:names.entrySet()) {
            ConfigurationSection command=Objects.requireNonNull(yaml.getConfigurationSection("commands."+entry.getKey()));
            if(!command.getStringList("aliases").contains(entry.getValue())) throw new AssertionError("Alias missing: "+entry);
            if(!"ultimatetpr.use".equals(command.getString("permission"))) throw new AssertionError("Wrong command permission");
        }
        if(!yaml.getBoolean("permissions.ultimatetpr.use.default")) throw new AssertionError("Commands must be available to ordinary players");
        System.out.println("PASS: corrected command aliases and player permissions parse from plugin.yml");
    }
}
