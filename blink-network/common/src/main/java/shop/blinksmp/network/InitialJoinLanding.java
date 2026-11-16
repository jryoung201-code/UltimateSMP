package shop.blinksmp.network;

import java.util.*;

/** Bounded, deterministic chunk candidates for a local import. Does not mutate routing or player state. */
public final class InitialJoinLanding {
    public static final int MAX_CHUNKS=16;
    private InitialJoinLanding(){}
    public static List<TransferSafety.Point> candidates(Map<Region,String> owners,String server,double spawnX,double spawnZ,NetworkStore.Border border){
        Region.at("Overworld",spawnX,spawnZ); // reject nonfinite / invalid coordinates
        List<Region> local=owners.entrySet().stream()
            .filter(e->server.equals(e.getValue())&&e.getKey().dimension().equals("Overworld"))
            .map(Map.Entry::getKey)
            .sorted(Comparator.<Region>comparingDouble(r->Math.hypot(r.x()*144.0+72-spawnX,r.z()*144.0+72-spawnZ))
                .thenComparingInt(Region::x).thenComparingInt(Region::z)).toList();
        List<TransferSafety.Point> points=new ArrayList<>();Set<Long> chunks=new HashSet<>();
        add(points,chunks,owners,server,spawnX,spawnZ,border);
        for(Region r:local){
            add(points,chunks,owners,server,r.x()*144.0+72,r.z()*144.0+72,border);
            if(points.size()==MAX_CHUNKS)break;
        }
        return List.copyOf(points);
    }
    private static void add(List<TransferSafety.Point> out,Set<Long> chunks,Map<Region,String> owners,String server,double x,double z,NetworkStore.Border border){
        if(out.size()>=MAX_CHUNKS)return;
        try{
            TransferSafety.Point point=TransferSafety.landing(owners,server,"Overworld",x,z,border);
            long cx=((long)Math.floor(point.x()))>>4,cz=((long)Math.floor(point.z()))>>4;
            long key=(cx<<32)^(cz&0xffffffffL);
            if(chunks.add(key))out.add(point);
        }catch(IllegalArgumentException ignored){ /* No local ownership or no in-border landing. */ }
    }
}
