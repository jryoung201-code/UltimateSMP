package shop.blinksmp.network;

public record Region(String dimension,int x,int z) {
    public Region { if(!dimension.matches("Overworld|Nether|End")) throw new IllegalArgumentException("Unknown dimension"); }
    public static Region at(String dimension,double x,double z) {
        if(!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000) throw new IllegalArgumentException("Invalid coordinates");
        return new Region(dimension,(int)Math.floor(x/144.0),(int)Math.floor(z/144.0));
    }
    public static String dimensionOf(String server) {
        if(server==null || !server.matches("BlinkSMP-(Overworld|Nether|End)-(0[1-9]|[1-9][0-9])")) throw new IllegalArgumentException("Use BlinkSMP-<Overworld|Nether|End>-<01..99>");
        return server.split("-")[1];
    }
}

