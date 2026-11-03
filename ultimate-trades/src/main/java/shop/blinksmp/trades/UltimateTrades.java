package shop.blinksmp.trades;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class UltimateTrades extends JavaPlugin implements Listener,TabCompleter {
    private static final int[][] SLOTS={{0,1,2,3,9,10,11,12,18,19,20,21},{5,6,7,8,14,15,16,17,23,24,25,26}};
    private record Request(UUID from,UUID to,long expires) {}
    private static final class Session implements InventoryHolder {
        final Player[] players;
        final List<LinkedHashMap<Integer,ItemStack>> offers=List.of(new LinkedHashMap<>(),new LinkedHashMap<>());
        final TradeState state=new TradeState();
        final long expires;
        Inventory gui;
        Session(Player a,Player b,long expires) { players=new Player[]{a,b};this.expires=expires; }
        int side(UUID id) { return players[0].getUniqueId().equals(id)?0:players[1].getUniqueId().equals(id)?1:-1; }
        @Override public Inventory getInventory() { return gui; }
    }
    private final Map<UUID,Session> active=new HashMap<>();
    private final Map<UUID,Request> requests=new HashMap<>(); // one outgoing request per sender
    private final Map<UUID,Long> cooldowns=new HashMap<>();
    private final Set<UUID> disabled=new HashSet<>();
    private BukkitTask heartbeat;
    private int requestSeconds,tradeSeconds,confirmSeconds,cooldownSeconds;
    private double distanceSquared;
    @Override public void onEnable() {
        saveDefaultConfig();
        requestSeconds=limit("request-timeout-seconds",30,5,120);tradeSeconds=limit("trade-timeout-seconds",180,30,600);
        confirmSeconds=limit("confirmation-seconds",3,3,10);cooldownSeconds=limit("request-cooldown-seconds",5,1,60);
        double distance=getConfig().getDouble("max-distance",16);
        if(!Double.isFinite(distance) || distance<1 || distance>128) distance=16;
        distanceSquared=distance*distance;
        Bukkit.getPluginManager().registerEvents(this,this);Objects.requireNonNull(getCommand("trade")).setTabCompleter(this);
        heartbeat=Bukkit.getScheduler().runTaskTimer(this,this::pulse,20,20);
        getLogger().info("UltimateTrades active. Item stacks stay in player inventories until both players confirm.");
    }
    private int limit(String key,int fallback,int min,int max) { return Math.max(min,Math.min(max,getConfig().getInt(key,fallback))); }
    private void say(CommandSender p,String text) { p.sendMessage(Component.text("[Trade] ",NamedTextColor.GOLD).append(Component.text(text,NamedTextColor.YELLOW))); }
    private boolean near(Player a,Player b) {
        return a.isOnline() && b.isOnline() && !a.isDead() && !b.isDead() && a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation())<=distanceSquared && a.canSee(b) && b.canSee(a);
    }
    private boolean usable(Player p) {
        return p.hasPermission("ultimatetrades.use") && p.getGameMode()!=GameMode.CREATIVE && p.getGameMode()!=GameMode.SPECTATOR && !p.isDead();
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)) { say(sender,"Use trading in-game.");return true; }
        if(!p.hasPermission("ultimatetrades.use")) { say(p,"You do not have permission.");return true; }
        UUID id=p.getUniqueId();
        if(args.length==0 || args[0].equalsIgnoreCase("help")) { help(p);return true; }
        String action=args[0].toLowerCase(Locale.ROOT);
        if(action.equals("cancel")) {
            if(active.containsKey(id)) cancel(active.get(id),"Trade cancelled.");
            else { Request r=requests.remove(id);say(p,r==null?"You have no outgoing request.":"Request cancelled."); }
            return true;
        }
        if(action.equals("toggle")) {
            if(disabled.remove(id)) say(p,"Trade requests enabled until server restart.");
            else { disabled.add(id);removeRequests(id);say(p,"Trade requests disabled until server restart."); }
            return true;
        }
        if(action.equals("accept") || action.equals("deny")) {
            List<Request> incoming=requests.values().stream().filter(r->r.to().equals(id) && System.nanoTime()-r.expires()<0)
                .filter(r->args.length<2 || Optional.ofNullable(Bukkit.getPlayer(r.from())).map(Player::getName).orElse("").equalsIgnoreCase(args[1])).toList();
            if(incoming.isEmpty()) { say(p,"No matching trade request.");return true; }
            if(incoming.size()>1) { say(p,"Use /trade "+action+" <player> to choose a request.");return true; }
            Request request=incoming.get(0);requests.remove(request.from());Player other=Bukkit.getPlayer(request.from());
            if(action.equals("deny")) { say(p,"Request declined.");if(other!=null) say(other,p.getName()+" declined your request.");return true; }
            if(other==null || active.containsKey(id) || active.containsKey(other.getUniqueId()) || !near(p,other) || !usable(p) || !usable(other) || disabled.contains(id) || disabled.contains(other.getUniqueId())) {
                say(p,"Cannot trade: both players must be available, nearby and in survival/adventure mode.");return true;
            }
            if(!InventoryPlan.empty(p.getItemOnCursor()) || !InventoryPlan.empty(other.getItemOnCursor())) { say(p,"Both players must put away the item held by their cursor first.");return true; }
            open(other,p);return true;
        }
        if(args.length!=1) { help(p);return true; }
        Player target=Bukkit.getPlayerExact(args[0]);
        if(target==null || target.equals(p) || !near(p,target)) { say(p,"Choose an online, visible player nearby in your world.");return true; }
        if(!usable(p) || !usable(target) || active.containsKey(id) || active.containsKey(target.getUniqueId()) || disabled.contains(id) || disabled.contains(target.getUniqueId())) { say(p,"One of you is busy, has requests disabled, or cannot trade in this game mode.");return true; }
        long now=System.nanoTime();Long last=cooldowns.get(id);
        if(last!=null && now-last<cooldownSeconds*1_000_000_000L) { say(p,"Please wait before sending another trade request.");return true; }
        long pending=requests.values().stream().filter(r->r.to().equals(target.getUniqueId()) && now-r.expires()<0).count();
        if(pending>=5) { say(p,"That player has too many pending requests.");return true; }
        cooldowns.put(id,now);requests.put(id,new Request(id,target.getUniqueId(),now+requestSeconds*1_000_000_000L));
        say(p,"Request sent to "+target.getName()+" for "+requestSeconds+" seconds.");say(target,p.getName()+" wants to trade! /trade accept "+p.getName()+" or /trade deny "+p.getName());return true;
    }
    private void help(Player p) { say(p,"/trade <player> | /trade accept [player] | /trade deny [player] | /trade cancel | /trade toggle"); }
    private void removeRequests(UUID id) { requests.entrySet().removeIf(e->e.getValue().from().equals(id)||e.getValue().to().equals(id)); }
    private void open(Player a,Player b) {
        Session s=new Session(a,b,System.nanoTime()+tradeSeconds*1_000_000_000L);
        s.gui=Bukkit.createInventory(s,54,Component.text("Trade: "+a.getName()+" | "+b.getName()));
        active.put(a.getUniqueId(),s);active.put(b.getUniqueId(),s);removeRequests(a.getUniqueId());removeRequests(b.getUniqueId());
        draw(s);
        try { a.openInventory(s.gui);b.openInventory(s.gui); }
        catch(RuntimeException ex) { cancel(s,"Could not open the trading menu.");getLogger().warning(ex.toString());return; }
        for(Player p:s.players) say(p,"Click a stack in your inventory to offer it. Items stay with you until completion. Both players must confirm.");
        Bukkit.getScheduler().runTask(this,()-> { if(!s.state.closed() && !viewing(s)) cancel(s,"The trading menu could not stay open."); });
    }
    private ItemStack button(Material material,String name,String... lore) {
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.displayName(Component.text(name,NamedTextColor.YELLOW));
        meta.lore(Arrays.stream(lore).map(l->Component.text(l,NamedTextColor.GRAY)).toList());item.setItemMeta(meta);return item;
    }
    private void draw(Session s) {
        s.gui.clear();ItemStack filler=button(Material.GRAY_STAINED_GLASS_PANE," ");
        for(int i=0;i<54;i++) s.gui.setItem(i,filler);
        for(int side=0;side<2;side++) {
            for(int slot:SLOTS[side]) s.gui.setItem(slot,null);
            int index=0;for(ItemStack item:s.offers.get(side).values()) s.gui.setItem(SLOTS[side][index++],item.clone());
            s.gui.setItem(side==0?36:44,button(Material.PLAYER_HEAD,s.players[side].getName(),side==0?"Offers on the LEFT":"Offers on the RIGHT","Click your offered stack to remove it."));
            s.gui.setItem(side==0?45:53,button(s.state.ready(side)?Material.LIME_CONCRETE:Material.YELLOW_CONCRETE,s.players[side].getName()+": "+(s.state.ready(side)?"CONFIRMED":"Click to confirm"),"Any offer change resets BOTH confirmations.","Click again to unconfirm."));
        }
        s.gui.setItem(49,button(Material.BARRIER,"Cancel trade","Closing this menu also cancels safely."));
        s.gui.setItem(40,button(Material.CLOCK,s.state.both()?"Completing after safety countdown...":"Waiting for both players","Up to 12 full stacks per player.","Offer items by clicking your inventory below.","No items move until the trade completes."));
    }
    private boolean viewing(Session s) { return Arrays.stream(s.players).allMatch(p->p.isOnline() && p.getOpenInventory().getTopInventory()==s.gui); }
    private boolean valid(Session s) { return !s.state.closed() && near(s.players[0],s.players[1]) && usable(s.players[0]) && usable(s.players[1]) && viewing(s); }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if(!(event.getWhoClicked() instanceof Player p)) return;
        Session s=active.get(p.getUniqueId());
        if(s==null && !(event.getView().getTopInventory().getHolder() instanceof Session)) return;
        event.setCancelled(true);
        if(s==null || s.state.closed() || event.getView().getTopInventory()!=s.gui || event.getClick()!=ClickType.LEFT) return;
        int side=s.side(p.getUniqueId()),raw=event.getRawSlot();long revision=s.state.revision();
        boolean bottom=event.getClickedInventory()==p.getInventory();int storageSlot=event.getSlot();
        Bukkit.getScheduler().runTask(this,()-> {
            if(!valid(s) || active.get(p.getUniqueId())!=s) { cancel(s,"Trade cancelled because a player is no longer available.");return; }
            if(revision!=s.state.revision()) { say(p,"The offer changed. Review the menu and click again.");return; }
            if(raw==49) { cancel(s,"Trade cancelled.");return; }
            if(raw==(side==0?45:53)) {
                if(s.offers.get(0).isEmpty() && s.offers.get(1).isEmpty()) { say(p,"Offer at least one item first.");return; }
                if(!refresh(s)) return;
                s.state.confirm(side,revision,System.nanoTime(),confirmSeconds*1_000_000_000L);draw(s);
                if(s.state.both()) for(Player viewer:s.players) say(viewer,"Both confirmed. Completing in "+confirmSeconds+" seconds. You can still change or cancel.");return;
            }
            LinkedHashMap<Integer,ItemStack> offer=s.offers.get(side);
            if(bottom && storageSlot>=0 && storageSlot<36) {
                if(offer.containsKey(storageSlot)) offer.remove(storageSlot);
                else {
                    ItemStack item=p.getInventory().getItem(storageSlot);
                    if(InventoryPlan.empty(item)) return;
                    if(offer.size()>=12) { say(p,"You can offer up to 12 stacks.");return; }
                    offer.put(storageSlot,item.clone());
                }
            } else {
                int index=-1;for(int i=0;i<SLOTS[side].length;i++) if(raw==SLOTS[side][i]) index=i;
                if(index<0 || index>=offer.size()) return;
                offer.remove(new ArrayList<>(offer.keySet()).get(index));
            }
            s.state.changed();draw(s);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event) {
        if(active.containsKey(event.getWhoClicked().getUniqueId()) || event.getView().getTopInventory().getHolder() instanceof Session) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void close(InventoryCloseEvent event) {
        if(event.getInventory().getHolder() instanceof Session s && !s.state.closed()) {
            // Leave InventoryCloseEvent before closing the other viewer's inventory.
            s.state.close();unlink(s);
            Bukkit.getScheduler().runTask(this,()->closeViews(s,"Trade cancelled. Your items stayed in your inventory."));
        }
    }
    private void unlink(Session s) { s.gui.clear();for(Player p:s.players) active.remove(p.getUniqueId(),s); }
    private void closeViews(Session s,String message) {
        for(Player p:s.players) if(p.isOnline()) { if(p.getOpenInventory().getTopInventory()==s.gui) p.closeInventory();say(p,message); }
    }
    private void cancel(Session s,String reason) { if(s!=null && s.state.close()) { unlink(s);closeViews(s,reason); } }
    private boolean refresh(Session s) {
        boolean changed=false;
        for(int side=0;side<2;side++) {
            ItemStack[] items=s.players[side].getInventory().getStorageContents();
            if(!InventoryPlan.matches(items,s.offers.get(side))) {
                s.offers.get(side).entrySet().removeIf(e->!Objects.equals(e.getValue(),items[e.getKey()]));changed=true;
            }
        }
        if(changed) { s.state.changed();draw(s);for(Player p:s.players) say(p,"Inventory changed. Invalid offers were removed; both players must confirm again."); }
        return !changed;
    }
    private void finish(Session s) {
        if(!valid(s) || !s.state.both()) { cancel(s,"Trade cancelled because a player is unavailable.");return; }
        if(!refresh(s)) return;
        Player a=s.players[0],b=s.players[1];ItemStack[] oldA=InventoryPlan.copy(a.getInventory().getStorageContents()),oldB=InventoryPlan.copy(b.getInventory().getStorageContents());
        ItemStack[] newA,newB;
        try {
            newA=InventoryPlan.receive(oldA,s.offers.get(0),s.offers.get(1).values(),a.getInventory().getMaxStackSize());
            newB=InventoryPlan.receive(oldB,s.offers.get(1),s.offers.get(0).values(),b.getInventory().getMaxStackSize());
        } catch(IllegalArgumentException ex) { s.state.changed();draw(s);for(Player p:s.players) say(p,ex.getMessage());return; }
        // No events, tasks or awaits occur between these writes. Both plans are complete before either inventory changes.
        try { a.getInventory().setStorageContents(newA);b.getInventory().setStorageContents(newB); }
        catch(RuntimeException ex) {
            try { a.getInventory().setStorageContents(oldA); } catch(RuntimeException rollback) { getLogger().severe("Rollback failed for "+a.getUniqueId()+": "+rollback); }
            try { b.getInventory().setStorageContents(oldB); } catch(RuntimeException rollback) { getLogger().severe("Rollback failed for "+b.getUniqueId()+": "+rollback); }
            getLogger().severe("Trade inventory update failed: "+ex);cancel(s,"Trade failed; inventories were restored where possible. Contact staff if anything is wrong.");return;
        }
        s.state.close();unlink(s);closeViews(s,"Trade complete!");
        getLogger().info("Completed item trade: "+a.getName()+" ("+a.getUniqueId()+", "+s.offers.get(0).size()+" stacks) <-> "+b.getName()+" ("+b.getUniqueId()+", "+s.offers.get(1).size()+" stacks)");
    }
    private void pulse() {
        long now=System.nanoTime();
        requests.entrySet().removeIf(e-> {
            Request r=e.getValue();if(now-r.expires()<0) return false;
            Player from=Bukkit.getPlayer(r.from());if(from!=null) say(from,"Your trade request expired.");return true;
        });
        cooldowns.entrySet().removeIf(e->now-e.getValue()>60_000_000_000L);
        for(Session s:new HashSet<>(active.values())) {
            if(now-s.expires>=0) { cancel(s,"Trade timed out. Your items stayed with you.");continue; }
            if(!valid(s)) { cancel(s,"Trade cancelled: stay nearby and keep the menu open.");continue; }
            if(!refresh(s)) continue;
            if(s.state.canFinish(now)) finish(s);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e) { UUID id=e.getPlayer().getUniqueId();cancel(active.get(id),"Trade cancelled: a player disconnected.");removeRequests(id);cooldowns.remove(id); }
    @EventHandler(priority=EventPriority.LOWEST) public void death(PlayerDeathEvent e) { cancel(active.get(e.getEntity().getUniqueId()),"Trade cancelled: a player died."); }
    @EventHandler(ignoreCancelled=true) public void teleport(PlayerTeleportEvent e) { cancel(active.get(e.getPlayer().getUniqueId()),"Trade cancelled: a player teleported."); }
    @EventHandler(ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent e) { cancel(active.get(e.getPlayer().getUniqueId()),"Trade cancelled: game mode changed."); }
    @EventHandler(ignoreCancelled=true) public void drop(PlayerDropItemEvent e) { if(active.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e) { if(active.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void pickup(EntityPickupItemEvent e) { if(e.getEntity() instanceof Player p && active.containsKey(p.getUniqueId())) e.setCancelled(true); }
    @Override public void onDisable() { if(heartbeat!=null) heartbeat.cancel();for(Session s:new HashSet<>(active.values())) cancel(s,"Trade cancelled: server/plugin is stopping.");requests.clear();cooldowns.clear();disabled.clear(); }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!(sender instanceof Player p) || !p.hasPermission("ultimatetrades.use")) return List.of();
        List<String> options=new ArrayList<>();
        if(args.length==1) { options.addAll(List.of("accept","deny","cancel","toggle","help"));for(Player other:Bukkit.getOnlinePlayers()) if(!p.equals(other) && p.canSee(other)) options.add(other.getName()); }
        else if(args.length==2 && (args[0].equalsIgnoreCase("accept")||args[0].equalsIgnoreCase("deny"))) for(Request r:requests.values()) if(r.to().equals(p.getUniqueId())) { Player other=Bukkit.getPlayer(r.from());if(other!=null && p.canSee(other)) options.add(other.getName()); }
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().sorted().toList();
    }
}
