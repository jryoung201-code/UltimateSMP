package shop.blinksmp.queue;

import com.google.inject.Inject;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.elytrium.limboapi.api.*;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.elytrium.limboapi.api.player.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** The whole queue is virtual. There is no physical queue backend and no host API. */
public final class BlinkQueue {
    private final ProxyServer proxy;private final Logger log;private final Path directory;
    private final Map<UUID,Attempt> attempts=new ConcurrentHashMap<>();
    private volatile boolean ready;
    private QueueConfig config;private NetworkBridge network;private Limbo limbo;
    private ScheduledTask timer;private Semaphore capacity;
    private static final class Attempt {
        final Player player;final WaitBudget budget;
        final AtomicBoolean spawning=new AtomicBoolean(),routing=new AtomicBoolean(),leaving=new AtomicBoolean();
        final AtomicLong revision=new AtomicLong();
        volatile LimboPlayer virtual;volatile boolean departing;volatile long departedAt;volatile String target;
        Attempt(Player player,WaitBudget budget){this.player=player;this.budget=budget;}
    }
    @Inject public BlinkQueue(ProxyServer proxy,Logger log,@DataDirectory Path directory){this.proxy=proxy;this.log=log;this.directory=directory;}
    @Subscribe public void start(ProxyInitializeEvent event){
        try{
            Files.createDirectories(directory);Path path=directory.resolve("queue.properties");
            if(!Files.exists(path))try(InputStream in=getClass().getResourceAsStream("/queue.properties")){Files.copy(Objects.requireNonNull(in),path);}
            Properties p=new Properties();try(InputStream in=Files.newInputStream(path)){p.load(in);}
            config=QueueConfig.read(p);if(!config.enabled()){log.info("BlinkQueue disabled.");return;}
            network=new NetworkBridge(proxy.getPluginManager().getPlugin("blinknetwork").flatMap(PluginContainer::getInstance).orElseThrow());
            LimboFactory factory=(LimboFactory)proxy.getPluginManager().getPlugin("limboapi").flatMap(PluginContainer::getInstance).orElseThrow();
            var world=factory.createVirtualWorld(Dimension.OVERWORLD,0.5,65,0.5,0,0);
            var block=factory.createSimpleBlock("minecraft:glass");for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.setBlock(x,64,z,block);
            world.fillSkyLight(15);
            limbo=factory.createLimbo(world).setName("BlinkSMP Virtual Queue").setGameMode(GameMode.ADVENTURE)
                .setWorldTime(6000).setViewDistance(2).setSimulationDistance(2).setReadTimeout(30_000).setShouldRejoin(true).setShouldRespawn(true);
            capacity=new Semaphore(config.capacity());ready=true;
            timer=proxy.getScheduler().buildTask(this,this::tick).repeat(Duration.ofSeconds(config.pollSeconds())).schedule();
            log.info("BlinkQueue virtual queue ready; no queue backend/API required; wait limit {} seconds.",config.waitSeconds());
        }catch(Exception|LinkageError ex){ready=false;log.error("BlinkQueue setup failed ({}). Requires Java 25, LimboAPI 1.2.0 and BlinkNetwork 0.4.0+.",ex.getClass().getSimpleName());}
    }
    @Subscribe(order=PostOrder.LAST) public void login(LoginLimboRegisterEvent event){
        if(!ready)return;
        var previous=event.getOnKickCallback();
        event.setOnKickCallback(kick->{
            if(previous!=null&&Boolean.TRUE.equals(previous.apply(kick)))return true;
            if(!ready||!FailurePolicy.network(kick.getServerKickReason()))return false;
            Attempt a=attempts.get(kick.getPlayer().getUniqueId());String origin=kick.getServer().getServerInfo().getName();
            if(kick.kickedDuringServerConnect()&&(a==null||!a.departing||!origin.equals(a.target)))return false;
            if(!network.recover(kick.getPlayer(),origin))return false;
            return holdForQueue(kick.getPlayer());
        });
        event.addOnJoinCallback(()->enter(event.getPlayer()));
    }
    public boolean isReady(){return ready;}
    /** Remains true during admission so BlinkNetwork can validate the exact reserved world. */
    public boolean isHeld(Player player){return ready&&attempts.containsKey(player.getUniqueId());}
    public boolean holdForQueue(Player player){return ready&&player.isActive()&&enter(player)!=null;}
    private synchronized Attempt enter(Player player){
        if(!ready||!player.isActive())return null;
        Attempt existing=attempts.get(player.getUniqueId());
        if(existing!=null){
            if(existing.budget.expired(System.nanoTime())){fail(existing,"No safe world became available in time. Please reconnect later.");return null;}
            if((existing.virtual!=null||existing.spawning.get())&&!existing.departing)return existing;
            existing.departing=false;existing.target=null;spawn(existing);return existing;
        }
        if(!capacity.tryAcquire()){player.disconnect(Component.text("The virtual queue is full. Please try again shortly."));return null;}
        Attempt a=new Attempt(player,new WaitBudget(System.nanoTime(),TimeUnit.SECONDS.toNanos(config.waitSeconds())));
        attempts.put(player.getUniqueId(),a);spawn(a);return a;
    }
    private void spawn(Attempt a){
        if(!a.spawning.compareAndSet(false,true))return;
        try{limbo.spawnPlayer(a.player,new LimboSessionHandler(){
            private LimboPlayer own;
            @Override public void onSpawn(Limbo server,LimboPlayer player){
                a.spawning.set(false);if(!ready||attempts.get(a.player.getUniqueId())!=a)return;
                own=player;a.virtual=player;player.disableFalling();
                a.player.sendMessage(Component.text("You are in the virtual queue. /leave requests safe RTP to a different world server."));
                show(a);resolve(a);
            }
            @Override public void onMove(double x,double y,double z){if(a.virtual==own&&own!=null&&(y<64||y>72||Math.abs(x)>4||Math.abs(z)>4))own.teleport(0.5,65,0.5,0,0);}
            @Override public void onChat(String message){
                // Not registered in Velocity/Brigadier: absent from TAB and autocomplete.
                if(FailurePolicy.leave(message)&&a.virtual==own&&!a.departing&&a.leaving.compareAndSet(false,true)){
                    a.revision.incrementAndGet();
                    network.leave(a.player.getUniqueId()).orTimeout(12,TimeUnit.SECONDS).whenComplete((accepted,error)->{
                        a.leaving.set(false);if(attempts.get(a.player.getUniqueId())!=a)return;
                        a.player.sendMessage(Component.text(error==null&&Boolean.TRUE.equals(accepted)?"Looking for a safe landing on another server...":"Your data must finish loading, or your first join must use the main server."));
                        resolve(a);
                    });
                }
            }
            @Override public void onDisconnect(){if(a.virtual==own)a.virtual=null;}
        });}catch(Exception|LinkageError ex){fail(a,"The virtual queue could not load. Please reconnect later.");log.warn("Virtual queue spawn failed ({}).",ex.getClass().getSimpleName());}
    }
    private void show(Attempt a){
        a.player.sendActionBar(Component.text("Waiting for a safe world... /leave for another server — "+a.budget.secondsLeft(System.nanoTime())+"s"));
        a.player.showTitle(Title.title(Component.text("BlinkSMP Queue"),Component.text("Waiting for an available world..."),Title.Times.times(Duration.ZERO,Duration.ofSeconds(config.pollSeconds()+1),Duration.ZERO)));
    }
    private void resolve(Attempt a){
        if(!ready||a.departing||a.leaving.get()||a.virtual==null||attempts.get(a.player.getUniqueId())!=a||!a.routing.compareAndSet(false,true))return;
        long revision=a.revision.get();
        network.destination(a.player.getUniqueId()).orTimeout(12,TimeUnit.SECONDS).whenComplete((target,error)->{
            a.routing.set(false);
            if(!ready||attempts.get(a.player.getUniqueId())!=a||!a.player.isActive()||a.departing||a.leaving.get()||revision!=a.revision.get())return;
            if(error!=null){log.warn("Virtual queue route unavailable; will retry safely.");return;}
            if(target==null||target.isBlank())return;
            var server=proxy.getServer(target);LimboPlayer virtual=a.virtual;
            if(server.isEmpty()||virtual==null)return;
            a.departing=true;a.departedAt=System.nanoTime();a.target=target;
            a.player.clearTitle();a.player.sendMessage(Component.text("World ready. Connecting..."));virtual.disconnect(server.get());
        });
    }
    private void tick(){
        if(!ready)return;long now=System.nanoTime();
        for(Attempt a:attempts.values()){
            if(!a.player.isActive()){remove(a);continue;}
            if(a.budget.expired(now)){fail(a,"No safe world became available in time. Please reconnect later.");continue;}
            if(a.departing){if(now-a.departedAt>TimeUnit.SECONDS.toNanos(20))fail(a,"The world connection did not finish. Please reconnect later.");continue;}
            show(a);resolve(a);
        }
    }
    @Subscribe public void connected(ServerConnectedEvent event){Attempt a=attempts.get(event.getPlayer().getUniqueId());if(a!=null)remove(a);}
    @Subscribe public void disconnected(DisconnectEvent event){Attempt a=attempts.get(event.getPlayer().getUniqueId());if(a!=null)remove(a);}
    private boolean remove(Attempt a){if(attempts.remove(a.player.getUniqueId(),a)){capacity.release();return true;}return false;}
    private void fail(Attempt a,String message){if(remove(a))a.player.disconnect(Component.text(message));}
    @Subscribe public void stop(ProxyShutdownEvent event){ready=false;if(timer!=null)timer.cancel();for(Attempt a:attempts.values())fail(a,"The proxy is restarting. Please reconnect shortly.");if(limbo!=null)limbo.dispose();}
}
