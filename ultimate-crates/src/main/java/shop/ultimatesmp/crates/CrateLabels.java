package shop.ultimatesmp.crates;
import java.util.*;import java.util.function.Supplier;import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.world.*;import org.bukkit.plugin.java.JavaPlugin;import org.bukkit.util.Transformation;import org.joml.*;
final class CrateLabels implements Listener {
 record Position(String world,int x,int y,int z){static Position parse(String value){String[] parts=value.split(":");if(parts.length!=4)throw new IllegalArgumentException();return new Position(parts[0],Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]));}}
 private final JavaPlugin plugin;private final Supplier<Map<String,Crate>> crates;private final Supplier<Map<String,String>> bindings;private final Map<String,TextDisplay> displays=new HashMap<>();private final Map<String,String> rendered=new HashMap<>();
 CrateLabels(JavaPlugin plugin,Supplier<Map<String,Crate>> crates,Supplier<Map<String,String>> bindings){this.plugin=plugin;this.crates=crates;this.bindings=bindings;}
 void sync(){Set<String> keep=new HashSet<>();for(var binding:bindings.get().entrySet())try{
  Crate c=crates.get().get(binding.getValue());if(c==null||!c.label().visible())continue;Position pos=Position.parse(binding.getKey());World world=Bukkit.getWorld(pos.world());if(world==null||!world.isChunkLoaded(pos.x()>>4,pos.z()>>4))continue;Material type=world.getBlockAt(pos.x(),pos.y(),pos.z()).getType();if(type!=Material.CHEST&&type!=Material.TRAPPED_CHEST&&type!=Material.BARREL)continue;
  String key=binding.getKey();keep.add(key);TextDisplay display=displays.get(key);if(display==null||!display.isValid()){display=world.spawn(new Location(world,pos.x()+.5,pos.y()+c.label().height(),pos.z()+.5),TextDisplay.class,e->{e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setBillboard(Display.Billboard.CENTER);e.setAlignment(TextDisplay.TextAlignment.CENTER);e.setDefaultBackground(false);e.setBackgroundColor(Color.fromARGB(0));e.setShadowed(true);e.setSeeThrough(false);e.setTextOpacity((byte)-1);e.setLineWidth(240);e.setViewRange(.5f);e.setBrightness(new Display.Brightness(15,15));});displays.put(key,display);rendered.remove(key);}
  String version=c.name()+"\0"+c.label();if(!version.equals(rendered.get(key))){display.text(c.label().component(c.name()));display.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(c.label().size()),new Quaternionf()));display.teleport(new Location(world,pos.x()+.5,pos.y()+c.label().height(),pos.z()+.5));rendered.put(key,version);}
 }catch(IllegalArgumentException ignored){}
  for(String key:new ArrayList<>(displays.keySet()))if(!keep.contains(key))remove(key);
 }
 private void remove(String key){TextDisplay display=displays.remove(key);rendered.remove(key);if(display!=null)display.remove();}
 @EventHandler public void load(ChunkLoadEvent e){Bukkit.getScheduler().runTask(plugin,this::sync);}
 @EventHandler public void unload(ChunkUnloadEvent e){for(String key:new ArrayList<>(displays.keySet())){Position p=Position.parse(key);if(p.world().equals(e.getWorld().getName())&&(p.x()>>4)==e.getChunk().getX()&&(p.z()>>4)==e.getChunk().getZ())remove(key);}}
 @EventHandler public void worldUnload(WorldUnloadEvent e){for(String key:new ArrayList<>(displays.keySet()))if(Position.parse(key).world().equals(e.getWorld().getName()))remove(key);}
 void close(){for(String key:new ArrayList<>(displays.keySet()))remove(key);}
}

