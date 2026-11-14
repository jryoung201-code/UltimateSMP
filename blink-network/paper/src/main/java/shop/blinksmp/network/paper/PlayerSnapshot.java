package shop.blinksmp.network.paper;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.*;
import org.bukkit.attribute.Attribute;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** No Java object deserialization. Paper NBT preserves item components/custom model data. */
final class PlayerSnapshot {
    final YamlConfiguration yaml;
    final ItemStack[] inventory,ender;
    final List<PotionEffect> effects;
    private PlayerSnapshot(YamlConfiguration y) {
        yaml=y;
        if(y.getInt("version")!=1)throw new IllegalArgumentException("Unsupported snapshot");
        RegionCheck.dimension(y.getString("dimension",""));
        for(String key:List.of("x","y","z","yaw","pitch","health","saturation","exhaustion","exp"))
            if(!y.isSet(key)||!Double.isFinite(y.getDouble(key)))throw new IllegalArgumentException("Invalid snapshot "+key);
        if(Math.abs(y.getDouble("x"))>29_999_984||Math.abs(y.getDouble("z"))>29_999_984||y.getDouble("health")<0||y.getInt("food")<0||y.getInt("food")>20||y.getDouble("exp")<0||y.getDouble("exp")>1||y.getInt("level")<0||y.getInt("slot")<0||y.getInt("slot")>8)throw new IllegalArgumentException("Out of range snapshot");
        GameMode.valueOf(y.getString("gamemode",""));
        inventory=ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("inventory","")));
        ender=ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("ender","")));
        InventoryLayout.validate(inventory.length,ender.length);
        effects=new ArrayList<>();
        var section=y.getConfigurationSection("effects");
        if(section!=null)for(String key:section.getKeys(false)) {
            PotionEffectType type=Registry.EFFECT.get(Objects.requireNonNull(NamespacedKey.fromString(section.getString(key+".type",""))));
            if(type==null)throw new IllegalArgumentException("Unknown effect");
            effects.add(new PotionEffect(type,section.getInt(key+".duration"),section.getInt(key+".amplifier"),section.getBoolean(key+".ambient"),section.getBoolean(key+".particles"),section.getBoolean(key+".icon")));
        }
    }
    static PlayerSnapshot capture(Player p,String dimension,Location location) {
        
        var y=new YamlConfiguration();y.set("version",1);y.set("dimension",dimension);
        y.set("x",location.getX());y.set("y",location.getY());y.set("z",location.getZ());y.set("yaw",location.getYaw());y.set("pitch",location.getPitch());
        y.set("inventory",Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(Arrays.asList(p.getInventory().getContents()))));
        y.set("ender",Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(Arrays.asList(p.getEnderChest().getContents()))));
        y.set("dead",p.isDead()||p.getHealth()<=0);y.set("health",p.getHealth());y.set("food",p.getFoodLevel());y.set("saturation",p.getSaturation());y.set("exhaustion",p.getExhaustion());
        y.set("level",p.getLevel());y.set("exp",p.getExp());y.set("total-exp",p.getTotalExperience());y.set("gamemode",p.getGameMode().name());
        y.set("slot",p.getInventory().getHeldItemSlot());y.set("air",p.getRemainingAir());y.set("fire",p.getFireTicks());
        int i=0;for(PotionEffect e:p.getActivePotionEffects()){
            String k="effects."+(i++)+".";y.set(k+"type",e.getType().getKey().toString());y.set(k+"duration",e.getDuration());y.set(k+"amplifier",e.getAmplifier());y.set(k+"ambient",e.isAmbient());y.set(k+"particles",e.hasParticles());y.set(k+"icon",e.hasIcon());
        }return new PlayerSnapshot(y);
    }
    static PlayerSnapshot decode(byte[] data) throws Exception {
        if(data.length>2_097_152)throw new IllegalArgumentException("Snapshot too large");
        var y=new YamlConfiguration();y.loadFromString(new String(data,StandardCharsets.UTF_8));return new PlayerSnapshot(y);
    }
    byte[] bytes(){return yaml.saveToString().getBytes(StandardCharsets.UTF_8);}
    Location location(World world){return new Location(world,yaml.getDouble("x"),yaml.getDouble("y"),yaml.getDouble("z"),(float)yaml.getDouble("yaw"),(float)yaml.getDouble("pitch"));}
    String dimension(){return yaml.getString("dimension");}
    void relocate(Location location){
        yaml.set("x",location.getX());yaml.set("y",location.getY());yaml.set("z",location.getZ());
        yaml.set("yaw",location.getYaw());yaml.set("pitch",location.getPitch());
    }
    void apply(Player p){
        p.setGameMode(GameMode.valueOf(yaml.getString("gamemode")));
        p.getInventory().setContents(InventoryLayout.adapt(inventory,p.getInventory().getSize()));p.getEnderChest().setContents(ender);p.getInventory().setHeldItemSlot(yaml.getInt("slot"));
        for(PotionEffect e:List.copyOf(p.getActivePotionEffects()))p.removePotionEffect(e.getType());p.addPotionEffects(effects);
        double max=Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue();
        p.setHealth(yaml.getBoolean("dead")?max:Math.min(yaml.getDouble("health"),max));
        p.setFoodLevel(yaml.getInt("food"));p.setSaturation((float)yaml.getDouble("saturation"));p.setExhaustion((float)yaml.getDouble("exhaustion"));
        p.setLevel(yaml.getInt("level"));p.setExp((float)yaml.getDouble("exp"));p.setTotalExperience(yaml.getInt("total-exp"));
        p.setRemainingAir(Math.min(yaml.getInt("air"),p.getMaximumAir()));p.setFireTicks(Math.max(0,yaml.getInt("fire")));p.setFallDistance(0);
    }
    private static class RegionCheck {static void dimension(String d){if(!d.matches("Overworld|Nether|End"))throw new IllegalArgumentException("Invalid dimension");}}
}

