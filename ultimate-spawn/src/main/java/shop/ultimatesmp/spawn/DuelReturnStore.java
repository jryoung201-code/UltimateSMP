package shop.ultimatesmp.spawn;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
/** Durable return markers and pre-duel vitals only. Never stores or replaces inventories. */
final class DuelReturnStore {
 private final Path file;DuelReturnStore(Path file){this.file=file;}
 Map<UUID,DuelVitals> read()throws Exception{
  Map<UUID,DuelVitals> result=new HashMap<>();if(!Files.exists(file))return result;
  YamlConfiguration y=new YamlConfiguration();y.loadFromString(Files.readString(file));
  if(y.getInt("version")!=1)throw new IOException("Unsupported duel return data");
  var players=y.getConfigurationSection("players");if(players==null)return result;
  for(String key:players.getKeys(false))result.put(UUID.fromString(key),DuelVitals.read(y.getConfigurationSection("players."+key)));
  return result;
 }
 void save(Map<UUID,DuelVitals> states)throws IOException{
  YamlConfiguration y=new YamlConfiguration();y.set("version",1);
  for(var entry:states.entrySet())entry.getValue().write(y.createSection("players."+entry.getKey()));
  Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),"duel-returns-",".tmp");
  try{Files.writeString(tmp,y.saveToString(),StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
 }
}
