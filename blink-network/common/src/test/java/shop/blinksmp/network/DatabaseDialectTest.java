package shop.blinksmp.network;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DatabaseDialectTest {
 @Test void postgresQueriesAreUnchanged(){
  var d=DatabaseDialect.from("jdbc:postgresql://localhost/test");String sql="INSERT INTO blinksmp_network_v1.servers(id,dimension) VALUES(?,?) ON CONFLICT DO NOTHING";
  assertEquals(sql,d.sql(sql));assertFalse(d.mysql);
 }
 @Test void bothMysqlUrlSchemesUseTheCrossCompatibleDriver(){
  for(String scheme:new String[]{"mysql","mariadb"}){
   var d=DatabaseDialect.from("jdbc:"+scheme+"://localhost/test");assertTrue(d.mysql);
   assertEquals("jdbc:mariadb://localhost/test",d.url("jdbc:"+scheme+"://localhost/test"));
   assertEquals("org.mariadb.jdbc.Driver",d.driver());
  }
 }
 @Test void duplicateProfilesStayNoOpAndUpsertsUseInsertedValues(){
  var d=DatabaseDialect.from("jdbc:mysql://localhost/test");
  assertEquals("INSERT INTO blinksmp_network_v1_player_modules(player_id,category) VALUES(?,?) ON DUPLICATE KEY UPDATE player_id=player_id",d.sql("INSERT INTO blinksmp_network_v1.player_modules(player_id,category) VALUES(?,?) ON CONFLICT DO NOTHING"));
  String translated=d.sql("INSERT INTO blinksmp_network_v1.rtp_points(server_id,dimension,x) VALUES(?,?,?) ON CONFLICT(server_id,dimension) DO UPDATE SET x=excluded.x,heartbeat=now()");
  assertTrue(translated.contains("ON DUPLICATE KEY UPDATE x=VALUES(x),heartbeat=CURRENT_TIMESTAMP(6)"));
 }
 @Test void heartbeatIntervalsAndDangerousAffectedRowOverride(){
  var d=DatabaseDialect.from("jdbc:mysql://localhost/test");
  assertEquals("CURRENT_TIMESTAMP(6)-INTERVAL 45 SECOND",d.sql("now()-interval '45 seconds'"));
  assertEquals("CURRENT_TIMESTAMP(6)-INTERVAL 2 MINUTE",d.sql("now()-interval '2 minutes'"));
  assertThrows(IllegalArgumentException.class,()->DatabaseDialect.from("jdbc:mysql://localhost/test?useAffectedRows=false"));
  assertThrows(IllegalArgumentException.class,()->DatabaseDialect.from("jdbc:sqlite:test"));
 }
}
