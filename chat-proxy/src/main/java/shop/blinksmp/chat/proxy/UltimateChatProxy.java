package shop.blinksmp.chat.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.util.concurrent.atomic.LongAdder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;
import shop.blinksmp.chat.bridge.*;

@Plugin(id="ultimatechat",name="UltimateChat",version="1.1.0",authors={"Aven"})
public final class UltimateChatProxy {
    private static final MinecraftChannelIdentifier CHANNEL=MinecraftChannelIdentifier.from(ChatFrame.CHANNEL);
    private final ProxyServer proxy;private final Logger log;
    private final RelayGuard guard=new RelayGuard();private final LongAdder relayed=new LongAdder(),rejected=new LongAdder();
    @Inject public UltimateChatProxy(ProxyServer proxy,Logger log){this.proxy=proxy;this.log=log;}
    @Subscribe public void start(ProxyInitializeEvent event){proxy.getChannelRegistrar().register(CHANNEL);log.info("UltimateChat 1.1.0 ready: Java and Bedrock public chat across world servers.");}
    @Subscribe public void message(PluginMessageEvent event){
        if(!CHANNEL.equals(event.getIdentifier()))return;
        // Always consume this channel, including client-originated attempts. Never forward it.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if(!(event.getSource() instanceof ServerConnection source)||!(event.getTarget() instanceof Player carrier)) {rejected.increment();return;}
        try{
            ChatFrame frame=ChatFrame.decode(event.getData());
            String server=source.getServerInfo().getName();
            String current=carrier.getCurrentServer().map(s->s.getServerInfo().getName()).orElse(null);
            if(source.getPlayer()!=carrier||!RelayGuard.authentic(frame.playerId(),carrier.getUniqueId(),server,current)) {rejected.increment();return;}
            Component rendered=GsonComponentSerializer.gson().deserialize(frame.json());
            if(!guard.admit(frame.messageId(),frame.playerId(),System.nanoTime())){rejected.increment();return;}
            for(Player recipient:proxy.getAllPlayers()){
                // Source Paper already delivered the message with its per-viewer formatting.
                if(recipient.getCurrentServer().map(s->s.getServerInfo().getName().equals(server)).orElse(false))continue;
                recipient.sendMessage(rendered);
            }
            relayed.increment();
        }catch(Exception ex){rejected.increment();}
    }
    @Subscribe public void disconnect(DisconnectEvent event){guard.disconnect(event.getPlayer().getUniqueId());}
    @Subscribe public void stop(ProxyShutdownEvent event){proxy.getChannelRegistrar().unregister(CHANNEL);log.info("UltimateChat stopped: {} messages relayed; {} rejected frames.",relayed.sum(),rejected.sum());}
}
