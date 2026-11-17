package shop.ultimatesmp.spawn;
import javax.sql.DataSource;import java.sql.*;
final class SpawnStore {
 static final String TABLE="ultimatesmp_spawn_v1";private final DataSource source;
 SpawnStore(DataSource source){this.source=source;}
 void initialize()throws SQLException{try(var c=source.getConnection();var q=c.createStatement()){q.executeUpdate("CREATE TABLE IF NOT EXISTS "+TABLE+" (id INT PRIMARY KEY,server_id VARCHAR(64) NOT NULL,world_name VARCHAR(64) NOT NULL,x DOUBLE NOT NULL,y DOUBLE NOT NULL,z DOUBLE NOT NULL,yaw FLOAT NOT NULL,pitch FLOAT NOT NULL,revision BIGINT NOT NULL DEFAULT 1) ENGINE=InnoDB");}}
 void save(SpawnPoint p,boolean onlyIfAbsent)throws SQLException{String tail=onlyIfAbsent?"id=id":"server_id=VALUES(server_id),world_name=VALUES(world_name),x=VALUES(x),y=VALUES(y),z=VALUES(z),yaw=VALUES(yaw),pitch=VALUES(pitch),revision=revision+1";
  try(var c=source.getConnection();var q=c.prepareStatement("INSERT INTO "+TABLE+"(id,server_id,world_name,x,y,z,yaw,pitch) VALUES(1,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "+tail)){q.setString(1,p.server());q.setString(2,p.world());q.setDouble(3,p.x());q.setDouble(4,p.y());q.setDouble(5,p.z());q.setFloat(6,p.yaw());q.setFloat(7,p.pitch());q.executeUpdate();}}
 SpawnPoint load()throws SQLException{try(var c=source.getConnection();var q=c.createStatement();var r=q.executeQuery("SELECT * FROM "+TABLE+" WHERE id=1")){return r.next()?new SpawnPoint(r.getString("server_id"),r.getString("world_name"),r.getDouble("x"),r.getDouble("y"),r.getDouble("z"),r.getFloat("yaw"),r.getFloat("pitch")):null;}}
}

