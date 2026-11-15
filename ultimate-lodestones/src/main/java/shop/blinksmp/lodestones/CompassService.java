package shop.blinksmp.lodestones;

import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;

public final class CompassService {
    public static final NamespacedKey TARGET_KEY=new NamespacedKey("ultimatelodestones","target");
    private final Map<String,World> worlds;private final boolean adopt;
    public CompassService(Map<String,World> worlds,boolean adopt){this.worlds=Map.copyOf(worlds);this.adopt=adopt;}
    private static String dimension(World world){return switch(world.getEnvironment()){case NORMAL->"Overworld";case NETHER->"Nether";case THE_END->"End";default->null;};}
    private Target nativeTarget(CompassMeta meta){
        if(!meta.hasLodestone())return null;
        Location location=meta.getLodestone();if(location==null)return null;
        String dimension=null;
        if(location.getWorld()!=null){
            dimension=dimension(location.getWorld());
            // Other custom worlds are not silently collapsed onto the three configured worlds.
            if(dimension==null||!Objects.equals(worlds.get(dimension),location.getWorld()))return null;
        }else{
            // Paper 1.21.11 serializes the dimension key even when that world is not loaded locally.
            dimension=switch(String.valueOf(meta.serialize().get("LodestonePosWorld"))){
                case "minecraft:overworld"->"Overworld";case "minecraft:the_nether"->"Nether";case "minecraft:the_end"->"End";default->null;
            };
        }
        if(dimension==null)return null;
        try{return new Target(dimension,location.getBlockX(),location.getBlockY(),location.getBlockZ());}catch(IllegalArgumentException ex){return null;}
    }
    public Target target(CompassMeta meta){
        // A valid new vanilla binding takes precedence over an older portable tag.
        Target nativeTarget=nativeTarget(meta);if(nativeTarget!=null||meta.hasLodestone())return nativeTarget;
        try{return Target.decode(meta.getPersistentDataContainer().get(TARGET_KEY,PersistentDataType.STRING));}catch(IllegalArgumentException ex){return null;}
    }
    public boolean repair(ItemStack item){
        if(item==null||item.getType()!=Material.COMPASS||!(item.getItemMeta() instanceof CompassMeta meta))return false;
        String stored=meta.getPersistentDataContainer().get(TARGET_KEY,PersistentDataType.STRING);
        if(stored==null&&!adopt)return false;
        Target target=target(meta);if(target==null)return false;
        World world=worlds.get(target.dimension());if(world==null)return false; // Never load worlds/chunks or discard unresolved targets.
        Location desired=new Location(world,target.x(),target.y(),target.z());
        Location current=meta.hasLodestone()?meta.getLodestone():null;
        boolean changed=false;
        if(current==null||!Objects.equals(current.getWorld(),world)||current.getBlockX()!=target.x()||current.getBlockY()!=target.y()||current.getBlockZ()!=target.z()){
            meta.setLodestone(desired);changed=true;
        }
        if(meta.isLodestoneTracked()){meta.setLodestoneTracked(false);changed=true;}
        if(!target.encode().equals(stored)){meta.getPersistentDataContainer().set(TARGET_KEY,PersistentDataType.STRING,target.encode());changed=true;}
        return changed&&item.setItemMeta(meta);
    }
    public boolean forget(ItemStack item){
        if(item==null||item.getType()!=Material.COMPASS||!(item.getItemMeta() instanceof CompassMeta meta))return false;
        meta.getPersistentDataContainer().remove(TARGET_KEY);meta.clearLodestone();return item.setItemMeta(meta);
    }
}
