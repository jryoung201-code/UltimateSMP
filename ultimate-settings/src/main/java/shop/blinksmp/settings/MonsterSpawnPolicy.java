package shop.blinksmp.settings;

import java.util.Collection;

/** Only veto natural monster attempts attributable exclusively to opted-out players. */
public final class MonsterSpawnPolicy {
    private MonsterSpawnPolicy() {}
    public record Candidate(double x,double y,double z,int radius,boolean excluded,boolean eligible) {}
    public static boolean cancel(boolean natural,boolean monster,double x,double y,double z,Collection<Candidate> players) {
        if(!natural || !monster) return false;
        boolean excludedInRange=false;
        double chunkX=Math.floor(x/16)*16+8,chunkZ=Math.floor(z/16)*16+8;
        for(Candidate p:players) {
            if(!p.eligible() || p.radius()<=0) continue;
            double dx=x-p.x(),dy=y-p.y(),dz=z-p.z();
            if(dx*dx+dy*dy+dz*dz>128d*128d) continue;
            double cx=chunkX-p.x(),cz=chunkZ-p.z();
            if(cx*cx+cz*cz>=(double)p.radius()*p.radius()) continue;
            if(!p.excluded()) return false;
            excludedInRange=true;
        }
        return excludedInRange;
    }
}

