package shop.blinksmp.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;
import shop.blinksmp.chat.bridge.ChatFrame;

/** API-level checks: cancellation after MONITOR, restricted audiences, rank renderer and no duplicate chat dispatch. */
public final class NetworkChatTest {
    public static void main(String[] args)throws Exception{
        JavaPlugin plugin=mock(JavaPlugin.class);FileConfiguration config=mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);when(config.getBoolean("proxy-chat.enabled",true)).thenReturn(true);
        when(plugin.isEnabled()).thenReturn(true);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);
        when(player.displayName()).thenReturn(Component.text("Alice"));
        ConsoleCommandSender console=mock(ConsoleCommandSender.class);BukkitScheduler scheduler=mock(BukkitScheduler.class);
        List<Runnable> pending=new ArrayList<>();AtomicReference<AsyncChatEvent> fired=new AtomicReference<>();
        doAnswer(call->{pending.add(call.getArgument(1));return null;}).when(scheduler).runTask(eq(plugin),any(Runnable.class));
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)){
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            NetworkChat network=new NetworkChat(plugin);
            doAnswer(call->{
                Set<Audience> viewers=new HashSet<>(List.of(player,console));
                AsyncChatEvent event=new AsyncChatEvent(false,player,viewers,
                    (source,name,message,viewer)->Component.text("[OWNER] ").append(name).append(Component.text(": ")).append(message),
                    Component.text(call.getArgument(0,String.class)),Component.text("original"),mock(SignedMessage.class));
                fired.set(event);network.accepted(event);return null;
            }).when(player).chat(anyString());
            network.chat(player,"hello");pending.remove(0).run();
            var bytes=org.mockito.ArgumentCaptor.forClass(byte[].class);
            verify(player,times(1)).sendPluginMessage(eq(plugin),eq(ChatFrame.CHANNEL),bytes.capture());
            ChatFrame result=ChatFrame.decode(bytes.getValue());
            if(!GsonComponentSerializer.gson().deserialize(result.json()).equals(Component.text("[OWNER] ").append(Component.text("Alice")).append(Component.text(": ")).append(Component.text("hello"))))throw new AssertionError("Rank rendering lost");
            network.chat(player,"muted");fired.get().setCancelled(true);pending.remove(0).run();
            network.chat(player,"private channel");fired.get().viewers().remove(console);pending.remove(0).run();
            verify(player,times(1)).sendPluginMessage(eq(plugin),eq(ChatFrame.CHANNEL),any(byte[].class));
            verify(player,times(3)).chat(anyString());
            network.chat(player,"disconnect race");when(player.isOnline()).thenReturn(false);pending.remove(0).run();
            verify(player,times(1)).sendPluginMessage(eq(plugin),eq(ChatFrame.CHANNEL),any(byte[].class));
            if(network.forwarded()!=1)throw new AssertionError("Unmoderated frame forwarded");
        }
        System.out.println("PASS: native event dispatch, rank rendering, late mute cancellation, restricted audience, disconnect race, exactly one frame");
    }
}
