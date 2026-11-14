package shop.blinksmp.network.paper;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Dedicated holding server: never claims or writes the gameplay database. */
final class QueueLobby implements Listener {
    private final JavaPlugin plugin;
    QueueLobby(JavaPlugin plugin){this.plugin=plugin;}
    @EventHandler public void join(PlayerJoinEvent e){
        Player p=e.getPlayer();p.getInventory().clear();p.getEnderChest().clear();
        p.setGameMode(GameMode.ADVENTURE);p.setAllowFlight(true);p.setFlying(true);
        p.setInvulnerable(true);p.setFoodLevel(20);p.setFireTicks(0);
        p.teleport(p.getWorld().getSpawnLocation());
        p.sendMessage("Your world server is restarting. Use /leave to RTP to another available server.");
    }
    @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent e){
        if(e instanceof PlayerTeleportEvent)return;
        if(e.getTo()!=null&&(e.getTo().getY()<e.getPlayer().getWorld().getMinHeight()+8||e.getTo().distanceSquared(e.getPlayer().getWorld().getSpawnLocation())>1024))e.setTo(e.getPlayer().getWorld().getSpawnLocation());
    }
    @EventHandler(ignoreCancelled=true) public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player)e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void food(FoodLevelChangeEvent e){e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void click(InventoryClickEvent e){e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void drag(InventoryDragEvent e){e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void drop(PlayerDropItemEvent e){e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player)e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void block(BlockBreakEvent e){e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void place(BlockPlaceEvent e){e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void interact(PlayerInteractEvent e){e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void entity(PlayerInteractEntityEvent e){e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){if(e.getCause()!=PlayerTeleportEvent.TeleportCause.PLUGIN)e.setCancelled(true);}
    @EventHandler public void commands(PlayerCommandSendEvent e){e.getCommands().clear();}
    @EventHandler public void command(PlayerCommandPreprocessEvent e){e.setCancelled(true);}
}
