package shop.blinksmp.queue;

import com.velocitypowered.api.proxy.Player;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Only world IDs and decisions cross this bridge; never inventory payloads. */
final class NetworkBridge {
    private final Object network;private final Method destination,leave,recovery;
    NetworkBridge(Object network)throws ReflectiveOperationException{
        this.network=network;destination=network.getClass().getMethod("queueDestination",UUID.class);
        leave=network.getClass().getMethod("queueLeave",UUID.class);
        recovery=network.getClass().getMethod("queueRecovery",Player.class,String.class);
    }
    @SuppressWarnings("unchecked") CompletableFuture<String> destination(UUID player){
        try{return (CompletableFuture<String>)destination.invoke(network,player);}catch(Exception ex){return CompletableFuture.failedFuture(new IllegalStateException("Network route unavailable"));}
    }
    @SuppressWarnings("unchecked") CompletableFuture<Boolean> leave(UUID player){
        try{return (CompletableFuture<Boolean>)leave.invoke(network,player);}catch(Exception ex){return CompletableFuture.completedFuture(false);}
    }
    boolean recover(Player player,String origin){try{return Boolean.TRUE.equals(recovery.invoke(network,player,origin));}catch(Exception ex){return false;}}
}
