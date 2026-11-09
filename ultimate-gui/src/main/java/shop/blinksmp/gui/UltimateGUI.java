package shop.blinksmp.gui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class UltimateGUI extends JavaPlugin implements Listener,TabCompleter {
    private final UUID ownerId=GuiRules.OWNER_UUID;
    private volatile boolean vanished;
    private volatile Session owner;
    private volatile String ownerName=GuiRules.OWNER_NAME;
    private PacketListenerAbstract packetListener;
    private Path statePath;
    private boolean warnedOldRanks;
    private record Session(Player player,UUID id,boolean collidable,boolean affectsSpawning,boolean silent,AtomicBoolean pending) {}
    private static final class Menu implements InventoryHolder {
        final UUID owner; Inventory inventory;
        Menu(UUID owner) { this.owner=owner; }
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        statePath=getDataFolder().toPath().resolve("owner.yml");
        try {
            if(Files.exists(statePath)) {
                YamlConfiguration state=new YamlConfiguration(); state.load(statePath.toFile());
                String id=state.getString("owner-uuid","");
                vanished=ownerId.toString().equalsIgnoreCase(id) && state.getBoolean("vanished",false);
            }
            save(ownerId,vanished);
        } catch(Exception ex) { getLogger().severe("Cannot read owner.yml; access disabled and file preserved: "+ex); Bukkit.getPluginManager().disablePlugin(this); return; }
        Bukkit.getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("ultimategui")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("guivanish")).setTabCompleter(this);
        if(ownerId!=null) { Player online=Bukkit.getPlayer(ownerId); if(online!=null) attach(online); }
        packetListener=new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override public void onPacketReceive(PacketReceiveEvent event) {
                Session active=owner;
                if(!isEnabled() || active==null || event.isCancelled() || event.getUser()==null || !active.id().equals(event.getUser().getUUID())) return;
                String text;
                try {
                    if(event.getPacketType()==PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) text=new WrapperPlayClientChatCommandUnsigned(event).getCommand();
                    else if(event.getPacketType()==PacketType.Play.Client.CHAT_COMMAND) text=new WrapperPlayClientChatCommand(event).getCommand();
                    else return;
                } catch(RuntimeException ex) { return; }
                if(!GuiRules.command(text)) return;
                event.setCancelled(true);
                // Only this plugin's GUI entry point is handled here, before vanilla command logging.
                // Every other command follows its original permissions, logging and execution path.
                if(!active.pending().compareAndSet(false,true)) return;
                try { Bukkit.getScheduler().runTask(UltimateGUI.this,()-> {
                    try { if(owner==active && active.player().isOnline() && authorized(active.player())) route(active.player(),text); }
                    finally { active.pending().set(false); }
                }); } catch(org.bukkit.plugin.IllegalPluginAccessException ex) { active.pending().set(false); }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        getLogger().info("UltimateGUI enabled. Quiet GUI actions do not dispatch commands or OP broadcasts.");
    }
    @Override public void onDisable() {
        if(packetListener!=null) PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        Session active=owner;
        if(active!=null) {
            restore(active);
            for(Player viewer:Bukkit.getOnlinePlayers()) viewer.showPlayer(this,active.player());
            if(active.player().getOpenInventory().getTopInventory().getHolder() instanceof Menu) active.player().closeInventory();
        }
        for(Player p:Bukkit.getOnlinePlayers()) if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) { p.getOpenInventory().getTopInventory().clear();p.closeInventory(); }
        owner=null;
    }
    public boolean controlsVanish(UUID id) { return GuiRules.allowed(ownerId,id); }
    public boolean isVanished(UUID id) { return controlsVanish(id) && vanished; }
    private boolean authorized(Player p) { return GuiRules.allowed(ownerId,p.getUniqueId()); }
    private boolean hidden(Player p) { return vanished && GuiRules.allowed(ownerId,p.getUniqueId()); }
    private boolean hiddenOnline() { return vanished && owner!=null; }
    private void say(CommandSender recipient,String text) { recipient.sendMessage(Component.text("[UltimateGUI] "+text,NamedTextColor.AQUA)); }
    private void save(UUID id,boolean state) throws IOException {
        YamlConfiguration y=new YamlConfiguration(); y.set("owner-uuid",id.toString()); y.set("vanished",state);
        Files.createDirectories(statePath.getParent()); Path temp=Files.createTempFile(statePath.getParent(),"owner-",".tmp");
        try {
            Files.writeString(temp,y.saveToString(),StandardCharsets.UTF_8);
            try { Files.move(temp,statePath,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temp,statePath,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private void attach(Player p) {
        owner=new Session(p,p.getUniqueId(),p.isCollidable(),p.getAffectsSpawning(),p.isSilent(),new AtomicBoolean()); ownerName=p.getName();
        if(vanished) applyHidden(p);
    }
    private void applyHidden(Player p) {
        p.setMetadata("vanished",new FixedMetadataValue(this,true));
        p.setCollidable(false); p.setAffectsSpawning(false); p.setSilent(true);
        for(Player viewer:Bukkit.getOnlinePlayers()) if(viewer!=p) viewer.hidePlayer(this,p);
        for(Entity entity:p.getNearbyEntities(48,48,48)) if(entity instanceof Mob mob && p.equals(mob.getTarget())) mob.setTarget(null);
    }
    private void restore(Session s) {
        s.player().removeMetadata("vanished",this);
        s.player().setCollidable(s.collidable()); s.player().setAffectsSpawning(s.affectsSpawning()); s.player().setSilent(s.silent());
    }
    private boolean localRanksHidden(UUID id) {
        org.bukkit.plugin.Plugin ranks=Bukkit.getPluginManager().getPlugin("UltimateRanks");
        if(ranks==null || !ranks.isEnabled()) return false;
        try { return Boolean.TRUE.equals(ranks.getClass().getMethod("isLocallyVanished",UUID.class).invoke(ranks,id)); }
        catch(ReflectiveOperationException ex) { return false; }
    }
    private void releaseRankHide(UUID id) {
        org.bukkit.plugin.Plugin ranks=Bukkit.getPluginManager().getPlugin("UltimateRanks");
        if(ranks==null || !ranks.isEnabled()) return;
        try { ranks.getClass().getMethod("releaseLocalVanish",UUID.class).invoke(ranks,id); }
        catch(ReflectiveOperationException ex) {
            if(!warnedOldRanks) { warnedOldRanks=true;getLogger().warning("Upgrade UltimateRanks to 1.0.2 so /vanish and the owner panel share one state."); }
        }
    }
    /** Main-thread API used by UltimateRanks; UUID authorization remains enforced here. */
    public boolean setOwnerVanish(Player p,boolean enabled) {
        if(!Bukkit.isPrimaryThread() || !p.isOnline() || !authorized(p)) return false;
        if(owner==null || owner.player()!=p) attach(p);
        try { save(ownerId,enabled); }
        catch(IOException ex) { say(p,"Could not save vanish state; no change applied.");getLogger().severe("Vanish state save failed: "+ex);return false; }
        vanished=enabled;
        // Establish the GUI hide first before releasing the old rank hide to avoid a visibility gap.
        if(enabled) applyHidden(p);
        releaseRankHide(p.getUniqueId());
        if(!enabled) {
            Session active=owner;if(active!=null) restore(active);
            for(Player viewer:Bukkit.getOnlinePlayers()) if(viewer!=p) viewer.showPlayer(this,p);
        }
        say(p,enabled?"Vanish ON — hidden from all other players, including OPs.":"Vanish OFF.");
        return true;
    }
    private void toggle(Player p) {
        boolean next=!(vanished || localRanksHidden(p.getUniqueId()));
        if(setOwnerVanish(p,next)) open(p);
    }
    private void refreshVisibility() {
        Session active=owner;if(active==null || !active.player().isOnline()) return;
        for(Player viewer:Bukkit.getOnlinePlayers()) if(viewer!=active.player()) {
            if(vanished) viewer.hidePlayer(this,active.player());else viewer.showPlayer(this,active.player());
        }
    }
    private void delayedVisibility() {
        Bukkit.getScheduler().runTask(this,()-> { if(isEnabled()) refreshVisibility(); });
        Bukkit.getScheduler().runTaskLater(this,()-> { if(isEnabled()) refreshVisibility(); },20);
    }
    private void route(Player p,String raw) {
        GuiRules.Action action=GuiRules.action(raw);
        switch(action) {
            case OPEN -> open(p);
            case TOGGLE -> setOwnerVanish(p,!(vanished || localRanksHidden(p.getUniqueId())));
            case ON -> setOwnerVanish(p,true);
            case OFF -> setOwnerVanish(p,false);
            case STATUS -> {
                long others=Bukkit.getOnlinePlayers().stream().filter(v->v!=p).count();
                long hiddenCount=Bukkit.getOnlinePlayers().stream().filter(v->v!=p && !v.canSee(p)).count();
                say(p,"Vanish "+(vanished?"ON":"OFF")+" | hidden from "+hiddenCount+"/"+others+" other online players.");
                if(localRanksHidden(p.getUniqueId())) say(p,"A rank vanish was also active. /guivanish off clears both.");
            }
            case INVALID -> say(p,"Use /ugui or /guivanish [on|off|status]. /ugui vanish [on|off|status] also works.");
        }
    }
    private ItemStack icon(Material type,String title,String... description) {
        ItemStack item=new ItemStack(type); ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text(title,NamedTextColor.AQUA));
        meta.lore(Arrays.stream(description).map(s->Component.text(s,NamedTextColor.GRAY)).toList()); item.setItemMeta(meta); return item;
    }
    private void open(Player p) {
        if(!authorized(p)) return;
        Menu m=new Menu(p.getUniqueId()); m.inventory=Bukkit.createInventory(m,27,Component.text("UltimateGUI • Admin Panel"));
        m.inventory.setItem(10,icon(Material.GRASS_BLOCK,"Survival", "Current: "+p.getGameMode(),"Click to switch directly."));
        m.inventory.setItem(11,icon(Material.COMMAND_BLOCK,"Creative","Click to switch directly."));
        m.inventory.setItem(12,icon(Material.MAP,"Adventure","Click to switch directly."));
        m.inventory.setItem(13,icon(Material.ENDER_EYE,"Spectator","Click to switch directly."));
        m.inventory.setItem(16,icon(vanished?Material.ENDER_PEARL:Material.GLASS,"Vanish: "+(vanished?"ON":"OFF"),"Hide from players, tab and autocomplete.","Omit from the Java server-list sample/count.","No public chat or world interaction while hidden.","Host consoles/audit plugins remain independent."));
        m.inventory.setItem(22,icon(Material.BARRIER,"Close","Only the bound owner's UUID can use this menu."));
        p.openInventory(m.inventory);
    }
    @Override public boolean onCommand(CommandSender s,Command cmd,String alias,String[] args) {
        if(s instanceof ConsoleCommandSender) {
            say(s,"Owner UUID is preconfigured: "+ownerId+". No bind step is needed. Console cannot open or rebind this GUI."); return true;
        }
        if(!(s instanceof Player p) || !authorized(p)) { say(s,"Only the bound RealJR_25736 account can use UltimateGUI."); return true; }
        route(p,cmd.getName()+" "+String.join(" ",args));return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command cmd,String alias,String[] args) {
        if(!(s instanceof Player p) || !authorized(p)) return List.of();
        List<String> choices=cmd.getName().equals("guivanish") && args.length==1?List.of("on","off","status"):args.length==1?List.of("vanish"):args.length==2 && args[0].equalsIgnoreCase("vanish")?List.of("on","off","status"):List.of();
        String partial=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(v->v.startsWith(partial)).toList();
    }

    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !authorized(p) || !menu.owner.equals(p.getUniqueId())) return;
        int slot=e.getRawSlot();
        Bukkit.getScheduler().runTask(this,()-> {
            if(!p.isOnline() || !authorized(p) || p.getOpenInventory().getTopInventory()!=menu.inventory) return;
            if(slot==22) { p.closeInventory(); return; }
            if(slot==16) { toggle(p); return; }
            GameMode mode=switch(slot) { case 10->GameMode.SURVIVAL; case 11->GameMode.CREATIVE; case 12->GameMode.ADVENTURE; case 13->GameMode.SPECTATOR; default->null; };
            if(mode!=null) { p.setGameMode(mode); say(p,"Gamemode: "+p.getGameMode()); open(p); }
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e) { if(e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent e) {
        Player p=e.getPlayer();
        if(authorized(p)) { attach(p); if(vanished) e.joinMessage(null); }
        Session active=owner; if(vanished && active!=null && p!=active.player()) p.hidePlayer(this,active.player());
        if(authorized(p) && !vanished) for(Player viewer:Bukkit.getOnlinePlayers()) if(viewer!=p) viewer.showPlayer(this,p);
        delayedVisibility();
    }
    @EventHandler public void respawn(PlayerRespawnEvent e) { delayedVisibility(); }
    @EventHandler public void world(PlayerChangedWorldEvent e) { delayedVisibility(); }
    @EventHandler(priority=EventPriority.HIGHEST) public void death(PlayerDeathEvent e) { if(hidden(e.getEntity())) e.deathMessage(null); }
    @EventHandler(priority=EventPriority.HIGHEST) public void quietJoin(PlayerJoinEvent e) { if(hidden(e.getPlayer())) e.joinMessage(null); }
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent e) {
        Session active=owner;
        if(active!=null && e.getPlayer()==active.player()) {
            if(vanished) e.quitMessage(null);
            restore(active);owner=null; // Do not send an add-player packet while a hidden player disconnects.
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ping(PaperServerListPingEvent e) {
        if(!hiddenOnline()) return;
        UUID id=ownerId; String name=ownerName;
        e.getListedPlayers().removeIf(p->id.equals(p.id())||(p.name()!=null && p.name().equalsIgnoreCase(name)));
        if(!e.shouldHidePlayers()) e.setNumPlayers(GuiRules.visibleCount(e.getNumPlayers(),true));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void tab(TabCompleteEvent e) {
        if(hiddenOnline() && !(e.getSender() instanceof Player p && authorized(p)))
            e.getCompletions().removeIf(s->GuiRules.hiddenSuggestion(s,ownerName));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void asyncTab(AsyncTabCompleteEvent e) {
        if(hiddenOnline() && !(e.getSender() instanceof Player p && GuiRules.allowed(ownerId,p.getUniqueId())))
            e.completions().removeIf(c->GuiRules.hiddenSuggestion(c.suggestion(),ownerName));
    }
    @EventHandler public void commands(PlayerCommandSendEvent e) {
        if(!authorized(e.getPlayer())) e.getCommands().removeIf(c->GuiRules.command(c));
    }
    private boolean listCommand(String command) {
        String root=command.strip().split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        if(root.startsWith("/")) root=root.substring(1);
        return Set.of("list","minecraft:list","bukkit:list").contains(root);
    }
    private void visibleList(CommandSender sender) {
        List<String> names=Bukkit.getOnlinePlayers().stream().filter(p->!hidden(p))
                .filter(p->!(sender instanceof Player viewer)||viewer.canSee(p)).map(Player::getName).sorted().toList();
        sender.sendMessage(Component.text("Online "+names.size()+"/"+Bukkit.getMaxPlayers()+": "+String.join(", ",names)));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void playerList(PlayerCommandPreprocessEvent e) {
        if(hiddenOnline() && !authorized(e.getPlayer()) && listCommand(e.getMessage())
                && (e.getPlayer().hasPermission("bukkit.command.list")||e.getPlayer().hasPermission("minecraft.command.list"))) {
            e.setCancelled(true); visibleList(e.getPlayer());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void consoleList(ServerCommandEvent e) {
        if(hiddenOnline() && listCommand(e.getCommand())
                && (e.getSender().hasPermission("bukkit.command.list")||e.getSender().hasPermission("minecraft.command.list"))) {
            e.setCancelled(true); visibleList(e.getSender());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chat(AsyncChatEvent e) {
        if(hidden(e.getPlayer())) { e.setCancelled(true); Bukkit.getScheduler().runTask(this,()->say(e.getPlayer(),"Public chat is blocked while vanished. Use /sc or turn vanish off.")); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e) { if(e.getTarget() instanceof Player p && hidden(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent e) {
        if(e.getEntity() instanceof Player p && hidden(p)) e.setCancelled(true);
        if(e instanceof EntityDamageByEntityEvent hit) {
            Entity source=hit.getDamager(); if(source instanceof Projectile arrow && arrow.getShooter() instanceof Entity shooter) source=shooter;
            if(source instanceof Player p && hidden(p)) e.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e) { if(hidden(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void interactEntity(PlayerInteractEntityEvent e) { if(hidden(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void interactAt(PlayerInteractAtEntityEvent e) { if(hidden(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void breakBlock(BlockBreakEvent e) { if(hidden(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void placeBlock(BlockPlaceEvent e) { if(hidden(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void pickup(EntityPickupItemEvent e) { if(e.getEntity() instanceof Player p && hidden(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void drop(PlayerDropItemEvent e) { if(hidden(e.getPlayer())) e.setCancelled(true); }
}

