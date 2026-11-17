package shop.ultimatesmp.spawn;
import java.nio.file.*;import org.bukkit.*;
final class DuelArena {
 static World open(UltimateSpawn plugin)throws Exception{
  String name=plugin.getConfig().getString("duels.world-name","ultimate_duel_arena");
  if(!name.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid arena world name");
  Path folder=Bukkit.getWorldContainer().toPath().resolve(name),marker=plugin.getDataFolder().toPath().resolve("arena-"+name+".marker");boolean fresh=!Files.exists(folder);
  if(!fresh&&!Files.exists(marker))throw new IllegalArgumentException("Arena name is already in use; choose a new world name");
  World w=new WorldCreator(name).environment(World.Environment.NORMAL).type(WorldType.FLAT).generateStructures(false).createWorld();if(w==null)throw new IllegalStateException("Arena world unavailable");
  if(fresh){
   for(int x=-25;x<=25;x++)for(int z=-25;z<=25;z++){
    boolean edge=Math.abs(x)==25||Math.abs(z)==25;
    w.getBlockAt(x,80,z).setType(edge?Material.POLISHED_BLACKSTONE_BRICKS:((x+z)%8==0?Material.POLISHED_ANDESITE:Material.SMOOTH_STONE),false);
    for(int y=81;y<=84;y++)w.getBlockAt(x,y,z).setType(edge?Material.TINTED_GLASS:Material.AIR,false);
   }
   for(int x:new int[]{-25,25})for(int z:new int[]{-25,25})for(int y=81;y<=86;y++)w.getBlockAt(x,y,z).setType(y==86?Material.SEA_LANTERN:Material.CRYING_OBSIDIAN,false);
   w.setSpawnLocation(0,81,0);w.save();Files.createDirectories(marker.getParent());Files.writeString(marker,"UltimateSpawn duel arena v1\n");
  }
  w.setPVP(true);w.setTime(6000);w.setStorm(false);w.setThundering(false);w.setGameRule(GameRule.DO_MOB_SPAWNING,false);w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);w.setGameRule(GameRule.DO_WEATHER_CYCLE,false);w.setGameRule(GameRule.KEEP_INVENTORY,true);w.setGameRule(GameRule.MOB_GRIEFING,false);w.setGameRule(GameRule.DO_FIRE_TICK,false);w.setGameRule(GameRule.RANDOM_TICK_SPEED,0);w.setViewDistance(3);w.setSimulationDistance(2);w.getWorldBorder().setCenter(.5,.5);w.getWorldBorder().setSize(64);
  plugin.registerActivityWorld(w);return w;
 }
 static Location side(World world,boolean first){return new Location(world,first?-12.5:12.5,81,.5,first?-90:90,0);}
}
