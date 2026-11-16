package shop.ultimatesmp.crates;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import java.util.*;import java.util.concurrent.atomic.*;import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.event.inventory.*;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import org.bukkit.plugin.java.JavaPlugin;import org.bukkit.scheduler.*;
class CrateRevealTest {
 @Test void closingAndShutdownNeverAwardOrNotifyTwice(){
  Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.isOnline()).thenReturn(true);JavaPlugin plugin=mock(JavaPlugin.class);BukkitScheduler scheduler=mock(BukkitScheduler.class);BukkitTask task=mock(BukkitTask.class);AtomicReference<Runnable> tick=new AtomicReference<>();AtomicReference<Inventory> ui=new AtomicReference<>();AtomicInteger notices=new AtomicInteger();
  when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(1L),eq(2L))).thenAnswer(c->{tick.set(c.getArgument(1));return task;});
  ItemStack prize=mock(ItemStack.class);ItemMeta prizeMeta=mock(ItemMeta.class);when(prize.clone()).thenReturn(prize);when(prize.getType()).thenReturn(Material.DIAMOND);when(prize.getAmount()).thenReturn(1);when(prize.getMaxStackSize()).thenReturn(64);when(prize.getItemMeta()).thenReturn(prizeMeta);
  Crate crate=new Crate("common","Common",1,List.of(new Crate.Reward(prize,1)));
  try(var bukkit=mockStatic(Bukkit.class);var stacks=mockConstruction(ItemStack.class,(stack,context)->when(stack.getItemMeta()).thenReturn(mock(ItemMeta.class)))){
   bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);bukkit.when(()->Bukkit.createInventory(any(InventoryHolder.class),eq(27),anyString())).thenAnswer(c->{Inventory inv=mock(Inventory.class);Map<Integer,ItemStack> slots=new HashMap<>();when(inv.getHolder()).thenReturn(c.getArgument(0));doAnswer(call->{slots.put(call.getArgument(0),call.getArgument(1));return null;}).when(inv).setItem(anyInt(),any());when(inv.getItem(anyInt())).thenAnswer(call->slots.get(call.getArgument(0)));ui.set(inv);return inv;});
   var reveal=new CrateReveal(plugin,player->true,3,player->{});reveal.start(p,crate,prize,notices::incrementAndGet);assertTrue(reveal.active(p.getUniqueId()));for(int i=0;i<32;i++)tick.get().run();assertEquals(1,notices.get());assertSame(prize,ui.get().getItem(13));
   InventoryView view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(ui.get());when(p.getOpenInventory()).thenReturn(view);
   for(int slot:new int[]{-999,13,30}){var click=mock(InventoryClickEvent.class);when(click.getView()).thenReturn(view);when(click.getRawSlot()).thenReturn(slot);reveal.click(click);verify(click).setCancelled(true);}
   var drag=mock(InventoryDragEvent.class);when(drag.getView()).thenReturn(view);reveal.drag(drag);verify(drag).setCancelled(true);
   var close=mock(InventoryCloseEvent.class);when(close.getPlayer()).thenReturn(p);when(close.getInventory()).thenReturn(ui.get());reveal.close(close);reveal.shutdown();assertEquals(1,notices.get());assertFalse(reveal.active(p.getUniqueId()));verify(p,never()).getInventory();
  }
 }
}
