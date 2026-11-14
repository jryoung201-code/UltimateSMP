package shop.blinksmp.queue;

import com.velocitypowered.api.proxy.Player;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Optional public BlinkNetwork bridge. Never reads player payloads or bypasses routing. */
final class NetworkBridge {
    private final Object network;
    private final Method required,recovery;
    NetworkBridge(Object network)throws ReflectiveOperationException {
        this.network=network;
        required=network.getClass().getMethod("queueRequired",UUID.class);
        recovery=network.getClass().getMethod("queueRecovery",Player.class,String.class);
    }
    @SuppressWarnings("unchecked") CompletableFuture<Boolean> required(UUID player){
        try{return (CompletableFuture<Boolean>)required.invoke(network,player);}catch(Exception ex){return CompletableFuture.failedFuture(new IllegalStateException("Network route unavailable"));}
    }
    boolean recover(Player player,String origin){try{return Boolean.TRUE.equals(recovery.invoke(network,player,origin));}catch(Exception ex){return false;}}
}
