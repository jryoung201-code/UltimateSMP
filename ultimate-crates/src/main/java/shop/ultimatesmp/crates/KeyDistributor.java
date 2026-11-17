package shop.ultimatesmp.crates;
import java.util.*;import java.util.function.Predicate;import org.bukkit.entity.Player;import org.bukkit.inventory.ItemStack;
final class KeyDistributor {
 record Result(int given,int full,int notReady){}
 static Result give(Collection<? extends Player> players,ItemStack key,Predicate<Player> ready){int given=0,full=0,notReady=0;for(Player player:players){if(!ready.test(player)){notReady++;continue;}ItemStack[] plan=InventoryGrant.add(player.getInventory().getStorageContents(),key);if(plan==null){full++;continue;}player.getInventory().setStorageContents(plan);given++;}return new Result(given,full,notReady);}
}

