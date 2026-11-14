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
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** A virtual holding area only. BlinkNetwork remains the sole authority for inventory/routing. */
public final class BlinkQueue {
    private final ProxyServer proxy;
    private final Logger log;
    private final Path directory;
    private final Map<UUID,Attempt> attempts=new ConcurrentHashMap<>();
    private final AtomicBoolean probing=new AtomicBoolean(),starting=new AtomicBoolean();
    private final AtomicLong lastStart=new AtomicLong(),demandUntil=new AtomicLong();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private volatile boolean ready,queueOnline;
    private volatile long checkedAt;
    private QueueConfig config;
    private NetworkBridge network;
    private Limbo limbo;
    private LimboFactory factory;
    private ScheduledTask timer;
    private Semaphore capacity;
    private static final class Attempt {
        final Player player;
        final WaitBudget budget;
        volatile LimboPlayer virtual;
        volatile boolean checking,departing;
        final AtomicBoolean spawning=new AtomicBoolean();
        volatile long departedAt;
        Attempt(Player player,WaitBudget budget,boolean checking){this.player=player;this.budget=budget;this.checking=checking;}
    }
    @Inject public BlinkQueue(ProxyServer proxy,Logger log,@DataDirectory Path directory){this.proxy=proxy;this.log=log;this.directory=directory;}

    @Subscribe public void start(ProxyInitializeEvent event){
        try {
            Files.createDirectories(directory);Path path=directory.resolve("queue.properties");
            if(!Files.exists(path))try(InputStream in=getClass().getResourceAsStream("/queue.properties")){Files.copy(Objects.requireNonNull(in),path);}
            Properties p=new Properties();try(InputStream in=Files.newInputStream(path)){p.load(in);}
            config=QueueConfig.read(p,System.getenv("BLINK_QUEUE_START_URL"));
            if(!config.enabled()){log.info("BlinkQueue disabled by configuration.");return;}
            if(proxy.getServer(config.queue()).isEmpty())throw new IllegalStateException("Queue ID must be registered in velocity.toml");
            Optional<Object> backend=proxy.getPluginManager().getPlugin("blinknetwork").flatMap(PluginContainer::getInstance);
            if(backend.isPresent())network=new NetworkBridge(backend.get());
            else if(proxy.getServer(config.defaultServer()).isEmpty())throw new IllegalStateException("Default server must be registered in velocity.toml");
            factory=(LimboFactory)proxy.getPluginManager().getPlugin("limboapi").flatMap(PluginContainer::getInstance).orElseThrow();
            var world=factory.createVirtualWorld(Dimension.OVERWORLD,0.5,65,0.5,0,0);
            var block=factory.createSimpleBlock("minecraft:glass");
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.setBlock(x,64,z,block);
            world.fillSkyLight(15);
            limbo=factory.createLimbo(world).setName("BlinkSMP Queue Starting")
                .setGameMode(GameMode.ADVENTURE).setWorldTime(6000).setViewDistance(2).setSimulationDistance(2)
                .setReadTimeout(30_000).setShouldRejoin(true).setShouldRespawn(true);
            capacity=new Semaphore(config.capacity());ready=true;
            timer=proxy.getScheduler().buildTask(this,this::tick).repeat(Duration.ofSeconds(config.pollSeconds())).schedule();
            log.info("BlinkQueue ready; virtual wait limit {} seconds; {} integration.",config.waitSeconds(),network==null?"standalone":"BlinkNetwork");
            if(config.startUri()==null)log.warn("Queue-start endpoint is empty. Players can wait in limbo, but the host must start the real queue manually.");
        }catch(Exception|LinkageError ex){ready=false;log.error("BlinkQueue setup failed ({}). Check Java 25, LimboAPI 1.2.0, registered queue ID and BlinkNetwork 0.3.1+; secrets are not logged.",ex.getClass().getSimpleName());}
    }

