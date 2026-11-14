package shop.blinksmp.queue;

import org.junit.jupiter.api.Test;
import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class NetworkBridgeTest {
    public static class Good {
        UUID seen;
        public CompletableFuture<Boolean> queueRequired(UUID player){seen=player;return CompletableFuture.completedFuture(true);}
        public boolean queueRecovery(Player player,String origin){return origin.equals("BlinkSMP-Overworld-01");}
    }
    public static class Broken {
        public CompletableFuture<Boolean> queueRequired(UUID player){throw new IllegalStateException("Route unavailable");}
        public boolean queueRecovery(Player player,String origin){throw new IllegalStateException();}
    }
    @Test void missingNetworkContractIsRejectedBeforeLoginHooksInstall(){
        assertThrows(ReflectiveOperationException.class,()->new NetworkBridge(new Object()));
    }
    @Test void adapterPreservesIdentityAndNetworkRecoveryDecision()throws Exception{
        Good network=new Good();NetworkBridge bridge=new NetworkBridge(network);UUID player=UUID.randomUUID();
        assertTrue(bridge.required(player).get());assertEquals(player,network.seen);
        assertTrue(bridge.recover(null,"BlinkSMP-Overworld-01"));assertFalse(bridge.recover(null,"foreign-server"));
    }
    @Test void routeFailureAndRejectedRecoveryFailClosed()throws Exception{
        NetworkBridge bridge=new NetworkBridge(new Broken());
        assertThrows(CompletionException.class,()->bridge.required(UUID.randomUUID()).join());assertFalse(bridge.recover(null,"BlinkSMP-Overworld-01"));
    }
}
