package shop.blinksmp.chat.bridge;

import java.util.*;

/** Atomic duplicate/rate admission. State is bounded and uses monotonic time. */
public final class RelayGuard {
    private final Map<UUID,Long> seen=new HashMap<>(),last=new HashMap<>();
    public static boolean authentic(UUID claimed,UUID carrier,String source,String current) {
        return claimed!=null&&claimed.equals(carrier)&&source!=null&&source.equals(current);
    }
    public synchronized boolean admit(UUID message,UUID player,long now) {
        seen.entrySet().removeIf(e->now-e.getValue()>60_000_000_000L);
        last.entrySet().removeIf(e->now-e.getValue()>60_000_000_000L);
        if(seen.containsKey(message)||seen.size()>=10000||last.size()>=10000&&!last.containsKey(player))return false;
        Long prior=last.get(player);if(prior!=null&&now-prior<250_000_000L)return false;
        seen.put(message,now);last.put(player,now);return true;
    }
    public synchronized void disconnect(UUID player){last.remove(player);}
}
