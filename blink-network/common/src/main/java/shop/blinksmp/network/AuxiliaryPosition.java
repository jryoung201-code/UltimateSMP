package shop.blinksmp.network;
/** An auxiliary world's physical position; regular routing coordinates remain in the main snapshot. */
public record AuxiliaryPosition(String world,double x,double y,double z,float yaw,float pitch){
 public AuxiliaryPosition{if(world==null||!world.matches("[A-Za-z0-9_-]{1,64}")||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(x)>29_999_984||Math.abs(z)>29_999_984||Math.abs(y)>4096||Math.abs(pitch)>90)throw new IllegalArgumentException("Invalid auxiliary world position");}
}
