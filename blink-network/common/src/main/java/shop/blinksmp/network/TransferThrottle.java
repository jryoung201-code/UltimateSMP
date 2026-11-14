package shop.blinksmp.network;

import java.util.*;

/** Short UUID-based circuit breaker retained across reconnects; tickets cannot be replayed. */
public final class TransferThrottle {
    private record Attempt(UUID ticket,long at) {}
    private final Map<UUID,Attempt> attempts=new LinkedHashMap<>();
    public synchronized boolean admit(UUID player,UUID ticket,long now) {
        Attempt previous=attempts.get(player);
        if(previous!=null&&(ticket.equals(previous.ticket())||now-previous.at()<TransferSafety.COOLDOWN_MILLIS))return false;
        attempts.put(player,new Attempt(ticket,now));
        if(attempts.size()>8192){attempts.entrySet().removeIf(e->now-e.getValue().at()>60_000);if(attempts.size()>8192)attempts.remove(attempts.keySet().iterator().next());}
        return true;
    }
}

