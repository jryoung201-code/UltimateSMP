package shop.blinksmp.network.velocity;

import org.junit.jupiter.api.Test;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import net.kyori.adventure.text.Component;
import java.net.InetSocketAddress;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KickLoopPolicyTest {
    private RegisteredServer server(String name){
        ServerInfo info=new ServerInfo(name,new InetSocketAddress("127.0.0.1",25565));
        return (RegisteredServer)Proxy.newProxyInstance(RegisteredServer.class.getClassLoader(),new Class<?>[]{RegisteredServer.class},(p,m,a)->m.getName().equals("getServerInfo")?info:null);
    }
    private Player player(RegisteredServer current){
        ServerConnection connection=current==null?null:(ServerConnection)Proxy.newProxyInstance(ServerConnection.class.getClassLoader(),new Class<?>[]{ServerConnection.class},(p,m,a)->m.getName().equals("getServerInfo")?current.getServerInfo():null);
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->m.getName().equals("getCurrentServer")?Optional.ofNullable(connection):null);
    }
    @Test void rejectedInitialLoginCannotRedirectToFallbackAndLoop(){
        RegisteredServer a=server("BlinkSMP-Overworld-01"),b=server("BlinkSMP-Overworld-02");
        var event=new KickedFromServerEvent(player(null),a,Component.text("Unsafe position"),true,KickedFromServerEvent.RedirectPlayer.create(b));
        BlinkNetworkProxy.preventKickRedirect(event);
        assertInstanceOf(KickedFromServerEvent.DisconnectPlayer.class,event.getResult());
    }
    @Test void failedDestinationRetainsSourceInsteadOfRedirecting(){
        RegisteredServer a=server("BlinkSMP-Overworld-01"),b=server("BlinkSMP-Overworld-02");
        var event=new KickedFromServerEvent(player(a),b,Component.text("Unavailable"),true,KickedFromServerEvent.RedirectPlayer.create(a));
        BlinkNetworkProxy.preventKickRedirect(event);
        assertInstanceOf(KickedFromServerEvent.Notify.class,event.getResult());
    }
    @Test void moderationDisconnectIsPreserved(){
        RegisteredServer a=server("BlinkSMP-Overworld-01");
        var original=KickedFromServerEvent.DisconnectPlayer.create(Component.text("Banned"));
        var event=new KickedFromServerEvent(player(null),a,Component.text("Banned"),true,original);
        BlinkNetworkProxy.preventKickRedirect(event);assertSame(original,event.getResult());
    }
    @Test void unrelatedServersAreNotChanged(){
        RegisteredServer lobby=server("lobby");
        var original=KickedFromServerEvent.RedirectPlayer.create(lobby);
        var event=new KickedFromServerEvent(player(null),lobby,Component.text("Other network"),true,original);
        BlinkNetworkProxy.preventKickRedirect(event);assertSame(original,event.getResult());
    }
    @Test void unexpectedDisconnectCanQueueButSuppliedModerationReasonsCannot(){
        assertTrue(BlinkNetworkProxy.isNetworkFailure(Optional.empty()));
        assertTrue(BlinkNetworkProxy.isNetworkFailure(Optional.of(Component.translatable("velocity.error.internal-server-connection-error"))));
        assertFalse(BlinkNetworkProxy.isNetworkFailure(Optional.of(Component.text("Banned"))));
        assertFalse(BlinkNetworkProxy.isNetworkFailure(Optional.of(Component.text("Unsafe destination"))));
    }
}

