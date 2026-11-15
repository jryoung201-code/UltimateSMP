package shop.ultimatesmp.spawn;
public record SpawnPoint(String server,String world,double x,double y,double z,float yaw,float pitch){
 public SpawnPoint{if(server==null||!server.matches("BlinkSMP-Overworld-(0[1-9]|[1-9][0-9])")||world==null||!world.matches("[A-Za-z0-9_-]{1,64}")||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(x)>29_999_984||Math.abs(z)>29_999_984||Math.abs(y)>4096||Math.abs(pitch)>90)throw new IllegalArgumentException("Invalid spawn point");}
}
