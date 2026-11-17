package shop.ultimatesmp.spawn;
import java.util.function.Predicate;import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.block.*;import org.bukkit.event.entity.*;import org.bukkit.event.hanging.*;import org.bukkit.event.inventory.*;import org.bukkit.event.player.*;import org.bukkit.event.world.PortalCreateEvent;
/** Protects only the dedicated spawn world. Editing requires an explicit permission, not OP bypass. */
public final class SpawnProtection implements Listener {
 private final Predicate<World> protectedWorld;public SpawnProtection(Predicate<World> protectedWorld){this.protectedWorld=protectedWorld;}
 private boolean locked(Player p,World w){return protectedWorld.test(w)&&!p.hasPermission("edit.spawn");}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void place(BlockPlaceEvent e){if(locked(e.getPlayer(),e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breakBlock(BlockBreakEvent e){if(locked(e.getPlayer(),e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void bucketEmpty(PlayerBucketEmptyEvent e){if(locked(e.getPlayer(),e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void bucketFill(PlayerBucketFillEvent e){if(locked(e.getPlayer(),e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interact(PlayerInteractEvent e){if(locked(e.getPlayer(),e.getPlayer().getWorld())&&e.getClickedBlock()!=null){e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);e.setCancelled(true);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void entityClick(PlayerInteractEntityEvent e){if(!(e.getRightClicked() instanceof Player)&&locked(e.getPlayer(),e.getRightClicked().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void entityAt(PlayerInteractAtEntityEvent e){if(locked(e.getPlayer(),e.getRightClicked().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void open(InventoryOpenEvent e){Location l=e.getInventory().getLocation();if(l!=null&&e.getPlayer() instanceof Player p&&e.getInventory().getHolder()!=p&&e.getInventory().getType()!=InventoryType.ENDER_CHEST&&e.getInventory().getType()!=InventoryType.CRAFTING&&locked(p,l.getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void drop(PlayerDropItemEvent e){if(locked(e.getPlayer(),e.getPlayer().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p&&locked(p,p.getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hurt(EntityDamageEvent e){if(!protectedWorld.test(e.getEntity().getWorld()))return;if(e.getEntity() instanceof Player){e.setCancelled(true);return;}if(!(e instanceof EntityDamageByEntityEvent hit)||!(hit.getDamager() instanceof Player p)||!p.hasPermission("edit.spawn"))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hanging(HangingBreakEvent e){if(protectedWorld.test(e.getEntity().getWorld())&&(!(e instanceof HangingBreakByEntityEvent hit)||!(hit.getRemover() instanceof Player p)||!p.hasPermission("edit.spawn")))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void explosion(EntityExplodeEvent e){if(protectedWorld.test(e.getEntity().getWorld())){e.blockList().clear();e.setCancelled(true);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void blockExplosion(BlockExplodeEvent e){if(protectedWorld.test(e.getBlock().getWorld())){e.blockList().clear();e.setCancelled(true);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void mobChange(EntityChangeBlockEvent e){if(protectedWorld.test(e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void flow(BlockFromToEvent e){if(protectedWorld.test(e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void ignite(BlockIgniteEvent e){if(protectedWorld.test(e.getBlock().getWorld())&&(e.getPlayer()==null||!e.getPlayer().hasPermission("edit.spawn")))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void burn(BlockBurnEvent e){if(protectedWorld.test(e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void spread(BlockSpreadEvent e){if(protectedWorld.test(e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void piston(BlockPistonExtendEvent e){if(protectedWorld.test(e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void retract(BlockPistonRetractEvent e){if(protectedWorld.test(e.getBlock().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void portal(PortalCreateEvent e){if(protectedWorld.test(e.getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void portalUse(PlayerPortalEvent e){if(protectedWorld.test(e.getFrom().getWorld()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void commands(PlayerCommandPreprocessEvent e){
  String label=e.getMessage().substring(1).split("\\s+",2)[0].toLowerCase(java.util.Locale.ROOT);label=label.substring(label.lastIndexOf(':')+1);
  if(protectedWorld.test(e.getPlayer().getWorld())&&label.equals("sethome")){e.setCancelled(true);e.getPlayer().sendMessage("Use /spawn to return to this world.");return;}
  if(locked(e.getPlayer(),e.getPlayer().getWorld())&&(java.util.Set.of("fill","setblock","clone","place","execute","worldborder","gamerule","setworldspawn","weather","time").contains(label)||label.startsWith("/")||label.equals("worldedit")||label.equals("we"))){e.setCancelled(true);e.getPlayer().sendMessage("You need edit.spawn to edit the spawn world.");}
 }
}

