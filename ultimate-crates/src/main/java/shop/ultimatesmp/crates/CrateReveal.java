package shop.ultimatesmp.crates;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Cosmetic only: the key and actual prize are settled before this view is opened. */
final class CrateReveal implements Listener {
    static final class Screen implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class Session {
        final Player player;
        final Screen screen;
        final Crate crate;
        final ItemStack prize;
        final Runnable notify;
        final int frames;
        BukkitTask task;
        int frame;
        boolean notified;
        Session(Player player, Screen screen, Crate crate, ItemStack prize, int frames, Runnable notify) {
            this.player=player;this.screen=screen;this.crate=crate;this.prize=prize.clone();this.frames=frames;this.notify=notify;
        }
        void notifyOnce() { if(!notified){notified=true;if(player.isOnline())notify.run();} }
    }
    private final JavaPlugin plugin;
    private final Predicate<Player> ready;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final int frames;
    CrateReveal(JavaPlugin plugin,Predicate<Player> ready,int seconds) {
        this.plugin=plugin;this.ready=ready;frames=Math.max(0,Math.min(10,seconds))*10;
    }
    boolean active(UUID id) { return sessions.containsKey(id); }
    void start(Player player,Crate crate,ItemStack prize,Runnable notify) {
        finish(player.getUniqueId());
        Screen screen=new Screen();
        screen.inventory=Bukkit.createInventory(screen,27,"Opening: "+crate.name());
        Session session=new Session(player,screen,crate,prize,frames,notify);
        sessions.put(player.getUniqueId(),session);
        paintBorder(screen.inventory);
        screen.inventory.setItem(22,icon(Material.CLOCK,"§eOpening your crate…","Your reward is already secured.","Closing early will not lose your prize."));
        screen.inventory.setItem(26,icon(Material.BARRIER,"§cClose","Your reward stays in your inventory."));
        session.task=Bukkit.getScheduler().runTaskTimer(plugin,()->tick(session),1,2);
    }
    private void tick(Session session) {
        Player player=session.player;
        if(sessions.get(player.getUniqueId())!=session)return;
        if(!player.isOnline()||!ready.test(player)) { finish(player.getUniqueId());closeScreen(session);return; }
        if(session.frame==0)player.openInventory(session.screen.inventory);
        if(session.frame++>=session.frames) {
            for(int slot=9;slot<=17;slot++)session.screen.inventory.setItem(slot,icon(Material.BLACK_STAINED_GLASS_PANE," "));
            session.screen.inventory.setItem(13,displayPrize(session.prize));
            session.screen.inventory.setItem(22,icon(Material.EMERALD,"§aYou won!","Your prize is in your inventory.","Click Close to return to the game."));
            if(session.task!=null)session.task.cancel();
            session.notifyOnce();
            return;
        }
        for(int slot=9;slot<17;slot++)session.screen.inventory.setItem(slot,session.screen.inventory.getItem(slot+1));
        int index=LootPolicy.choose(session.crate.rewards(),ThreadLocalRandom.current().nextDouble());
        session.screen.inventory.setItem(17,session.crate.rewards().get(index).item().clone());
        if(session.frame%3==0)player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.25f,1.1f);
    }
    private static ItemStack displayPrize(ItemStack original) {
        ItemStack copy=original.clone();ItemMeta meta=copy.getItemMeta();
        List<String> lore=new ArrayList<>(meta.hasLore()?meta.getLore():List.of());
        lore.add("§aAlready delivered to your inventory.");lore.add("§7This is a display copy; close to continue.");
        meta.setLore(lore);copy.setItemMeta(meta);return copy;
    }
    private static ItemStack icon(Material type,String title,String... lines) {
        ItemStack item=new ItemStack(type);ItemMeta meta=item.getItemMeta();meta.setDisplayName(title);
        meta.setLore(Arrays.stream(lines).map(line->"§7"+line).toList());item.setItemMeta(meta);return item;
    }
    private static void paintBorder(Inventory inventory) {
        for(int slot=0;slot<27;slot++)inventory.setItem(slot,icon(Material.BLACK_STAINED_GLASS_PANE," "));
        inventory.setItem(4,icon(Material.YELLOW_STAINED_GLASS_PANE,"§e▼ Your reward ▼"));
    }
    private void finish(UUID id) {
        Session session=sessions.remove(id);if(session==null)return;
        if(session.task!=null)session.task.cancel();session.notifyOnce();
    }
    private static void closeScreen(Session session) {
        if(session.player.isOnline()&&session.player.getOpenInventory().getTopInventory()==session.screen.inventory)session.player.closeInventory();
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Screen))return;
        event.setCancelled(true);
        if(event.getRawSlot()==26)Bukkit.getScheduler().runTask(plugin,()->event.getWhoClicked().closeInventory());
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Screen)event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        Session session=sessions.get(event.getPlayer().getUniqueId());
        if(session!=null&&session.screen.inventory==event.getInventory())finish(event.getPlayer().getUniqueId());
    }
    @EventHandler public void quit(PlayerQuitEvent event) { finish(event.getPlayer().getUniqueId()); }
    void shutdown() {
        for(Session session:new ArrayList<>(sessions.values())) { finish(session.player.getUniqueId());closeScreen(session); }
    }
}
