package shop.blinksmp.network.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.*;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import shop.blinksmp.network.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class BlinkNetworkProxy {
    private final ProxyServer proxy;private final Logger log;private final Path directory;
    private final ExecutorService io=Executors.newFixedThreadPool(2,r->new Thread(r,"BlinkNetwork-ProxyDB"));
    private static final MinecraftChannelIdentifier CHANNEL=MinecraftChannelIdentifier.from(Protocol.CHANNEL);
    private final TransferThrottle transferThrottle=new TransferThrottle();
    private NetworkStore db;private String bootstrap;private volatile boolean active,ready;
    @Inject public BlinkNetworkProxy(ProxyServer proxy,Logger log,@DataDirectory Path directory){this.proxy=proxy;this.log=log;this.directory=directory;}
    @Subscribe public void start(ProxyInitializeEvent event){
        try {
            Files.createDirectories(directory);Path path=directory.resolve("network.properties");
            if(!Files.exists(path))try(InputStream in=getClass().getResourceAsStream("/network.properties")){Files.copy(Objects.requireNonNull(in),path);}
            Properties p=new Properties();try(InputStream in=Files.newInputStream(path)){p.load(in);}
            active=Boolean.parseBoolean(p.getProperty("enabled","false"));if(!active){log.info("BlinkNetwork is disabled; configure network.properties then restart.");return;}
            bootstrap=p.getProperty("bootstrap-server");Region.dimensionOf(bootstrap);
            db=new NetworkStore(env("BLINK_NETWORK_JDBC_URL",p,"jdbc-url"),env("BLINK_NETWORK_DB_USER",p,"username"),env("BLINK_NETWORK_DB_PASSWORD",p,"password"));
            proxy.getChannelRegistrar().register(CHANNEL);
            io.execute(()->{try{db.verify();if(proxy.getServer(bootstrap).isEmpty())throw new IllegalStateException();ready=true;log.info("BlinkNetwork proxy ready.");}catch(Exception ex){log.error("Network startup failed ({}); connections blocked. Check schema and configured server IDs.",ex.getClass().getSimpleName());}});
        }catch(Exception ex){active=true;log.error("BlinkNetwork setup failed ({}); connections blocked.",ex.getClass().getSimpleName());}
    }
    private static String env(String name,Properties p,String key){return System.getenv().getOrDefault(name,p.getProperty(key,""));}
    @Subscribe(order=PostOrder.LAST) public EventTask preconnect(ServerPreConnectEvent event){
        if(!active||event.getResult().getServer().isEmpty())return null;
        return EventTask.async(()->{
            if(!ready){event.setResult(ServerPreConnectEvent.ServerResult.denied());event.getPlayer().disconnect(Component.text("BlinkSMP network is not ready."));return;}
            try {
                Player player=event.getPlayer();NetworkStore.Route route=db.route(player.getUniqueId());
                Optional<ServerConnection> from=player.getCurrentServer();String target;
                if(from.isEmpty())target=route==null?bootstrap:db.loginTarget(player.getUniqueId(),route);
                else {
                    target=event.getResult().getServer().map(s->s.getServerInfo().getName()).orElse("");
                    if(route==null||!route.permits(from.get().getServerInfo().getName(),target)){event.setResult(ServerPreConnectEvent.ServerResult.denied());return;}
                }
                var server=proxy.getServer(target);
                if(server.isEmpty()||!db.ready(target)){event.setResult(ServerPreConnectEvent.ServerResult.denied());if(from.isEmpty())player.disconnect(Component.text("Your world server is unavailable. Please try later."));return;}
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(server.get()));
            }catch(Exception ex){event.setResult(ServerPreConnectEvent.ServerResult.denied());log.warn("Network route lookup failed ({}).",ex.getClass().getSimpleName());if(event.getPlayer().getCurrentServer().isEmpty())event.getPlayer().disconnect(Component.text("Network data is unavailable. Please try later."));}
        });
    }
    @Subscribe public void message(PluginMessageEvent event){
        if(!event.getIdentifier().equals(CHANNEL))return;
        // Consume first, even malformed/client messages. Clients never participate in this protocol.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if(!ready||!(event.getSource() instanceof ServerConnection source)||!(event.getTarget() instanceof Player player))return;
        try {
            Protocol request=Protocol.read(event.getData());
            if(!request.player().equals(player.getUniqueId())||!source.getPlayer().getUniqueId().equals(player.getUniqueId()))return;
            String from=source.getServerInfo().getName();
            io.execute(()->{
                try {
                    if(!player.isActive()||player.getCurrentServer().filter(c->c==source).isEmpty())return;
                    NetworkStore.Route route=db.route(player.getUniqueId());
                    if(route==null||!route.permits(from,request.target())||!request.ticket().equals(route.ticket())||!db.ready(request.target()))return;
                    if(!transferThrottle.admit(player.getUniqueId(),request.ticket(),System.currentTimeMillis())){
                        player.sendMessage(Component.text("Repeated border transfer paused; your source server will return you safely."));return;
                    }
                    proxy.getServer(request.target()).ifPresent(server->player.createConnectionRequest(server).connect().exceptionally(ex->{log.warn("Backend transfer failed; source will recover its pending handoff.");return null;}));
                }catch(Exception ex){log.warn("Transfer verification failed ({}). Source will cancel safely.",ex.getClass().getSimpleName());}
            });
        }catch(IOException ignored){}
    }
    @Subscribe(order=PostOrder.LAST) public void kicked(KickedFromServerEvent event){
        if(active)preventKickRedirect(event);
    }
    static void preventKickRedirect(KickedFromServerEvent event){
        try{Region.dimensionOf(event.getServer().getServerInfo().getName());}catch(IllegalArgumentException ignored){return;}
        // Never hide a moderation disconnect, or bounce a rejected login to a different world copy.
        if(event.getResult() instanceof KickedFromServerEvent.DisconnectPlayer)return;
        Component reason=event.getServerKickReason().orElse(Component.text("BlinkSMP destination unavailable. Contact staff if reconnecting fails."));
        boolean hasSource=event.kickedDuringServerConnect()&&event.getPlayer().getCurrentServer()
            .filter(c->!c.getServerInfo().getName().equals(event.getServer().getServerInfo().getName())).isPresent();
        event.setResult(hasSource?KickedFromServerEvent.Notify.create(reason):KickedFromServerEvent.DisconnectPlayer.create(reason));
    }
    @Subscribe public void stop(ProxyShutdownEvent event){ready=false;io.shutdown();try{io.awaitTermination(15,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}if(db!=null)db.close();}
}

