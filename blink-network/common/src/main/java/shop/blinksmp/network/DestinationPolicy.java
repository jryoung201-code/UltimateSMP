package shop.blinksmp.network;

/** Distinguish an unassigned region from a backend that is actually unavailable. */
public final class DestinationPolicy {
    private DestinationPolicy(){}
    public static String issue(boolean ready,boolean fresh,boolean sourceRestarting,String owner,boolean ownerOnline){
        if(!ready||!fresh)return "Network routing is still refreshing. Please try again shortly.";
        if(sourceRestarting)return "This server is restarting. Please wait for the queue transfer.";
        if(owner==null)return "That destination is outside assigned server regions.";
        if(!ownerOnline)return "This server is under maintenance. Please try again later. Destination: "+owner;
        return null;
    }
}
