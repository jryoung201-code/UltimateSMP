package shop.ultimatesmp.crates;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.sql.*;import org.bukkit.configuration.file.YamlConfiguration;
/** Serialized reward definitions only; never stores or modifies player inventories. */
final class CrateStore {
 private final Path file;private final String url,user,password;
 CrateStore(Path file,String url,String user,String password){this.file=file;this.url=url;this.user=user;this.password=password;}
 private Connection connection()throws SQLException{Properties p=new Properties();p.setProperty("user",user);p.setProperty("password",password);p.setProperty("connectTimeout","3000");p.setProperty("socketTimeout","5000");return new org.mariadb.jdbc.Driver().connect(url,p);}
 void initialize()throws Exception{if(url==null){Files.createDirectories(file.getParent());return;}try(var c=connection();var s=c.createStatement()){s.executeUpdate("CREATE TABLE IF NOT EXISTS ultimatesmp_crates_v1 (id VARCHAR(32) PRIMARY KEY,revision BIGINT NOT NULL,payload MEDIUMTEXT NOT NULL) ENGINE=InnoDB");}}
 Map<String,String> load()throws Exception{Map<String,String> out=new TreeMap<>();if(url==null){if(Files.exists(file)){var y=new YamlConfiguration();y.load(file.toFile());for(String k:y.getKeys(false))out.put(k,y.getString(k));}return out;}try(var c=connection();var q=c.createStatement();var r=q.executeQuery("SELECT id,payload FROM ultimatesmp_crates_v1")){while(r.next())out.put(r.getString(1),r.getString(2));}return out;}
 boolean save(String id,long previous,String payload)throws Exception{
  if(url==null){Map<String,String> all=load();String old=all.get(id);if(previous==0?old!=null:old==null||revision(old)!=previous)return false;all.put(id,payload);var y=new YamlConfiguration();all.forEach(y::set);Path tmp=Files.createTempFile(file.getParent(),"crates-",".tmp");try{Files.writeString(tmp,y.saveToString(),StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}return true;}
  try(var c=connection()){if(previous==0){try(var q=c.prepareStatement("INSERT IGNORE INTO ultimatesmp_crates_v1(id,revision,payload) VALUES(?,1,?)")){q.setString(1,id);q.setString(2,payload);return q.executeUpdate()==1;}}try(var q=c.prepareStatement("UPDATE ultimatesmp_crates_v1 SET revision=revision+1,payload=? WHERE id=? AND revision=?")){q.setString(1,payload);q.setString(2,id);q.setLong(3,previous);return q.executeUpdate()==1;}}
 }
 private long revision(String payload)throws Exception{var y=new YamlConfiguration();y.loadFromString(payload);return y.getLong("revision");}
}

