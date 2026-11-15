package shop.ultimatesmp.weather;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.junit.jupiter.api.Assumptions.*;import java.util.*;
class WorldStoreTest {
 Snapshot s(String d,long t,boolean daylight,boolean storm,long age){return new Snapshot(d,t,daylight,true,storm,false,1000,2000,3000,age);}
 @Test void predictionRespectsFrozenDayAndStaleness(){assertEquals(6040,s("Overworld",6000,true,false,2000).predictedTime());assertEquals(6000,s("Overworld",6000,false,false,2000).predictedTime());assertEquals(0,s("End",0,true,false,15000).remaining(20));assertFalse(s("Nether",0,true,false,15001).fresh());assertTrue(s("Overworld",0,true,false,15000).fresh());assertThrows(IllegalArgumentException.class,()->s("Custom",0,true,false,0));assertThrows(IllegalArgumentException.class,()->s("End",-1,true,false,0));}
 @Test void mariaDbRoundTripRelayAcknowledgementAndDimensionIsolation()throws Exception {
  String url=System.getenv("TEST_WEATHER_JDBC");assumeTrue(url!=null);
  org.mariadb.jdbc.MariaDbDataSource source=new org.mariadb.jdbc.MariaDbDataSource(url);source.setUser("root");source.setPassword("disposable");WorldStore store=new WorldStore(source);store.initialize();
  var initial=List.of(s("Overworld",6000,true,false,0),s("Nether",18000,false,true,0),s("End",12000,true,false,0));
  store.exchange("two","two",true,initial,List.of(),List.of());var follower=store.exchange("one","two",false,List.of(),List.of(),List.of());assertEquals(3,follower.snapshots().size());assertEquals(18000,follower.snapshots().stream().filter(x->x.dimension().equals("Nether")).findFirst().orElseThrow().fullTime());
  assertTrue(store.exchange("one","other",false,List.of(),List.of(),List.of()).snapshots().isEmpty());
  var edits=List.of(new WorldStore.Change("time",s("Overworld",0,true,false,0)),new WorldStore.Change("weather",s("Nether",18000,false,false,0)));
  store.exchange("one","two",false,List.of(),edits,List.of());var leader=store.exchange("two","two",true,initial,List.of(),List.of());assertEquals(2,leader.requests().size());assertEquals("time",leader.requests().getFirst().kind());assertEquals("weather",leader.requests().getLast().kind());assertEquals("Overworld",leader.requests().getFirst().state().dimension());assertEquals("Nether",leader.requests().getLast().state().dimension());
  var ids=leader.requests().stream().map(WorldStore.Intent::id).toList();assertEquals(2,store.exchange("two","two",true,initial,List.of(),List.of()).requests().size());assertTrue(store.exchange("two","two",true,initial,List.of(),ids).requests().isEmpty());
  try(var c=source.getConnection();var q=c.createStatement();var r=q.executeQuery("SHOW TABLES")){var names=new ArrayList<String>();while(r.next())names.add(r.getString(1));assertEquals(Set.of(WorldStore.STATE,WorldStore.REQUESTS),Set.copyOf(names));}
 }
}