    @Subscribe(order=PostOrder.LAST) public void login(LoginLimboRegisterEvent event){
        if(!ready)return;
        // Preserve an earlier auth/reconnect callback; this plugin never handles moderation kicks.
        var previous=event.getOnKickCallback();
        event.setOnKickCallback(kick->{
            if(previous!=null&&Boolean.TRUE.equals(previous.apply(kick)))return true;
            if(!ready||!FailurePolicy.network(kick.getServerKickReason()))return false;
            String origin=kick.getServer().getServerInfo().getName();
            if(kick.kickedDuringServerConnect()&&!origin.equals(config.queue()))return false;
            if(network!=null&&!network.recover(kick.getPlayer(),origin))return false;
            return holdForQueue(kick.getPlayer());
        });
        // LimboAPI runs these callbacks in its login sequence, with authentication plugins intact.
        event.addOnJoinCallback(()->enter(event.getPlayer(),true));
    }

    /** Public BlinkNetwork API, called only after its own route/ticket verification. */
    public boolean holdForQueue(Player player){
        if(!ready||!player.isActive())return false;
        Attempt a=enter(player,false);
        if(a!=null){wakeQueue();return true;}return false;
    }
    /** Public warm-up API used when a backend schedules maintenance, even with no waiting players. */
    public boolean wakeQueue(){
        if(!ready)return false;
        demandUntil.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(config.waitSeconds()));probe();return true;
    }
    private Attempt enter(Player player,boolean checkRoute){
        if(!ready||!player.isActive())return null;
        Attempt existing=attempts.get(player.getUniqueId());
        if(existing!=null){
            if(existing.budget.expired(System.nanoTime())){fail(existing,"Queue startup timed out. Please try joining again later.");return null;}
            if((existing.virtual!=null||existing.spawning.get())&&!existing.departing)return existing;
            existing.departing=false;existing.checking=checkRoute;spawn(existing);return existing;
        }
        if(!capacity.tryAcquire()){player.disconnect(Component.text("The starting queue is full. Please try again shortly."));return null;}
        Attempt a=new Attempt(player,new WaitBudget(System.nanoTime(),TimeUnit.SECONDS.toNanos(config.waitSeconds())),checkRoute);
        Attempt raced=attempts.putIfAbsent(player.getUniqueId(),a);
        if(raced!=null){capacity.release();return raced;}
        spawn(a);return a;
    }
    private void spawn(Attempt a){
        if(!a.spawning.compareAndSet(false,true))return;
        try {limbo.spawnPlayer(a.player,new LimboSessionHandler(){
            private LimboPlayer own;
            @Override public void onSpawn(Limbo server,LimboPlayer player){
                if(!ready||attempts.get(a.player.getUniqueId())!=a){a.player.disconnect(Component.text("Queue session expired. Please reconnect."));return;}
                a.spawning.set(false);own=player;a.virtual=player;player.disableFalling();
                if(a.checking)checkRoute(a);else {show(a);wakeQueue();}
            }
            @Override public void onMove(double x,double y,double z){
                if(a.virtual==own&&own!=null&&(y<64||y>72||Math.abs(x)>4||Math.abs(z)>4))own.teleport(0.5,65,0.5,0,0);
            }
            @Override public void onDisconnect(){if(a.virtual==own)a.virtual=null;}
        });}catch(Exception|LinkageError ex){fail(a,"The waiting room could not load. Please reconnect later.");log.warn("Limbo spawn failed ({}).",ex.getClass().getSimpleName());}
    }
    private void checkRoute(Attempt a){
        CompletableFuture<Boolean> needed=network!=null?network.required(a.player.getUniqueId()):
            proxy.getServer(config.defaultServer()).orElseThrow().ping().orTimeout(config.pingSeconds(),TimeUnit.SECONDS).handle((ping,error)->error!=null);
        needed.orTimeout(12,TimeUnit.SECONDS).whenComplete((required,error)->{
            if(!ready||attempts.get(a.player.getUniqueId())!=a||!a.player.isActive())return;
            if(error!=null){fail(a,"Your network route could not be verified. Please try again later.");return;}
            a.checking=false;
            if(!Boolean.TRUE.equals(required)){
                a.departing=true;a.departedAt=System.nanoTime();remove(a);
                LimboPlayer virtual=a.virtual;if(virtual!=null)virtual.disconnect();
            }else {show(a);wakeQueue();}
        });
    }
    private void show(Attempt a){
        a.player.sendActionBar(Component.text("Starting queue server... "+a.budget.secondsLeft(System.nanoTime())+"s remaining"));
        a.player.showTitle(Title.title(Component.text("Starting queue server..."),Component.text("You are safely waiting on the proxy."),Title.Times.times(Duration.ZERO,Duration.ofSeconds(config.pollSeconds()+1),Duration.ZERO)));
    }
    private void tick(){
        if(!ready)return;
        long now=System.nanoTime();
        for(Attempt a:attempts.values()){
            if(!a.player.isActive()){remove(a);continue;}
            if(a.budget.expired(now)){fail(a,"Queue startup timed out. Please try joining again later.");continue;}
            if(a.departing){
                if(now-a.departedAt>TimeUnit.SECONDS.toNanos(20))fail(a,"The backend connection did not finish. Please reconnect later.");
                continue;
            }
            if(a.checking)continue;
            show(a);
            if(queueOnline&&now-checkedAt<TimeUnit.SECONDS.toNanos(config.pollSeconds()+config.pingSeconds()+1)&&a.virtual!=null){
                a.departing=true;a.departedAt=now;
                a.player.clearTitle();a.player.sendMessage(Component.text("Queue server ready. Connecting..."));
                a.virtual.disconnect(proxy.getServer(config.queue()).orElseThrow());
            }
        }
        long demand=demandUntil.get();
        if(!attempts.isEmpty()||(demand!=0&&now-demand<0))probe();
    }
    private void probe(){
        if(!ready||!probing.compareAndSet(false,true))return;
        proxy.getServer(config.queue()).orElseThrow().ping().orTimeout(config.pingSeconds(),TimeUnit.SECONDS).whenComplete((ping,error)->{
            checkedAt=System.nanoTime();queueOnline=error==null;probing.set(false);
            if(error!=null)requestStart();
        });
    }
    private void requestStart(){
        if(!ready||config.startUri()==null||!starting.compareAndSet(false,true))return;
        long now=System.nanoTime(),previous=lastStart.get();
        if(previous!=0&&now-previous<TimeUnit.SECONDS.toNanos(config.startRetry())){starting.set(false);return;}
        lastStart.set(now);
        try {
            var request=HttpRequest.newBuilder(config.startUri()).timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.noBody());
            String token=System.getenv("BLINK_QUEUE_START_TOKEN");if(token!=null&&!token.isBlank())request.header("Authorization","Bearer "+token);
            http.sendAsync(request.build(),HttpResponse.BodyHandlers.discarding()).whenComplete((response,error)->{
                starting.set(false);
                if(error!=null||response.statusCode()<200||response.statusCode()>=300)log.warn("Queue start request failed; verify the host-start endpoint. Waiting players remain in limbo.");
            });
        }catch(Exception ex){starting.set(false);log.warn("Queue start request could not be sent; endpoint details are not logged.");}
    }
    @Subscribe public void connected(ServerConnectedEvent event){Attempt a=attempts.get(event.getPlayer().getUniqueId());if(a!=null)remove(a);}
    @Subscribe public void disconnected(DisconnectEvent event){Attempt a=attempts.get(event.getPlayer().getUniqueId());if(a!=null)remove(a);}
    private void remove(Attempt a){if(attempts.remove(a.player.getUniqueId(),a))capacity.release();}
    private void fail(Attempt a,String message){remove(a);a.player.disconnect(Component.text(message));}
    @Subscribe public void stop(ProxyShutdownEvent event){
        ready=false;if(timer!=null)timer.cancel();
        for(Attempt a:attempts.values())fail(a,"The proxy is restarting. Please reconnect shortly.");
        if(limbo!=null)limbo.dispose();
    }
}
