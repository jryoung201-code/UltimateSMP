package shop.ultimatesmp.crates;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** The key/reward transaction runs once, only after all spin frames have elapsed. */
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
        final BooleanSupplier deliver;
        final int frames;
        BukkitTask task;
        int frame;
        boolean notified,settled,closed;
        Session(Player player, Screen screen, Crate crate, ItemStack prize, int frames, BooleanSupplier deliver, Runnable notify) {
            this.player=player;this.screen=screen;this.crate=crate;this.prize=prize.clone();this.frames=frames;this.deliver=deliver;this.notify=notify;
        }
        void notifyOnce() { if(!notified){notified=true;if(player.isOnline())notify.run();} }
    }
    private final JavaPlugin plugin;
    private final Predicate<Player> ready;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final int frames;
    private final Consumer<Player> tickSound;
    CrateReveal(JavaPlugin plugin,Predicate<Player> ready,int seconds) {
        this(plugin,ready,seconds,p->p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.25f,1.1f));
    }
    CrateReveal(JavaPlugin plugin,Predicate<Player> ready,int seconds,Consumer<Player> tickSound) {
        this.plugin=plugin;this.ready=ready;this.tickSound=tickSound;frames=Math.max(0,Math.min(10,seconds))*10;
    }
    boolean active(UUID id) { return sessions.containsKey(id); }
    void start(Player player,Crate crate,ItemStack prize,BooleanSupplier deliver,Runnable notify) {
        cancel(player.getUniqueId(),false);
        Screen screen=new Screen();
        screen.inventory=Bukkit.createInventory(screen,27,"Opening: "+crate.name());
        Session session=new Session(player,screen,crate,prize,frames,deliver,notify);
        sessions.put(player.getUniqueId(),session);
        paintBorder(screen.inventory);
        screen.inventory.setItem(22,icon(Material.CLOCK,"§eOpening your crate…","Your key is used after the spin finishes.","Closing keeps the spin running in the background."));
        screen.inventory.setItem(26,icon(Material.BARRIER,"§cClose","The spin continues; closing does not award early."));
        session.task=Bukkit.getScheduler().runTaskTimer(plugin,()->tick(session),1,2);
    }
    private void tick(Session session) {
        Player player=session.player;
        if(sessions.get(player.getUniqueId())!=session)return;
        if(!player.isOnline()||!ready.test(player)) { cancel(player.getUniqueId(),true);closeScreen(session);return; }
        if(session.settled)return;
        if(session.frame==0)player.openInventory(session.screen.inventory);
        if(session.frame++>=session.frames) {
            session.settled=true;
            if(session.task!=null)session.task.cancel();
            boolean delivered=false;
            try{delivered=session.deliver.getAsBoolean();}catch(Exception ex){plugin.getLogger().warning("Crate delivery failed ("+ex.getClass().getSimpleName()+"); this spin will not be retried.");}
            for(int slot=9;slot<=17;slot++)session.screen.inventory.setItem(slot,icon(Material.BLACK_STAINED_GLASS_PANE," "));
            if(delivered){
                session.screen.inventory.setItem(13,displayPrize(session.prize));
                session.screen.inventory.setItem(22,icon(Material.EMERALD,"§aYou won!","Your prize is now in your inventory.","Click Close to return to the game."));
                session.notifyOnce();
            }else{
                session.screen.inventory.setItem(13,icon(Material.BARRIER,"§cOpening cancelled","No reward was delivered.","Check your key and inventory space."));
                session.screen.inventory.setItem(22,icon(Material.BARRIER,"§cNo reward claimed","Your key was not consumed."));
            }
            session.screen.inventory.setItem(26,icon(Material.BARRIER,"§cClose","Return to the game."));
            if(session.closed)sessions.remove(player.getUniqueId(),session);
            return;
        }
        for(int slot=9;slot<17;slot++)session.screen.inventory.setItem(slot,session.screen.inventory.getItem(slot+1));
        int index=LootPolicy.choose(session.crate.rewards(),ThreadLocalRandom.current().nextDouble());
        session.screen.inventory.setItem(17,session.crate.rewards().get(index).item().clone());
        if(session.frame%3==0)tickSound.accept(player);
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
    private void cancel(UUID id,boolean message) {
        Session session=sessions.remove(id);if(session==null)return;
        if(session.task!=null)session.task.cancel();
        if(message&&!session.settled&&session.player.isOnline())session.player.sendMessage("§5[UltimateCrates] §fOpening cancelled. Your key was not consumed.");
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
        if(session!=null&&session.screen.inventory==event.getInventory()){session.closed=true;if(session.settled)cancel(event.getPlayer().getUniqueId(),false);}
    }
    @EventHandler public void quit(PlayerQuitEvent event) { cancel(event.getPlayer().getUniqueId(),false); }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) { cancel(event.getPlayer().getUniqueId(),true); }
    void shutdown() {
        for(Session session:new ArrayList<>(sessions.values())) { cancel(session.player.getUniqueId(),false);closeScreen(session); }
    }
}

