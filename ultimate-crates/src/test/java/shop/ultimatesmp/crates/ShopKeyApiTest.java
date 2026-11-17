package shop.ultimatesmp.crates;
import org.junit.jupiter.api.Test;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ShopKeyApiTest {
 @Test void unreadyUnknownAndOffThreadRequestsNeverGrant()throws Exception {
  var plugin=mock(UltimateCrates.class,CALLS_REAL_METHODS);var field=UltimateCrates.class.getDeclaredField("crates");field.setAccessible(true);field.set(plugin,new TreeMap<>());Player player=mock(Player.class);when(player.isOnline()).thenReturn(true);
  try(var b=mockStatic(Bukkit.class)){b.when(Bukkit::isPrimaryThread).thenReturn(true);assertTrue(plugin.getShopCrateIds().isEmpty());assertFalse(plugin.canReceiveShopKeys(player,"missing",1));assertFalse(plugin.giveShopKeys(player,"missing",1));verify(player,never()).getInventory();
   b.when(Bukkit::isPrimaryThread).thenReturn(false);assertThrows(IllegalStateException.class,plugin::getShopCrateIds);assertThrows(IllegalStateException.class,()->plugin.giveShopKeys(player,"common",1));
  }
 }
}
