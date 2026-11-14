package shop.blinksmp.network.velocity;

import shop.blinksmp.network.NetworkStore;
import java.util.Objects;

record QueuePermit(String target,long expiresAt,String intent){
    boolean expired(long now){return now-expiresAt>=0;}
    boolean allows(String requested,long now){return !expired(now)&&Objects.equals(target,requested);}
    static boolean routeAllows(NetworkStore.Route route,String authoritative,String requested){
        return Objects.equals(authoritative,requested)&&(route==null||route.state().equals("offline")||route.state().equals("handoff"));
    }
}

