package shop.ultimatesmp.crates;
import java.util.*;import java.util.function.Consumer;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import org.bukkit.*;import org.bukkit.block.Block;import org.bukkit.entity.TextDisplay;import org.bukkit.plugin.java.JavaPlugin;
class CrateLabelsTest {
 @Test void reusesOneNonPersistentLabelAndCleansItWhenHidden(){
  World world=mock(World.class);Block block=mock(Block.class);TextDisplay text=mock(TextDisplay.class);JavaPlugin plugin=mock(JavaPlugin.class);when(world.isChunkLoaded(0,0)).thenReturn(true);when(world.getBlockAt(0,64,0)).thenReturn(block);when(block.getType()).thenReturn(Material.CHEST);when(text.isValid()).thenReturn(true);
  doAnswer(call->{Consumer<TextDisplay> setup=call.getArgument(2);setup.accept(text);return text;}).when(world).spawn(any(Location.class),eq(TextDisplay.class),any(Consumer.class));
  var values=new HashMap<String,Crate>();Crate c=new Crate("rare","Rare",1,List.of());values.put(c.id(),c);var bindings=new HashMap<String,String>();bindings.put("ultimate_spawn:0:64:0","rare");
  try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(()->Bukkit.getWorld("ultimate_spawn")).thenReturn(world);var labels=new CrateLabels(plugin,()->values,()->bindings);labels.sync();labels.sync();verify(world,times(1)).spawn(any(Location.class),eq(TextDisplay.class),any(Consumer.class));verify(text).setPersistent(false);verify(text,times(1)).text(any());values.put(c.id(),new Crate(c.id(),c.name(),2,c.rewards(),c.label().visible(false)));labels.sync();verify(text).remove();labels.close();verify(text,times(1)).remove();}
 }
 @Test void doesNotLoadChunksForDistantCrates(){World world=mock(World.class);try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(()->Bukkit.getWorld("ultimate_spawn")).thenReturn(world);Crate c=new Crate("rare","Rare",1,List.of());var labels=new CrateLabels(mock(JavaPlugin.class),()->Map.of("rare",c),()->Map.of("ultimate_spawn:400:64:0","rare"));labels.sync();verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());verify(world,never()).spawn(any(Location.class),eq(TextDisplay.class),any(Consumer.class));}}
}

