package shop.blinksmp.network;

import java.util.Map;

/** Geometry shared by movement, restoration and offline rescue. Never changes ownership. */
public final class TransferSafety {
    public static final double INSET=2.0;
    public static final long COOLDOWN_MILLIS=3000;
    public record Point(double x,double z) {}
    private TransferSafety() {}
    public static Point landing(Map<Region,String> owners,String server,String dimension,double x,double z,NetworkStore.Border border) {
        Region r=Region.at(dimension,x,z);
        if(!dimension.equals(Region.dimensionOf(server))||!server.equals(owners.get(r)))throw new IllegalArgumentException("Destination has no matching owner");
        double minX=r.x()*144.0,maxX=minX+144,minZ=r.z()*144.0,maxZ=minZ+144;
        if(!server.equals(owners.get(new Region(dimension,r.x()-1,r.z()))))x=Math.max(x,minX+INSET);
        if(!server.equals(owners.get(new Region(dimension,r.x()+1,r.z()))))x=Math.min(x,maxX-INSET);
        if(!server.equals(owners.get(new Region(dimension,r.x(),r.z()-1))))z=Math.max(z,minZ+INSET);
        if(!server.equals(owners.get(new Region(dimension,r.x(),r.z()+1))))z=Math.min(z,maxZ-INSET);
        if(border!=null&&border.enabled()) {
            double half=border.sizeBlocks()/2.0;
            x=Math.max(border.centerX()-half+INSET,Math.min(border.centerX()+half-INSET,x));
            z=Math.max(border.centerZ()-half+INSET,Math.min(border.centerZ()+half-INSET,z));
        }
        if(!server.equals(owners.get(Region.at(dimension,x,z)))||(border!=null&&!border.contains(x,z)))throw new IllegalArgumentException("No safe inset inside destination territory");
        return new Point(x,z);
    }
    /** Coordinates must actually change: camera turns and cancelled corrections never trigger handoffs. */
    public static boolean automaticAllowed(long now,long notBefore,double fromX,double fromZ,double toX,double toZ) {
        return now>=notBefore&&(Double.compare(fromX,toX)!=0||Double.compare(fromZ,toZ)!=0);
    }
}
