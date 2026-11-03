package shop.blinksmp.chat;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.PluginManager;

/** API-level integration: real Bukkit event, simulated server/command dispatcher. */
public final class PrivateMessagesTest {
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    public static void main(String[] args) throws Exception {
        AtomicReference<Consumer<PlayerCommandPreprocessEvent>> hook=new AtomicReference<>(e->{});
        AtomicInteger events=new AtomicInteger(),dispatches=new AtomicInteger();
        AtomicReference<String> command=new AtomicReference<>();
        Player player=proxy(Player.class,(p,m,a)->switch(m.getName()) {
            case "getName" -> ".Bedrock_User";
            case "toString" -> "Bedrock test player";
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p==a[0];
            default -> null;
        });
        PluginManager manager=proxy(PluginManager.class,(p,m,a)-> {
            if(m.getName().equals("callEvent")) { events.incrementAndGet();hook.get().accept((PlayerCommandPreprocessEvent)a[0]); }
            return null;
        });
        Server server=proxy(Server.class,(p,m,a)->switch(m.getName()) {
            case "getLogger" -> Logger.getLogger("PrivateMessagesTest");
            case "getName","getVersion","getBukkitVersion" -> "test";
            case "getPluginManager" -> manager;
            case "dispatchCommand" -> {
                if(a[0]!=player) throw new AssertionError("Sender changed or elevated");
                dispatches.incrementAndGet();command.set((String)a[1]);yield true;
            }
            default -> null;
        });
        // No running Paper implementation exists here to provide ServerBuildInfo.
        var serverField=Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true); serverField.set(null,server);
        if(!PrivateMessages.dispatch(player,"msg Alice hi") || events.get()!=1 || dispatches.get()!=1 || !command.get().equals("msg Alice hi")) throw new AssertionError("Normal private message");
        hook.set(e->e.setCancelled(true));
        if(PrivateMessages.dispatch(player,"msg Alice muted") || dispatches.get()!=1) throw new AssertionError("Cancellation ignored");
        hook.set(e->e.setMessage("/op Alice"));
        if(PrivateMessages.dispatch(player,"msg Alice hi") || dispatches.get()!=1) throw new AssertionError("Arbitrary rewrite dispatched");
        hook.set(e->e.setMessage("/tell Bob filtered"));
        if(!PrivateMessages.dispatch(player,"msg Alice hi") || !command.get().equals("tell Bob filtered")) throw new AssertionError("Valid filter rewrite lost");
        int before=events.get();
        if(PrivateMessages.dispatch(player,"op Alice") || events.get()!=before || dispatches.get()!=2) throw new AssertionError("Non-message command entered relay");
        System.out.println("PASS: real preprocess cancellation, safe rewrites, original sender and dispatch isolation");
    }
}
