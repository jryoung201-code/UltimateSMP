package shop.blinksmp.lodestones;

import java.util.Set;

/** Stable logical dimension and block position, independent of backend/world UUIDs. */
public record Target(String dimension,int x,int y,int z) {
    public Target {
        if(!Set.of("Overworld","Nether","End").contains(dimension)||Math.abs((long)x)>29999984||Math.abs((long)z)>29999984||y< -2048||y>2047)throw new IllegalArgumentException("Invalid target");
    }
    public String encode(){return "1|"+dimension+"|"+x+"|"+y+"|"+z;}
    public static Target decode(String value){
        if(value==null||value.length()>100)throw new IllegalArgumentException("Invalid target data");
        String[] fields=value.split("\\|",-1);
        if(fields.length!=5||!fields[0].equals("1"))throw new IllegalArgumentException("Invalid target version");
        return new Target(fields[1],Integer.parseInt(fields[2]),Integer.parseInt(fields[3]),Integer.parseInt(fields[4]));
    }
}
