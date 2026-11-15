package shop.ultimatesmp.weather;
import java.sql.*;import java.util.*;import javax.sql.DataSource;
/** Isolated additive tables; never reads or writes players, regions, inventories or profiles. */
final class WorldStore {
 static final String STATE="ultimatesmp_weather_time_v1",REQUESTS="ultimatesmp_weather_requests_v1";
 record Intent(long id,String kind,Snapshot state){}
 record Change(String kind,Snapshot state){}
 record Batch(List<Snapshot> snapshots,List<Intent> requests){}
 private final DataSource source;
 WorldStore(DataSource source){this.source=source;}
 void initialize()throws SQLException{try(Connection c=source.getConnection();Statement s=c.createStatement()){
  s.executeUpdate("CREATE TABLE IF NOT EXISTS "+STATE+" (dimension VARCHAR(16) PRIMARY KEY,source_id VARCHAR(64) NOT NULL,full_time BIGINT NOT NULL,daylight BOOLEAN NOT NULL,weather_cycle BOOLEAN NOT NULL,storm BOOLEAN NOT NULL,thunder BOOLEAN NOT NULL,rain_ticks INT NOT NULL,thunder_ticks INT NOT NULL,clear_ticks INT NOT NULL,updated_at DATETIME(6) NOT NULL) ENGINE=InnoDB");
  s.executeUpdate("CREATE TABLE IF NOT EXISTS "+REQUESTS+" (id BIGINT AUTO_INCREMENT PRIMARY KEY,kind VARCHAR(8) NOT NULL,dimension VARCHAR(16) NOT NULL,full_time BIGINT NOT NULL,daylight BOOLEAN NOT NULL,weather_cycle BOOLEAN NOT NULL,storm BOOLEAN NOT NULL,thunder BOOLEAN NOT NULL,rain_ticks INT NOT NULL,thunder_ticks INT NOT NULL,clear_ticks INT NOT NULL,updated_at DATETIME(6) NOT NULL,INDEX(updated_at)) ENGINE=InnoDB");
 }}
 Batch exchange(String server,String master,boolean leader,List<Snapshot> local,List<Change> edits,List<Long> acknowledged)throws SQLException{
  try(Connection c=source.getConnection()){c.setAutoCommit(false);try{
   if(leader){
    try(PreparedStatement p=c.prepareStatement("INSERT INTO "+STATE+"(dimension,source_id,full_time,daylight,weather_cycle,storm,thunder,rain_ticks,thunder_ticks,clear_ticks,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE source_id=VALUES(source_id),full_time=VALUES(full_time),daylight=VALUES(daylight),weather_cycle=VALUES(weather_cycle),storm=VALUES(storm),thunder=VALUES(thunder),rain_ticks=VALUES(rain_ticks),thunder_ticks=VALUES(thunder_ticks),clear_ticks=VALUES(clear_ticks),updated_at=VALUES(updated_at)")){
     for(Snapshot s:local){p.setString(1,s.dimension());p.setString(2,server);values(p,s,3);p.addBatch();}p.executeBatch();
    }
    try(PreparedStatement p=c.prepareStatement("DELETE FROM "+REQUESTS+" WHERE id=?")){for(long id:acknowledged){p.setLong(1,id);p.addBatch();}p.executeBatch();}
   }else if(!edits.isEmpty())try(PreparedStatement p=c.prepareStatement("INSERT INTO "+REQUESTS+"(kind,dimension,full_time,daylight,weather_cycle,storm,thunder,rain_ticks,thunder_ticks,clear_ticks,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))")){
    for(Change change:edits){if(!Set.of("time","weather").contains(change.kind))throw new IllegalArgumentException("Invalid change");p.setString(1,change.kind);p.setString(2,change.state.dimension());values(p,change.state,3);p.addBatch();}p.executeBatch();
   }
   var states=new ArrayList<Snapshot>();var requests=new ArrayList<Intent>();
   if(leader){
    try(Statement p=c.createStatement()){p.executeUpdate("DELETE FROM "+REQUESTS+" WHERE updated_at<UTC_TIMESTAMP(6)-INTERVAL 30 SECOND");}
    try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT *,GREATEST(0,TIMESTAMPDIFF(MICROSECOND,updated_at,UTC_TIMESTAMP(6)) DIV 1000) AS age FROM "+REQUESTS+" ORDER BY id LIMIT 64")){while(r.next())requests.add(new Intent(r.getLong("id"),r.getString("kind"),snapshot(r)));}
   }else try(PreparedStatement p=c.prepareStatement("SELECT *,GREATEST(0,TIMESTAMPDIFF(MICROSECOND,updated_at,UTC_TIMESTAMP(6)) DIV 1000) AS age FROM "+STATE+" WHERE source_id=?")){p.setString(1,master);try(ResultSet r=p.executeQuery()){while(r.next())states.add(snapshot(r));}}
   c.commit();return new Batch(List.copyOf(states),List.copyOf(requests));
  }catch(Exception e){c.rollback();throw e;}}
 }
 private static void values(PreparedStatement p,Snapshot s,int i)throws SQLException{p.setLong(i++,s.fullTime());p.setBoolean(i++,s.daylight());p.setBoolean(i++,s.weatherCycle());p.setBoolean(i++,s.storm());p.setBoolean(i++,s.thunder());p.setInt(i++,s.rainTicks());p.setInt(i++,s.thunderTicks());p.setInt(i,s.clearTicks());}
 private static Snapshot snapshot(ResultSet r)throws SQLException{return new Snapshot(r.getString("dimension"),r.getLong("full_time"),r.getBoolean("daylight"),r.getBoolean("weather_cycle"),r.getBoolean("storm"),r.getBoolean("thunder"),r.getInt("rain_ticks"),r.getInt("thunder_ticks"),r.getInt("clear_ticks"),r.getLong("age"));}
}
