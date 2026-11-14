package shop.blinksmp.queue;

import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import net.elytrium.limboapi.api.*;
import net.elytrium.limboapi.api.player.LimboPlayer;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the public virtual queue with only a world backend registered. No Minecraft socket is faked. */
class VirtualQueueFlowTest {
    public static class Routes {
        volatile CompletableFuture<String> next=CompletableFuture.completedFuture("");
        final AtomicInteger calls=new AtomicInteger(),leaves=new AtomicInteger();
        public CompletableFuture<String> queueDestination(UUID id){calls.incrementAndGet();return next;}
        public CompletableFuture<Boolean> queueLeave(UUID id){leaves.incrementAndGet();return CompletableFuture.completedFuture(true);}
        public boolean queueRecovery(Player player,String origin){return true;}
    }
    static class Harness {
        final UUID id=UUID.randomUUID();final Routes routes=new Routes();
        final AtomicInteger worldConnections=new AtomicInteger(),kicks=new AtomicInteger();
        final List<String> lookups=new ArrayList<>();final BlinkQueue plugin;LimboSessionHandler handler;
        Harness()throws Exception{
            Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
                case "getUniqueId"->id;case "isActive"->true;case "disconnect"->{kicks.incrementAndGet();yield null;}default->null;
            });
            ServerInfo info=new ServerInfo("BlinkSMP-Overworld-02",new InetSocketAddress("127.0.0.1",25565));
            RegisteredServer world=(RegisteredServer)Proxy.newProxyInstance(RegisteredServer.class.getClassLoader(),new Class<?>[]{RegisteredServer.class},(p,m,a)->m.getName().equals("getServerInfo")?info:null);
            ProxyServer proxy=(ProxyServer)Proxy.newProxyInstance(ProxyServer.class.getClassLoader(),new Class<?>[]{ProxyServer.class},(p,m,a)->{
                if(m.getName().equals("getServer")){String name=(String)a[0];lookups.add(name);return name.equals(info.getName())?Optional.of(world):Optional.empty();}
                throw new AssertionError("Unexpected proxy operation: "+m.getName());
            });
            LimboPlayer virtual=(LimboPlayer)Proxy.newProxyInstance(LimboPlayer.class.getClassLoader(),new Class<?>[]{LimboPlayer.class},(p,m,a)->{
                if(m.getName().equals("disconnect")){assertSame(world,a[0]);worldConnections.incrementAndGet();}return null;
            });
            Limbo limbo=(Limbo)Proxy.newProxyInstance(Limbo.class.getClassLoader(),new Class<?>[]{Limbo.class},(p,m,a)->{
                if(m.getName().equals("spawnPlayer")){handler=(LimboSessionHandler)a[1];handler.onSpawn((Limbo)p,virtual);}return null;
            });
            plugin=new BlinkQueue(proxy,null,Path.of("."));
            set("ready",true);set("config",QueueConfig.read(new Properties()));set("capacity",new Semaphore(2));set("network",new NetworkBridge(routes));set("limbo",limbo);
            this.player=player;
        }
        final Player player;
        void set(String name,Object value)throws Exception{Field f=BlinkQueue.class.getDeclaredField(name);f.setAccessible(true);f.set(plugin,value);}
        void tick()throws Exception{Method m=BlinkQueue.class.getDeclaredMethod("tick");m.setAccessible(true);m.invoke(plugin);}
        void remove()throws Exception{
            Field f=BlinkQueue.class.getDeclaredField("attempts");f.setAccessible(true);Object a=((Map<?,?>)f.get(plugin)).get(id);
            Method m=BlinkQueue.class.getDeclaredMethod("remove",a.getClass());m.setAccessible(true);m.invoke(plugin,a);
        }
    }
    @Test void canWaitWithNoPhysicalQueueBackendRegistered()throws Exception{
        Harness h=new Harness();assertTrue(h.plugin.holdForQueue(h.player));assertTrue(h.plugin.isHeld(h.player));assertTrue(h.lookups.isEmpty());assertEquals(0,h.kicks.get());
    }
    @Test void newlyAvailableWorldIsReachedDirectlyWithoutQueueServer()throws Exception{
        Harness h=new Harness();h.plugin.holdForQueue(h.player);h.routes.next=CompletableFuture.completedFuture("BlinkSMP-Overworld-02");h.tick();
        assertEquals(List.of("BlinkSMP-Overworld-02"),h.lookups);assertEquals(1,h.worldConnections.get());assertEquals(0,h.kicks.get());assertTrue(h.plugin.isHeld(h.player));
    }
    @Test void hiddenLeaveWorksThroughLimboCallbackWithoutRegisteringCommand()throws Exception{
        Harness h=new Harness();h.plugin.holdForQueue(h.player);h.handler.onChat("/server");assertEquals(0,h.routes.leaves.get());h.handler.onChat("/LEAVE");assertEquals(1,h.routes.leaves.get());assertTrue(h.plugin.isHeld(h.player));
    }
    @Test void disconnectBeforeRouteCompletesCannotMoveOrKickPlayer()throws Exception{
        Harness h=new Harness();CompletableFuture<String> pending=new CompletableFuture<>();h.routes.next=pending;h.plugin.holdForQueue(h.player);h.remove();pending.complete("BlinkSMP-Overworld-02");
        assertEquals(0,h.worldConnections.get());assertEquals(0,h.kicks.get());
    }
    @Test void slowRouteDoesNotScheduleOverlappingRequests()throws Exception{
        Harness h=new Harness();h.routes.next=new CompletableFuture<>();h.plugin.holdForQueue(h.player);h.tick();h.tick();assertEquals(1,h.routes.calls.get());
        h.routes.next.complete("BlinkSMP-Overworld-02");assertEquals(1,h.worldConnections.get());
    }
}
