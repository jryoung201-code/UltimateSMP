package shop.blinksmp.chat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Java and Bedrock chat share the unsigned Paper event pipeline. */
public final class ChatRelay implements Relay, Listener {
    private final JavaPlugin plugin;
    public ChatRelay(JavaPlugin plugin) { this.plugin=plugin; }
    private record Session(Player player,ChatPolicy.Gate gate,AtomicInteger pending) {}
    private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
    private final LongAdder intercepted=new LongAdder(),dispatched=new LongAdder(),rejected=new LongAdder();
    private final LongAdder privateDispatched=new LongAdder();
    private PacketListenerAbstract listener;
    private NetworkChat network;
    private long intervalNanos;

    @Override public void start() {
        intervalNanos=Math.max(250,Math.min(10000,plugin.getConfig().getInt("minimum-message-interval-ms",750)))*1_000_000L;
        network=new NetworkChat(plugin);network.start();
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player p:Bukkit.getOnlinePlayers()) register(p);
        listener=new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override public void onPacketReceive(PacketReceiveEvent event) {
                if(!plugin.isEnabled() || event.isCancelled() || event.getUser()==null) return;
                UUID id=event.getUser().getUUID();
                if(id==null) return;
                Session session=sessions.get(id);
                if(session==null) return;
                boolean command;
                String raw;
                try {
                    if(event.getPacketType()==PacketType.Play.Client.CHAT_MESSAGE) {
                        raw=new WrapperPlayClientChatMessage(event).getMessage(); command=false;
                    } else if(event.getPacketType()==PacketType.Play.Client.CHAT_COMMAND) {
                        raw=new WrapperPlayClientChatCommand(event).getCommand(); command=true;
                    } else if(event.getPacketType()==PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
                        raw=new WrapperPlayClientChatCommandUnsigned(event).getCommand(); command=true;
                    } else return;
                } catch(RuntimeException ex) { rejected.increment(); return; }
                // Only public chat and the existing private-message allowlist enter this relay.
                if(command && !PrivateMessagePolicy.handles(raw)) return;
                event.setCancelled(true);
                intercepted.increment();
                String message=command?PrivateMessagePolicy.validate(raw):ChatPolicy.validate(raw);
                if(message==null || !session.gate().allow(System.nanoTime())) { rejected.increment(); return; }
                if(session.pending().incrementAndGet()>2) { session.pending().decrementAndGet(); rejected.increment(); return; }
                try {
                    Bukkit.getScheduler().runTask(plugin,()-> {
                        try {
                            if(!plugin.isEnabled() || sessions.get(id)!=session || !session.player().isOnline()) { rejected.increment(); return; }
                            if(command) {
                                if(PrivateMessages.dispatch(session.player(),message)) privateDispatched.increment();
                                else rejected.increment();
                            } else {
                                // Normal chat events/formatters run. Public chat cannot execute commands.
                                network.chat(session.player(),message);
                                dispatched.increment();
                            }
                        } finally { session.pending().decrementAndGet(); }
                    });
                } catch(org.bukkit.plugin.IllegalPluginAccessException ex) {
                    session.pending().decrementAndGet(); rejected.increment();
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(listener);
        plugin.getLogger().info("Java and Bedrock chat relay active; public messages pass Paper filters and mutes before proxy forwarding.");
        plugin.getLogger().info("Chat signing settings are not changed automatically. Keep Velocity account authentication and modern forwarding configured.");
    }

    private void register(Player p) {
        sessions.put(p.getUniqueId(),new Session(p,new ChatPolicy.Gate(intervalNanos),new AtomicInteger()));
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e) { register(e.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent e) { sessions.remove(e.getPlayer().getUniqueId()); }
    @Override public void stop() {
        HandlerList.unregisterAll(this);
        if(network!=null)network.stop();
        sessions.clear();
        if(listener!=null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }
    @Override public void status(CommandSender sender) {
        sender.sendMessage("UltimateBedrockChat 1.1.0 | Java and Bedrock sessions: "+sessions.size());
        sender.sendMessage("Intercepted: "+intercepted.sum()+" | passed to Paper chat events: "+dispatched.sum()+" | invalid/rate-limited/stale: "+rejected.sum());
        sender.sendMessage("Private-message commands dispatched: "+privateDispatched.sum()+" | /msg, /tell, /w supported");
        sender.sendMessage("Unsigned chat for both editions. Public frames sent to Velocity: "+network.forwarded()+". Install UltimateChat-Velocity on the proxy for cross-server delivery.");
    }
}


