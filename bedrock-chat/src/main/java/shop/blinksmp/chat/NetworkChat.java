package shop.blinksmp.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import shop.blinksmp.chat.bridge.ChatFrame;

/** The native Paper chat processor owns local delivery and all mute/filter events. */
public final class NetworkChat implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID,UUID> dispatches=new ConcurrentHashMap<>();
    private final LongAdder forwarded=new LongAdder();
    private final boolean enabled;
    public NetworkChat(JavaPlugin plugin){this.plugin=plugin;enabled=plugin.getConfig().getBoolean("proxy-chat.enabled",true);}
    public void start(){
        if(enabled){Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,ChatFrame.CHANNEL);Bukkit.getPluginManager().registerEvents(this,plugin);}
    }
    public void chat(Player player,String message){
        dispatches.put(player.getUniqueId(),UUID.randomUUID());
        try{player.chat(message);}finally{dispatches.remove(player.getUniqueId());}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void accepted(AsyncChatEvent event){
        UUID message=dispatches.get(event.getPlayer().getUniqueId());if(message==null)return;
        // Never promote private/local-channel chat to global. Capture membership at event time.
        Set<Player> locals=new HashSet<>(Bukkit.getOnlinePlayers());
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!plugin.isEnabled()||event.isCancelled()||!event.getPlayer().isOnline())return;
            if(!event.viewers().contains(Bukkit.getConsoleSender())||!event.viewers().containsAll(locals))return;
            try{
                var rendered=event.renderer().render(event.getPlayer(),event.getPlayer().displayName(),event.message(),Bukkit.getConsoleSender());
                var frame=new ChatFrame(message,event.getPlayer().getUniqueId(),GsonComponentSerializer.gson().serialize(rendered));
                event.getPlayer().sendPluginMessage(plugin,ChatFrame.CHANNEL,frame.encode());forwarded.increment();
            }catch(Exception ex){plugin.getLogger().warning("Network chat relay rejected a message ("+ex.getClass().getSimpleName()+"). Local chat was retained.");}
        });
    }
    public long forwarded(){return forwarded.sum();}
    public void stop(){HandlerList.unregisterAll(this);dispatches.clear();if(enabled)Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin,ChatFrame.CHANNEL);}
}
