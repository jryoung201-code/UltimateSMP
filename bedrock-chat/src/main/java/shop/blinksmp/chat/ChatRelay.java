package shop.blinksmp.chat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Only verified Floodgate sessions are relayed. Java chat and authentication are untouched. */
public final class ChatRelay implements Relay, Listener {
    private final JavaPlugin plugin;
    public ChatRelay(JavaPlugin plugin) { this.plugin=plugin; }
    private record Session(Player player,ChatPolicy.Gate gate,AtomicInteger pending) {}
    private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
    private final LongAdder intercepted=new LongAdder(),dispatched=new LongAdder(),rejected=new LongAdder();
    private final LongAdder privateDispatched=new LongAdder();
    private PacketListenerAbstract listener;
    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private long intervalNanos;

    @Override public void start() {
        intervalNanos=Math.max(250,Math.min(10000,plugin.getConfig().getInt("minimum-message-interval-ms",750)))*1_000_000L;
        try {
            Plugin floodgate=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("floodgate"));
            Class<?> api=Class.forName("org.geysermc.floodgate.api.FloodgateApi",true,floodgate.getClass().getClassLoader());
            floodgateApi=api.getMethod("getInstance").invoke(null);
            isFloodgatePlayer=api.getMethod("isFloodgatePlayer",UUID.class);
            if(floodgateApi==null) throw new IllegalStateException("Floodgate API is not ready");
        } catch(ReflectiveOperationException|RuntimeException ex) {
            plugin.getLogger().severe("Cannot verify Floodgate identities; relay disabled: "+ex);
            throw new IllegalStateException("Cannot verify Floodgate identities",ex);
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player p:Bukkit.getOnlinePlayers()) register(p);
        listener=new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override public void onPacketReceive(PacketReceiveEvent event) {
                if(!plugin.isEnabled() || event.isCancelled() || event.getUser()==null) return;
                UUID id=event.getUser().getUUID();
                if(id==null) return;
                Session session=sessions.get(id);
                if(session==null) return; // All Java players, including name/UUID lookalikes, keep the vanilla path.
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
                // Never intercept arbitrary commands, nor any command packet from a Java session.
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
                                session.player().chat(message);
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
        plugin.getLogger().info("Bedrock-only chat relay active. Java signatures and server.properties are unchanged.");
        if(!Bukkit.getOnlineMode()) plugin.getLogger().warning("online-mode=false: Java account authentication is not enforced. This plugin does not change that setting.");
    }

    private void register(Player p) {
        try {
            if(Boolean.TRUE.equals(isFloodgatePlayer.invoke(floodgateApi,p.getUniqueId())))
                sessions.put(p.getUniqueId(),new Session(p,new ChatPolicy.Gate(intervalNanos),new AtomicInteger()));
        } catch(ReflectiveOperationException ex) { plugin.getLogger().warning("Could not verify player for chat relay: "+ex); }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e) { register(e.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent e) { sessions.remove(e.getPlayer().getUniqueId()); }
    @Override public void stop() {
        HandlerList.unregisterAll(this);
        sessions.clear();
        if(listener!=null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }
    @Override public void status(CommandSender sender) {
        sender.sendMessage("UltimateBedrockChat 1.0.4 | verified Bedrock sessions: "+sessions.size());
        sender.sendMessage("Intercepted: "+intercepted.sum()+" | passed to Paper chat events: "+dispatched.sum()+" | invalid/rate-limited/stale: "+rejected.sum());
        sender.sendMessage("Private-message commands dispatched: "+privateDispatched.sum()+" | /msg, /tell, /w supported");
        sender.sendMessage("Java messages are not relayed. Bedrock output is unsigned. Mute/filter events may cancel messages after dispatch.");
    }
}

