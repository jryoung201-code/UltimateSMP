package shop.blinksmp.network;

import java.util.*;

/** Per-session, bounded replay protection. A previously accepted nonce is never evicted while valid. */
public final class SwitchReplayGuard {
    private final Map<UUID,Long> accepted=new HashMap<>();
    public synchronized boolean admit(UUID nonce,long expiresAt,long now){
        accepted.entrySet().removeIf(row->row.getValue()<=now);
        if(nonce==null||expiresAt<=now||expiresAt-now>90_000||accepted.containsKey(nonce)||accepted.size()>=32)return false;
        accepted.put(nonce,expiresAt);return true;
    }
}
