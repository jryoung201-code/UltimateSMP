package shop.blinksmp.network.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.*;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import shop.blinksmp.network.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Inventory authority and verified world routing. The queue itself lives entirely in LimboAPI. */
public final class BlinkNetworkProxy {
    private final ProxyServer proxy;private final Logger log;private final Path directory;
    private final ExecutorService io=Executors.newFixedThreadPool(2,r->new Thread(r,"BlinkNetwork-ProxyDB"));
    private static final MinecraftChannelIdentifier CHANNEL=MinecraftChannelIdentifier.from(Protocol.CHANNEL);
    private static final String MAINTENANCE="This server is under maintenance. Please try again later.";
    private final TransferThrottle transferThrottle=new TransferThrottle();
    private final Map<UUID,Long> switchAfter=new ConcurrentHashMap<>();
    private static final MinecraftChannelIdentifier SWITCH=MinecraftChannelIdentifier.from(SwitchRequest.CHANNEL);
    private final Map<UUID,Waiting> waiting=new ConcurrentHashMap<>();
    private final Map<UUID,QueuePermit> permits=new ConcurrentHashMap<>();
    private final AtomicBoolean polling=new AtomicBoolean();
    private NetworkStore db;private String bootstrap,queueId;private volatile boolean active,ready;
    private record Waiting(String origin,boolean rtp) {}
    @Inject public BlinkNetworkProxy(ProxyServer proxy,Logger log,@DataDirectory Path directory){this.proxy=proxy;this.log=log;this.directory=directory;}
    @Subscribe public void start(ProxyInitializeEvent event){
        try {
            Files.createDirectories(directory);Path path=directory.resolve("network.properties");
            if(!Files.exists(path))try(InputStream in=getClass().getResourceAsStream("/network.properties")){Files.copy(Objects.requireNonNull(in),path);}
            Properties p=new Properties();try(InputStream in=Files.newInputStream(path)){p.load(in);}
            active=Boolean.parseBoolean(p.getProperty("enabled","false"));
            if(!active){log.info("BlinkNetwork disabled; configure network.properties then restart.");return;}
            bootstrap=p.getProperty("bootstrap-server");Region.dimensionOf(bootstrap);
            // Wire identifier and queue-status key, not a real Velocity backend.
            queueId=p.getProperty("queue-id",p.getProperty("queue-server","BlinkSMP-Queue-01"));
            if(!queueId.matches("BlinkSMP-Queue-(0[1-9]|[1-9][0-9])"))throw new IllegalArgumentException("Invalid queue ID");
            if(proxy.getServer(bootstrap).isEmpty())throw new IllegalArgumentException("Bootstrap server is not registered");
            db=new NetworkStore(env("BLINK_NETWORK_JDBC_URL",p,"jdbc-url"),env("BLINK_NETWORK_DB_USER",p,"username"),env("BLINK_NETWORK_DB_PASSWORD",p,"password"));
            proxy.getChannelRegistrar().register(CHANNEL,SWITCH);
            initialize();
            proxy.getScheduler().buildTask(this,()->{
                if(!polling.compareAndSet(false,true))return;
                io.execute(()->{try{
                    if(!ready){db.verify();ready=true;log.info("BlinkNetwork proxy ready.");}
                    db.queueHealth(queueId,queueBridge("isReady",null));
                    permits.entrySet().removeIf(row->row.getValue().expired(System.nanoTime()));
                }catch(Exception ex){log.warn("Network check failed ({}); startup will retry. Verify Neon/schema 3.",ex.getClass().getSimpleName());}finally{polling.set(false);}});
            }).repeat(Duration.ofSeconds(3)).schedule();
        }catch(Exception ex){active=true;log.error("BlinkNetwork setup failed ({}); connections blocked. Check JDBC configuration and bootstrap ID.",ex.getClass().getSimpleName());}
    }
    private void initialize(){io.execute(()->{try{db.verify();ready=true;log.info("BlinkNetwork proxy ready.");}catch(Exception ex){log.error("Network startup failed ({}); will retry every 3 seconds. Expected schema 3.",ex.getClass().getSimpleName());}});}
    private static String env(String name,Properties p,String key){return System.getenv().getOrDefault(name,p.getProperty(key,""));}
    @Subscribe(order=PostOrder.LAST) public EventTask preconnect(ServerPreConnectEvent event){
        if(!active||event.getResult().getServer().isEmpty())return null;
        return EventTask.async(()->{
            Player player=event.getPlayer();
            if(!ready){event.setResult(ServerPreConnectEvent.ServerResult.denied());player.disconnect(Component.text("BlinkSMP network is not ready."));return;}
            try {
                var from=player.getCurrentServer();NetworkStore.Route route=db.route(player.getUniqueId());
                String requested=event.getResult().getServer().orElseThrow().getServerInfo().getName(),target;
                QueuePermit permit=permits.remove(player.getUniqueId());
                if(permit!=null&&queueBridge("isHeld",player)&&permit.allows(requested,System.nanoTime())){
                    target=route==null?bootstrap:db.loginTarget(player.getUniqueId(),route);
                    if(!QueuePermit.routeAllows(route,target,requested))throw new IllegalStateException("Queue destination changed");
                }else if(from.isEmpty()){
                    if(route!=null&&route.state().equals("active"))throw new IllegalStateException("Active session requires virtual queue recovery");
                    target=route==null?bootstrap:db.loginTarget(player.getUniqueId(),route);
                }else {
                    target=requested;
                    if(route==null||!route.permits(from.get().getServerInfo().getName(),target)){
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                        String source=from.get().getServerInfo().getName();
                        if(route!=null&&route.state().equals("active")&&source.equals(route.owner())&&proxy.getServer(target).isPresent()&&db.ready(target)){
                            try{Region.dimensionOf(target);}catch(IllegalArgumentException ex){player.sendMessage(Component.text("That server is not a gameplay destination."));return;}
                            long now=System.currentTimeMillis();Long previous=switchAfter.putIfAbsent(player.getUniqueId(),now+3000);
                            if(previous!=null&&(previous>now||!switchAfter.replace(player.getUniqueId(),previous,now+3000))){player.sendMessage(Component.text("Please wait a moment before switching again."));return;}
                            UUID token=db.switchKey(player.getUniqueId(),source);
                            byte[] request=new SwitchRequest(player.getUniqueId(),UUID.randomUUID(),target,now+60_000).bytes(token);
                            if(!from.get().sendPluginMessage(SWITCH,request))player.sendMessage(Component.text("The source server could not receive the travel request."));
                            else player.sendMessage(Component.text("Preparing a saved transfer to "+target+"..."));
                            return;
                        }
                        throw new IllegalStateException("No active source session or world unavailable");
                    }
                }
                var server=proxy.getServer(target);
                if(server.isEmpty()||!db.ready(target))throw new IllegalStateException("World is unavailable");
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(server.get()));
            }catch(Exception ex){
                event.setResult(ServerPreConnectEvent.ServerResult.denied());player.sendMessage(Component.text(MAINTENANCE));
                log.warn("Network route rejected ({}); inventory remains fenced. Requested server must match its prepared session/queue admission.",ex.getClass().getSimpleName());
                if(player.getCurrentServer().isEmpty()&&!queueBridge("isHeld",player))player.disconnect(Component.text("Network data is unavailable. Please try later."));
            }
        });
    }
    @Subscribe public void connected(ServerConnectedEvent event){waiting.remove(event.getPlayer().getUniqueId());permits.remove(event.getPlayer().getUniqueId());}
    @Subscribe public void disconnected(DisconnectEvent event){waiting.remove(event.getPlayer().getUniqueId());permits.remove(event.getPlayer().getUniqueId());switchAfter.remove(event.getPlayer().getUniqueId());}
    @Subscribe public void message(PluginMessageEvent event){
        if(event.getIdentifier().equals(SWITCH)){event.setResult(PluginMessageEvent.ForwardResult.handled());return;}
        if(!event.getIdentifier().equals(CHANNEL))return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if(!ready||!(event.getSource() instanceof ServerConnection source)||!(event.getTarget() instanceof Player player))return;
        try {
            Protocol request=Protocol.read(event.getData());
            if(!request.player().equals(player.getUniqueId())||!source.getPlayer().getUniqueId().equals(player.getUniqueId()))return;
            String from=source.getServerInfo().getName();
            io.execute(()->{
                try {
                    if(!player.isActive()||player.getCurrentServer().filter(c->c==source).isEmpty())return;
                    if(request.target().equals(queueId)){
                        if(!db.queued(player.getUniqueId(),from,request.ticket()))return;
                        waiting.put(player.getUniqueId(),new Waiting(from,false));permits.remove(player.getUniqueId());
                        if(!queueBridge("holdForQueue",player))player.disconnect(Component.text("Virtual queue is unavailable. Your saved inventory is preserved."));
                        return;
                    }
                    NetworkStore.Route route=db.route(player.getUniqueId());
                    if(route==null||!route.permits(from,request.target())||!request.ticket().equals(route.ticket())||!db.ready(request.target())){player.sendMessage(Component.text(MAINTENANCE));return;}
                    if(!transferThrottle.admit(player.getUniqueId(),request.ticket(),System.currentTimeMillis())){player.sendMessage(Component.text("Repeated border transfer paused; the source will recover safely."));return;}
                    proxy.getServer(request.target()).ifPresent(server->player.createConnectionRequest(server).connect().exceptionally(ex->{log.warn("Backend transfer failed; source will recover its pending handoff.");return null;}));
                }catch(Exception ex){log.warn("Transfer verification failed ({}); source will cancel safely.",ex.getClass().getSimpleName());}
            });
        }catch(IOException ignored){}
    }
    /** Select a world and reserve one admission. An empty string means safely keep waiting. */
    public CompletableFuture<String> queueDestination(UUID player){
        return CompletableFuture.supplyAsync(()->{
            if(!ready)throw new CompletionException(new IllegalStateException("Network is not ready"));
            try {
                NetworkStore.Route route=db.route(player);
                if(route!=null&&!route.state().equals("offline")){
                    if(db.releaseDeadSession(player))route=db.route(player);
                    else if(!route.state().equals("handoff")){
                        waiting.putIfAbsent(player,new Waiting(route.last(),false));return "";
                    }
                }
                String target=route==null?bootstrap:db.loginTarget(player,route);
                String origin=route==null?bootstrap:route.last();
                Waiting wait=waiting.computeIfAbsent(player,key->new Waiting(origin,false));
                String intent=wait.origin()+":"+wait.rtp();
                QueuePermit existing=permits.get(player);
                if(existing!=null&&Objects.equals(existing.intent(),intent)&&existing.allows(target,System.nanoTime())&&db.ready(target))return target;
                if(route!=null&&route.state().equals("handoff")){
                    if(!db.ready(target))return ""; // Source/target still own the exact pending ticket.
                }else if(route!=null&&((wait!=null&&wait.rtp())||!db.ready(target)||db.rtpPending(player))){
                    String exclude=wait==null?target:wait.origin();boolean selected=false;
                    for(NetworkStore.Rtp point:db.rtpPoints(exclude)){
                        if(proxy.getServer(point.server()).isPresent()&&db.rerouteOffline(player,point)){target=point.server();selected=true;break;}
                    }
                    if(!selected){waiting.putIfAbsent(player,new Waiting(exclude,false));return "";}
                }
                if(proxy.getServer(target).isEmpty()||!db.ready(target)){
                    waiting.putIfAbsent(player,new Waiting(target,false));return "";
                }
                if(waiting.get(player)!=wait)return "";
                permits.put(player,new QueuePermit(target,System.nanoTime()+TimeUnit.SECONDS.toNanos(20),intent));
                return target;
            }catch(Exception ex){throw new CompletionException(new IllegalStateException("Network route unavailable"));}
        },io);
    }
    /** Called by the virtual room's unregistered /leave handler. New players need bootstrap first. */
    public CompletableFuture<Boolean> queueLeave(UUID player){
        return CompletableFuture.supplyAsync(()->{
            if(!ready)return false;
            try{
                NetworkStore.Route route=db.route(player);if(route==null)return false;
                Waiting old=waiting.get(player);
                waiting.put(player,new Waiting(old==null?route.last():old.origin(),true));permits.remove(player);return true;
            }catch(Exception ex){return false;}
        },io);
    }
    /** Called only for a trusted Velocity network-failure callback, not moderation kicks. */
    public boolean queueRecovery(Player player,String origin){
        if(!ready||!player.isActive()||proxy.getServer(origin).isEmpty())return false;
        try{Region.dimensionOf(origin);}catch(IllegalArgumentException ex){return false;}
        waiting.put(player.getUniqueId(),new Waiting(origin,true));permits.remove(player.getUniqueId());return true;
    }
    private boolean queueBridge(String method,Player player){
        Optional<Object> bridge=proxy.getPluginManager().getPlugin("blinkqueue").flatMap(PluginContainer::getInstance);
        if(bridge.isEmpty())return false;
        try{return Boolean.TRUE.equals(player==null?bridge.get().getClass().getMethod(method).invoke(bridge.get()):bridge.get().getClass().getMethod(method,Player.class).invoke(bridge.get(),player));}
        catch(ReflectiveOperationException ex){return false;}
    }
    @Subscribe(order=PostOrder.LAST) public void kicked(KickedFromServerEvent event){if(active)preventKickRedirect(event);}
    static boolean isNetworkFailure(Optional<Component> reason){return reason.isEmpty()||(reason.get() instanceof net.kyori.adventure.text.TranslatableComponent t&&t.key().equals("velocity.error.internal-server-connection-error"));}
    static void preventKickRedirect(KickedFromServerEvent event){
        try{Region.dimensionOf(event.getServer().getServerInfo().getName());}catch(IllegalArgumentException ignored){return;}
        if(event.getResult() instanceof KickedFromServerEvent.DisconnectPlayer)return;
        Component reason=event.getServerKickReason().orElse(Component.text("BlinkSMP destination unavailable."));
        boolean hasSource=event.kickedDuringServerConnect()&&event.getPlayer().getCurrentServer().filter(c->!c.getServerInfo().getName().equals(event.getServer().getServerInfo().getName())).isPresent();
        event.setResult(hasSource?KickedFromServerEvent.Notify.create(reason):KickedFromServerEvent.DisconnectPlayer.create(reason));
    }
    @Subscribe public void stop(ProxyShutdownEvent event){ready=false;io.shutdown();try{io.awaitTermination(15,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}if(db!=null)db.close();}
}
