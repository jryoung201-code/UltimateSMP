package shop.blinksmp.network.velocity;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import shop.blinksmp.network.*;import com.velocitypowered.api.proxy.*;import com.velocitypowered.api.proxy.server.*;import org.slf4j.Logger;
import java.util.*;import java.util.concurrent.*;import java.lang.reflect.Field;import java.net.*;import java.nio.file.Path;
class RestartQueueTest {
 static final String A="BlinkSMP-Overworld-01",B="BlinkSMP-Overworld-02";
 ProxyServer proxy;NetworkStore db;BlinkNetworkProxy plugin;UUID id;RegisteredServer a,b;
 @BeforeEach void setup()throws Exception{
  id=UUID.randomUUID();proxy=mock(ProxyServer.class);db=mock(NetworkStore.class);plugin=new BlinkNetworkProxy(proxy,mock(Logger.class),Path.of("."));set("db",db);set("ready",true);set("active",true);set("bootstrap",B);
  a=mock(RegisteredServer.class);b=mock(RegisteredServer.class);when(proxy.getServer(A)).thenReturn(Optional.of(a));when(proxy.getServer(B)).thenReturn(Optional.of(b));
  var route=new NetworkStore.Route("offline",A,null,null,null);when(db.route(id)).thenReturn(route);when(db.loginTarget(id,route)).thenReturn(A);
  when(db.ready(A)).thenReturn(false);when(db.ready(B)).thenReturn(true);when(db.rtpPoints(anyString())).thenReturn(List.of(new NetworkStore.Rtp(B,"Overworld",10,70,20)));when(db.rerouteOffline(eq(id),any())).thenReturn(true);
 }
 void set(String name,Object value)throws Exception{Field f=BlinkNetworkProxy.class.getDeclaredField(name);f.setAccessible(true);f.set(plugin,value);}
 Player inWorld(){Player p=mock(Player.class);ServerConnection source=mock(ServerConnection.class);when(p.getUniqueId()).thenReturn(id);when(p.isActive()).thenReturn(true);when(p.getCurrentServer()).thenReturn(Optional.of(source));when(source.getServerInfo()).thenReturn(new ServerInfo(A,new InetSocketAddress("127.0.0.1",25565)));return p;}
 @AfterEach void stop(){plugin.stop(null);}
 @Test void restartingWorldWaitsWithoutRtpThenReturnsToSavedOwner()throws Exception{
  assertTrue(plugin.queueRecovery(inWorld(),A));assertEquals("restart",plugin.queueMode(id));assertTrue(plugin.queueCanLeave(id));
  for(int i=0;i<3;i++)assertEquals("",plugin.queueDestination(id).get(5,TimeUnit.SECONDS));verify(db,never()).rtpPoints(anyString());verify(db,never()).rerouteOffline(any(),any());
  when(db.ready(A)).thenReturn(true);assertEquals(A,plugin.queueDestination(id).get());verify(db,never()).rerouteOffline(any(),any());
 }
 @Test void freshJoinCanRtpButNeverGetsLeave()throws Exception{
  assertTrue(plugin.queueBeginJoin(id));assertEquals("join",plugin.queueMode(id));assertFalse(plugin.queueCanLeave(id));assertFalse(plugin.queueLeave(id).get());
  assertEquals(B,plugin.queueDestination(id).get());verify(db).rtpPoints("");verify(db).rerouteOffline(eq(id),any());
 }
 @Test void explicitLeaveFromRestartExcludesOriginalServer()throws Exception{
  plugin.queueRecovery(inWorld(),A);assertTrue(plugin.queueLeave(id).get());assertEquals("join",plugin.queueMode(id));assertFalse(plugin.queueCanLeave(id));assertFalse(plugin.queueLeave(id).get());
  assertEquals(B,plugin.queueDestination(id).get());verify(db).rtpPoints(A);verify(db).rerouteOffline(eq(id),any());
 }
 @Test void joinConnectionFailureIsNotReclassifiedAsRestart(){plugin.queueBeginJoin(id);assertTrue(plugin.queueRecovery(inWorld(),A));assertEquals("join",plugin.queueMode(id));assertFalse(plugin.queueCanLeave(id));}
 @Test void activeSessionRemainsFencedBeforeEitherQueueCanReroute()throws Exception{when(db.route(id)).thenReturn(new NetworkStore.Route("active",A,A,null,null));when(db.releaseDeadSession(id)).thenReturn(false);plugin.queueBeginJoin(id);assertEquals("",plugin.queueDestination(id).get());verify(db,never()).rerouteOffline(any(),any());}
}
