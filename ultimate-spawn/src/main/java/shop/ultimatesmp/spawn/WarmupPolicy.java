package shop.ultimatesmp.spawn;
import org.bukkit.Location;
final class WarmupPolicy {
 static boolean moved(Location origin,Location current){return current.getWorld()!=origin.getWorld()||current.distanceSquared(origin)>.01;}
}

