package shop.blinksmp.queue;

import org.junit.jupiter.api.Test;
import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class NetworkBridgeTest {
    public static class Good {
        UUID seen;
        public CompletableFuture<String> queueDestination(UUID player){seen=player;return CompletableFuture.completedFuture("BlinkSMP-Overworld-02");}
        public CompletableFuture<Boolean> queueLeave(UUID player){return CompletableFuture.completedFuture(true);}
        public boolean queueRecovery(Player player,String origin){return origin.equals("BlinkSMP-Overworld-01");}
    }
    public static class Broken {
        public CompletableFuture<String> queueDestination(UUID player){throw new IllegalStateException();}
        public CompletableFuture<Boolean> queueLeave(UUID player){throw new IllegalStateException();}
        public boolean queueRecovery(Player player,String origin){throw new IllegalStateException();}
    }
    @Test void oldBridgeContractIsRejectedBeforeLoginHook(){assertThrows(ReflectiveOperationException.class,()->new NetworkBridge(new Object()));}
    @Test void identityAndWorldDecisionArePreserved()throws Exception{
        Good n=new Good();NetworkBridge b=new NetworkBridge(n);UUID p=UUID.randomUUID();assertEquals("BlinkSMP-Overworld-02",b.destination(p).get());assertEquals(p,n.seen);
        assertTrue(b.leave(p).get());assertTrue(b.recover(null,"BlinkSMP-Overworld-01"));assertFalse(b.recover(null,"foreign"));
    }
    @Test void unavailableRoutesAndFailedLeaveFailClosed()throws Exception{
        NetworkBridge b=new NetworkBridge(new Broken());assertThrows(CompletionException.class,()->b.destination(UUID.randomUUID()).join());assertFalse(b.leave(UUID.randomUUID()).get());assertFalse(b.recover(null,"BlinkSMP-Overworld-01"));
    }
}
