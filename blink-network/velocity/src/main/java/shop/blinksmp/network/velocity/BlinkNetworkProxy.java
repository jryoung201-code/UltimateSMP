package shop.blinksmp.network.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.event.command.*;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.*;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import shop.blinksmp.network.*;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BlinkNetworkProxy {
    private final ProxyServer proxy;private final Logger log;private final Path directory;
    private final ExecutorService io=Executors.newFixedThreadPool(2,r->new Thread(r,"BlinkNetwork-ProxyDB"));
    private static final MinecraftChannelIdentifier CHANNEL=MinecraftChannelIdentifier.from(Protocol.CHANNEL);
    private static final String MAINTENANCE="This server is under maintenance. Please try again later.";
    private final TransferThrottle transferThrottle=new TransferThrottle();
    private final Map<UUID,Waiting> waiting=new ConcurrentHashMap<>();
    private final Map<UUID,String> queuePermits=new ConcurrentHashMap<>();
    private final Set<UUID> busy=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean polling=new AtomicBoolean();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private NetworkStore db;private String bootstrap,queueServer,startUrl;private long lastStart;private volatile boolean active,ready;
    private record Waiting(String origin,boolean rtp) {}
    @Inject public BlinkNetworkProxy(ProxyServer proxy,Logger log,@DataDirectory Path directory){this.proxy=proxy;this.log=log;this.directory=directory;}
    @Subscribe public void start(ProxyInitializeEvent event){
        try {
            Files.createDirectories(directory);Path path=directory.resolve("network.properties");
            if(!Files.exists(path))try(InputStream in=getClass().getResourceAsStream("/network.properties")){Files.copy(Objects.requireNonNull(in),path);}
            Properties p=new Properties();try(InputStream in=Files.newInputStream(path)){p.load(in);}
            active=Boolean.parseBoolean(p.getProperty("enabled","false"));if(!active){log.info("BlinkNetwork is disabled; configure network.properties then restart.");return;}
            bootstrap=p.getProperty("bootstrap-server");Region.dimensionOf(bootstrap);
            queueServer=p.getProperty("queue-server","BlinkSMP-Queue-01");
            if(!queueServer.matches("BlinkSMP-Queue-(0[1-9]|[1-9][0-9])"))throw new IllegalArgumentException("Invalid queue ID");
            startUrl=env("BLINK_QUEUE_START_URL",p,"queue-start-url");
            if(!startUrl.isBlank()&&!URI.create(startUrl).getScheme().equalsIgnoreCase("https"))throw new IllegalArgumentException("Queue startup requires HTTPS");
            db=new NetworkStore(env("BLINK_NETWORK_JDBC_URL",p,"jdbc-url"),env("BLINK_NETWORK_DB_USER",p,"username"),env("BLINK_NETWORK_DB_PASSWORD",p,"password"));
            proxy.getChannelRegistrar().register(CHANNEL);
            io.execute(()->{try{db.verify();if(proxy.getServer(bootstrap).isEmpty()||proxy.getServer(queueServer).isEmpty())throw new IllegalStateException();ready=true;log.info("BlinkNetwork proxy ready.");}catch(Exception ex){log.error("Network startup failed ({}); connections blocked. Check schema and configured IDs.",ex.getClass().getSimpleName());}});
            proxy.getScheduler().buildTask(this,()->{
                if(!ready||!polling.compareAndSet(false,true))return;
                io.execute(()->{try{
                    if(db.anyMaintenance()||!waiting.isEmpty())startQueue();
                    probeQueue();
                    for(var row:waiting.entrySet())proxy.getPlayer(row.getKey()).ifPresent(player->{
                        if(player.getCurrentServer().filter(c->c.getServerInfo().getName().equals(queueServer)).isPresent())resume(player,row.getValue());
                    });
                }catch(Exception ex){log.warn("Queue poll failed ({}).",ex.getClass().getSimpleName());}finally{polling.set(false);}});
            }).repeat(Duration.ofSeconds(3)).schedule();
        }catch(Exception ex){active=true;log.error("BlinkNetwork setup failed ({}); connections blocked.",ex.getClass().getSimpleName());}
    }
    private static String env(String name,Properties p,String key){return System.getenv().getOrDefault(name,p.getProperty(key,""));}
    private boolean inQueue(Player p){return p.getCurrentServer().filter(c->c.getServerInfo().getName().equals(queueServer)).isPresent();}
    @Subscribe(order=PostOrder.LAST) public EventTask preconnect(ServerPreConnectEvent event){
        if(!active||event.getResult().getServer().isEmpty())return null;
        return EventTask.async(()->{
            if(!ready){event.setResult(ServerPreConnectEvent.ServerResult.denied());event.getPlayer().disconnect(Component.text("BlinkSMP network is not ready."));return;}
            try {
                Player player=event.getPlayer();NetworkStore.Route route=db.route(player.getUniqueId());
                Optional<ServerConnection> from=player.getCurrentServer();String requested=event.getResult().getServer().orElseThrow().getServerInfo().getName(),target;
                if(requested.equals(queueServer)&&queuePermits.remove(player.getUniqueId())!=null){event.setResult(ServerPreConnectEvent.ServerResult.allowed(proxy.getServer(queueServer).orElseThrow()));return;}
                if(from.isEmpty()){
                    target=route==null?bootstrap:db.loginTarget(player.getUniqueId(),route);
                    if(waiting.containsKey(player.getUniqueId())||(route!=null&&route.state().equals("active"))||!db.ready(target)||db.rtpPending(player.getUniqueId())){
                        waiting.putIfAbsent(player.getUniqueId(),new Waiting(target,true));startQueue();
                        event.setResult(ServerPreConnectEvent.ServerResult.allowed(proxy.getServer(queueServer).orElseThrow()));return;
                    }
                }else if(inQueue(player)){
                    if(route==null||!route.state().equals("offline")){event.setResult(ServerPreConnectEvent.ServerResult.denied());return;}
                    target=db.loginTarget(player.getUniqueId(),route);
                    if(!target.equals(requested)){event.setResult(ServerPreConnectEvent.ServerResult.denied());return;}
                }else {
                    target=requested;
                    if(route==null||!route.permits(from.get().getServerInfo().getName(),target)){event.setResult(ServerPreConnectEvent.ServerResult.denied());player.sendMessage(Component.text(MAINTENANCE));return;}
                }
                var server=proxy.getServer(target);
                if(server.isEmpty()||!db.ready(target)){event.setResult(ServerPreConnectEvent.ServerResult.denied());player.sendMessage(Component.text(MAINTENANCE));return;}
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(server.get()));
            }catch(Exception ex){event.setResult(ServerPreConnectEvent.ServerResult.denied());log.warn("Network route lookup failed ({}).",ex.getClass().getSimpleName());if(event.getPlayer().getCurrentServer().isEmpty())event.getPlayer().disconnect(Component.text("Network data is unavailable. Please try later."));}
        });
    }
    @Subscribe public void connected(ServerConnectedEvent event){
        if(!active)return;
        if(!event.getServer().getServerInfo().getName().equals(queueServer))waiting.remove(event.getPlayer().getUniqueId());
    }
    @Subscribe public void disconnected(DisconnectEvent event){waiting.remove(event.getPlayer().getUniqueId());queuePermits.remove(event.getPlayer().getUniqueId());busy.remove(event.getPlayer().getUniqueId());}
    @Subscribe public void message(PluginMessageEvent event){
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
                    if(request.target().equals(queueServer)){
                        if(!db.queued(player.getUniqueId(),from,request.ticket()))return;
                        waiting.put(player.getUniqueId(),new Waiting(from,false));startQueue();queuePermits.put(player.getUniqueId(),from);
                        proxy.getServer(queueServer).ifPresent(server->player.createConnectionRequest(server).connect());return;
                    }
                    NetworkStore.Route route=db.route(player.getUniqueId());
                    if(route==null||!route.permits(from,request.target())||!request.ticket().equals(route.ticket())||!db.ready(request.target())){player.sendMessage(Component.text(MAINTENANCE));return;}
                    if(!transferThrottle.admit(player.getUniqueId(),request.ticket(),System.currentTimeMillis())){
                        player.sendMessage(Component.text("Repeated border transfer paused; your source server will return you safely."));return;
                    }
                    proxy.getServer(request.target()).ifPresent(server->player.createConnectionRequest(server).connect().exceptionally(ex->{log.warn("Backend transfer failed; source will recover its pending handoff.");return null;}));
                }catch(Exception ex){log.warn("Transfer verification failed ({}). Source will cancel safely.",ex.getClass().getSimpleName());}
            });
        }catch(IOException ignored){}
    }
    // Unregistered command: /leave never enters Velocity's command tree.
    @Subscribe(order=PostOrder.LAST) public void command(CommandExecuteEvent event){
        if(!active||!(event.getCommandSource() instanceof Player p)||!inQueue(p))return;
        if(event.getCommand().trim().equalsIgnoreCase("leave")){
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            Waiting w=waiting.get(p.getUniqueId());
            if(w==null){p.sendMessage(Component.text("Queue state unavailable. Please reconnect."));return;}
            Waiting rtp=new Waiting(w.origin(),true);waiting.put(p.getUniqueId(),rtp);
            io.execute(()->resume(p,rtp));
        }
    }
    private void resume(Player player,Waiting wait){
        if(!busy.add(player.getUniqueId()))return;
        boolean connecting=false;
        try {
            if(!player.isActive()||!inQueue(player))return;
            NetworkStore.Route route=db.route(player.getUniqueId());
            // Brand-new players must wait for the import/bootstrap server; there is no inventory to migrate yet.
            String target=bootstrap;
            if(route!=null){
                if(!route.state().equals("offline")){
                    if(!db.releaseDeadSession(player.getUniqueId()))return;
                    route=db.route(player.getUniqueId());
                }
                target=db.loginTarget(player.getUniqueId(),route);
                if(wait.rtp()||!db.ready(target)){
                    boolean found=false;
                    for(NetworkStore.Rtp point:db.rtpPoints(wait.origin())){
                        if(proxy.getServer(point.server()).isEmpty())continue;
                        if(db.rerouteOffline(player.getUniqueId(),point)){target=point.server();found=true;break;}
                    }
                    if(!found){player.sendActionBar(Component.text("Waiting for another server with a safe RTP landing..."));return;}
                }
            }
            if(!db.ready(target))return;
            var server=proxy.getServer(target);if(server.isEmpty())return;
            connecting=true;
            player.createConnectionRequest(server.get()).connect().whenComplete((result,error)->{
                busy.remove(player.getUniqueId());
                if(error!=null||!result.isSuccessful())player.sendMessage(Component.text(MAINTENANCE));
            });
        }catch(Exception ex){log.warn("Queue recovery paused ({}); saved state retained.",ex.getClass().getSimpleName());}
        finally{if(!connecting)busy.remove(player.getUniqueId());}
    }
    private void probeQueue(){
        proxy.getServer(queueServer).ifPresent(server->server.ping().orTimeout(3,TimeUnit.SECONDS).whenComplete((ping,error)->{
            if(!ready)return;
            io.execute(()->{try{db.queueHealth(queueServer,error==null);}catch(Exception ex){log.warn("Queue health unavailable.");}});
        }));
    }
    private void startQueue(){
        // Optional operator-owned HTTPS webhook. No panel URLs or credentials are guessed.
        if(startUrl.isBlank()||System.currentTimeMillis()-lastStart<60_000)return;
        try{if(db.queueReady(queueServer))return;}catch(java.sql.SQLException ex){return;}
        lastStart=System.currentTimeMillis();
        var builder=HttpRequest.newBuilder(URI.create(startUrl)).timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.noBody());
        String token=System.getenv("BLINK_QUEUE_START_TOKEN");if(token!=null&&!token.isBlank())builder.header("Authorization","Bearer "+token);
        http.sendAsync(builder.build(),HttpResponse.BodyHandlers.discarding()).whenComplete((r,e)->{
            if(e!=null||r.statusCode()<200||r.statusCode()>=300)log.warn("Queue start request failed; check the configured host-control webhook.");
        });
    }
    @Subscribe(order=PostOrder.LAST) public EventTask kicked(KickedFromServerEvent event){
        if(!active||!ready)return null;
        return EventTask.async(()->{
            try {
                String origin=event.getServer().getServerInfo().getName();Region.dimensionOf(origin);
                // Network failure only. A supplied moderation/login rejection is never converted into a bypass.
                if(!event.kickedDuringServerConnect()&&isNetworkFailure(event.getServerKickReason())){
                    waiting.put(event.getPlayer().getUniqueId(),new Waiting(origin,true));startQueue();queuePermits.put(event.getPlayer().getUniqueId(),origin);
                    var queue=proxy.getServer(queueServer);if(queue.isPresent()){event.setResult(KickedFromServerEvent.RedirectPlayer.create(queue.get()));return;}
                }
            }catch(Exception ignored){}
            preventKickRedirect(event);
        });
    }
    static boolean isNetworkFailure(Optional<Component> reason){
        return reason.isEmpty()||(reason.get() instanceof net.kyori.adventure.text.TranslatableComponent translated
            && translated.key().equals("velocity.error.internal-server-connection-error"));
    }
    static void preventKickRedirect(KickedFromServerEvent event){
        try{Region.dimensionOf(event.getServer().getServerInfo().getName());}catch(IllegalArgumentException ignored){return;}
        if(event.getResult() instanceof KickedFromServerEvent.DisconnectPlayer)return;
        Component reason=event.getServerKickReason().orElse(Component.text("BlinkSMP destination unavailable. Contact staff if reconnecting fails."));
        boolean hasSource=event.kickedDuringServerConnect()&&event.getPlayer().getCurrentServer()
            .filter(c->!c.getServerInfo().getName().equals(event.getServer().getServerInfo().getName())).isPresent();
        event.setResult(hasSource?KickedFromServerEvent.Notify.create(reason):KickedFromServerEvent.DisconnectPlayer.create(reason));
    }
    @Subscribe public void stop(ProxyShutdownEvent event){ready=false;io.shutdown();try{io.awaitTermination(15,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}if(db!=null)db.close();}
}
