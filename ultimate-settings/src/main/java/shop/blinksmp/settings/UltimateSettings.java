package shop.blinksmp.settings;

import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class UltimateSettings extends JavaPlugin implements Listener,TabCompleter {
    private PreferenceStore store;
    private final Map<UUID,Booster> boosts=new HashMap<>();
    private final Map<UUID,Scoreboard> boards=new HashMap<>(), previousBoards=new HashMap<>();
    private final Map<UUID,Long> clicked=new HashMap<>();
    private Plugin ranks;
    private Method rankName,rankPrefix;
    private int chunkCap;
    private boolean manageBoards;
    private int elapsed;
    private final Set<UUID> excludingMonsters=new HashSet<>();
    private record SpawnRange(int blocks,int tick) {}
    private final Map<UUID,SpawnRange> spawnRanges=new HashMap<>();
    private static final LegacyComponentSerializer LEGACY=LegacyComponentSerializer.legacyAmpersand();
    private static final Map<Integer,String> SLOTS=Map.of(10,"booster",12,"rank-tags",14,"players",16,"messages",21,"join-messages",23,"coordinates",25,"exclude-monsters");
    private static final List<String> RANKS=List.of("OWNER","DEV","ADMIN","MOD","TRIAL_MOD","HELPER","MEMBER");
    private static final Set<String> DIRECT=Set.of("msg","tell","w","whisper","pm","message","emsg","etell","ew");
    private static final class Booster {
        final int prior; final boolean inherited; int last;
        Booster(int prior,boolean inherited) { this.prior=prior; this.inherited=inherited; this.last=prior; }
    }
    private static final class Menu implements InventoryHolder {
        final UUID owner; Inventory inventory;
        Menu(UUID owner) { this.owner=owner; }
        public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        chunkCap=Math.max(2,Math.min(32,getConfig().getInt("booster.chunk-send-distance",4)));
        manageBoards=getConfig().getBoolean("manage-rank-scoreboards",true);
        try { store=new PreferenceStore(getDataFolder().toPath().resolve("players.yml")); }
        catch(Exception ex) { getLogger().severe("Cannot load settings; file preserved: "+ex); Bukkit.getPluginManager().disablePlugin(this); return; }
        Bukkit.getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("settings")).setTabCompleter(this);
        bindRanks();
        for(Player p:Bukkit.getOnlinePlayers()) apply(p);
        Bukkit.getScheduler().runTaskTimer(this,this::tick,20,20);
        getLogger().info("Ready: /settings is available to all players. Booster reduces chunk traffic; it does not change internet ping.");
    }
    @Override public void onDisable() {
        for(Player p:Bukkit.getOnlinePlayers()) {
            restoreBooster(p); releaseBoard(p);
            for(Player target:Bukkit.getOnlinePlayers()) p.showPlayer(this,target);
            if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
        }
        boosts.clear(); boards.clear(); previousBoards.clear(); clicked.clear(); excludingMonsters.clear(); spawnRanges.clear();
    }
    // Public bridge methods are thread-safe snapshot reads used by UltimateRanks.
    public boolean showRankTags(UUID id) { return store==null || store.get(id).rankTags(); }
    public boolean managesRankTab() { return isEnabled() && manageBoards; }
    public void refreshRankDisplays() { if(Bukkit.isPrimaryThread()) refreshBoards(); }
    private Preferences preferences(Player p) { return store.get(p.getUniqueId()); }
    private void say(CommandSender to,String text) { to.sendMessage(Component.text("[Settings] "+text)); }
    private void bindRanks() {
        ranks=Bukkit.getPluginManager().getPlugin("UltimateRanks"); rankName=rankPrefix=null;
        if(ranks==null || !ranks.isEnabled()) { ranks=null; return; }
        try { rankName=ranks.getClass().getMethod("getRankName",UUID.class); rankPrefix=ranks.getClass().getMethod("getRankPrefix",UUID.class); }
        catch(ReflectiveOperationException ex) { ranks=null; getLogger().warning("Rank display toggles require UltimateRanks 1.0.1 or newer."); }
    }
    private String rankValue(Method method,Player p,String fallback) {
        if(ranks==null || method==null) return fallback;
        try { return (String)method.invoke(ranks,p.getUniqueId()); }
        catch(ReflectiveOperationException ex) { return fallback; }
    }
    private void rebuildBoard(Player viewer) {
        UUID id=viewer.getUniqueId();
        if(!manageBoards || ranks==null) { releaseBoard(viewer); return; }
        Scoreboard mine=boards.get(id);
        if(mine!=null && viewer.getScoreboard()!=mine) { boards.remove(id); previousBoards.remove(id); return; }
        if(mine==null) {
            // Preserve custom sidebars/scoreboards owned by other plugins.
            if(viewer.getScoreboard()!=Bukkit.getScoreboardManager().getMainScoreboard()
                    || !viewer.getScoreboard().getTeams().isEmpty() || !viewer.getScoreboard().getObjectives().isEmpty()) return;
            previousBoards.put(id,viewer.getScoreboard());
            mine=Bukkit.getScoreboardManager().getNewScoreboard(); boards.put(id,mine); viewer.setScoreboard(mine);
        }
        Set<String> onlineNames=new HashSet<>();
        for(Player target:Bukkit.getOnlinePlayers()) {
            onlineNames.add(target.getName());
            String rank=rankValue(rankName,target,"MEMBER"); int order=RANKS.indexOf(rank); if(order<0) order=6;
            String key="us"+order; Team team=mine.getTeam(key);
            if(team==null) { team=mine.registerNewTeam(key); team.setCanSeeFriendlyInvisibles(false); team.setAllowFriendlyFire(true); }
            Component prefix=showRankTags(id)?LEGACY.deserialize(rankValue(rankPrefix,target,"")):Component.empty();
            // Prefix may be configured per rank; all members of this team have the same rank.
            team.prefix(prefix);
            Team current=mine.getEntryTeam(target.getName());
            if(current!=team) team.addEntry(target.getName());
        }
        for(Team team:mine.getTeams()) for(String name:new HashSet<>(team.getEntries())) if(!onlineNames.contains(name)) team.removeEntry(name);
    }
    private void refreshBoards() { for(Player p:Bukkit.getOnlinePlayers()) rebuildBoard(p); }
    private void releaseBoard(Player p) {
        UUID id=p.getUniqueId(); Scoreboard mine=boards.remove(id),old=previousBoards.remove(id);
        if(mine!=null && old!=null && p.getScoreboard()==mine) p.setScoreboard(old);
    }
    private int worldSend(Player p) {
        int distance=p.getWorld().getSendViewDistance();
        return distance<2?p.getWorld().getViewDistance():Math.min(distance,p.getWorld().getViewDistance());
    }
    private void applyBooster(Player p) {
        if(!preferences(p).booster()) { restoreBooster(p); return; }
        UUID id=p.getUniqueId(); Booster b=boosts.get(id); int world=worldSend(p);
        if(b==null) { int prior=p.getSendViewDistance(); b=new Booster(prior,prior==world || prior<2); boosts.put(id,b); }
        int baseline=b.inherited?world:Math.max(2,b.prior);
        int cap=Preferences.chunkCap(chunkCap,world,baseline);
        if(p.getSendViewDistance()!=cap) p.setSendViewDistance(cap);
        b.last=p.getSendViewDistance();
    }
    private void restoreBooster(Player p) {
        Booster b=boosts.remove(p.getUniqueId());
        if(b!=null) p.setSendViewDistance(b.inherited?-1:b.prior);
    }
    private void visibility(Player viewer) {
        boolean show=preferences(viewer).players();
        for(Player target:Bukkit.getOnlinePlayers()) if(target!=viewer) {
            if(show) viewer.showPlayer(this,target); else viewer.hidePlayer(this,target);
        }
    }
    private void apply(Player p) {
        if(preferences(p).excludeMonsters()) excludingMonsters.add(p.getUniqueId());else excludingMonsters.remove(p.getUniqueId());
        applyBooster(p); visibility(p); rebuildBoard(p);
    }
    private void tick() {
        elapsed++;
        for(Player p:Bukkit.getOnlinePlayers()) {
            if(elapsed%5==0 && boosts.containsKey(p.getUniqueId())) applyBooster(p);
            if(preferences(p).coordinates()) {
                Location l=p.getLocation(); p.sendActionBar(Component.text("X: "+l.getBlockX()+"  Y: "+l.getBlockY()+"  Z: "+l.getBlockZ(),NamedTextColor.AQUA));
            }
        }
    }
    private void saveAndApply(Player p,Preferences next) {
        try { store.put(p.getUniqueId(),next); }
        catch(java.io.IOException ex) { getLogger().severe("Settings save failed: "+ex); say(p,"Could not save. Your previous choices are unchanged."); return; }
        apply(p);
        if(!next.coordinates()) p.sendActionBar(Component.empty());
        open(p);
    }
    private ItemStack item(Material type,String title,boolean on,String... description) {
        ItemStack item=new ItemStack(type); ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text(title,on?NamedTextColor.GREEN:NamedTextColor.GRAY));
        List<Component> lore=new ArrayList<>(); lore.add(Component.text(on?"ON — click to disable":"OFF — click to enable",on?NamedTextColor.GREEN:NamedTextColor.RED));
        for(String line:description) lore.add(Component.text(line,NamedTextColor.GRAY));
        meta.lore(lore); item.setItemMeta(meta); return item;
    }
    private void open(Player p) {
        Menu holder=new Menu(p.getUniqueId()); holder.inventory=Bukkit.createInventory(holder,36,Component.text("UltimateSMP • Settings"));
        Inventory inv=holder.inventory; Preferences v=preferences(p);
        inv.setItem(10,item(Material.FEATHER,"Bedrock Booster",v.booster(),"Send fewer terrain chunks (cap: "+chunkCap+").","Helps chunk loading and low-end devices.","Also usable on Java. Internet ping is unchanged."));
        inv.setItem(12,item(Material.NAME_TAG,"Show Rank Tags",v.rankTags(),"Personal rank prefixes in chat, tab and nametags.",ranks==null?"Install the included UltimateRanks 1.0.1.":"Other scoreboard plugins may control tab/nametags."));
        inv.setItem(14,item(Material.PLAYER_HEAD,"Show Other Players",v.players(),"Show/hide other players on YOUR screen.","This can hide opponents in survival/PvP."));
        inv.setItem(16,item(Material.WRITABLE_BOOK,"Allow Direct Messages",v.messages(),"Allow players to /msg, /tell or /w you.","Custom channels and /reply need their own support."));
        inv.setItem(21,item(Material.OAK_SIGN,"Join / Leave Messages",v.joinMessages(),"Show normal player join and leave notices."));
        inv.setItem(23,item(Material.COMPASS,"Coordinates",v.coordinates(),"Show XYZ coordinates above your hotbar.","May replace another plugin's action bar."));
        inv.setItem(25,item(Material.ZOMBIE_HEAD,"Exclude Me: Natural Monsters",v.excludeMonsters(),"ON: ignore you as a reason to spawn monsters.","Animals and monster spawners are unchanged.","Other nearby players may still allow monsters.","Does not delete mobs or rewrite global caps."));
        ItemStack reset=new ItemStack(Material.BARRIER); ItemMeta meta=reset.getItemMeta(); meta.displayName(Component.text("Reset to Defaults",NamedTextColor.RED)); reset.setItemMeta(meta); inv.setItem(31,reset);
        p.openInventory(inv);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String alias,String[] args) {
        if(!(sender instanceof Player p)) { say(sender,"Run /settings in game."); return true; }
        if(args.length==0) { open(p); return true; }
        if(args[0].equalsIgnoreCase("reset")) { toggleReset(p); return true; }
        if(args.length==2 && args[0].equalsIgnoreCase("toggle") && PreferenceStore.KEYS.contains(args[1].toLowerCase(Locale.ROOT))) {
            toggle(p,args[1].toLowerCase(Locale.ROOT)); return true;
        }
        say(p,"/settings | /settings toggle <booster|rank-tags|players|messages|join-messages|coordinates|exclude-monsters> | /settings reset"); return true;
    }
    private void toggle(Player p,String key) {
        long now=System.nanoTime(); Long prior=clicked.get(p.getUniqueId());
        if(prior!=null && now-prior<1_000_000_000L) return;
        clicked.put(p.getUniqueId(),now); saveAndApply(p,preferences(p).toggle(key));
    }
    @Override public List<String> onTabComplete(CommandSender s,Command cmd,String alias,String[] args) {
        List<String> list=args.length==1?List.of("toggle","reset"):args.length==2&&args[0].equalsIgnoreCase("toggle")?PreferenceStore.KEYS:List.of();
        String start=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT); return list.stream().filter(v->v.startsWith(start)).toList();
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p) || !menu.owner.equals(p.getUniqueId()) || event.getRawSlot()<0 || event.getRawSlot()>=36) return;
        int slot=event.getRawSlot();
        // Schedule inventory replacement outside the inventory transaction.
        Bukkit.getScheduler().runTask(this,()-> {
            if(!p.isOnline() || p.getOpenInventory().getTopInventory()!=menu.inventory) return;
            if(slot==31) { toggleReset(p); return; }
            String key=SLOTS.get(slot); if(key!=null) toggle(p,key);
        });
    }
    private void toggleReset(Player p) {
        long now=System.nanoTime(); Long prior=clicked.get(p.getUniqueId());
        if(prior!=null && now-prior<1_000_000_000L) return;
        clicked.put(p.getUniqueId(),now); saveAndApply(p,Preferences.DEFAULT);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e) { if(e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e) {
        String[] args=e.getMessage().substring(1).split("\\s+"); String name=args[0].toLowerCase(Locale.ROOT); name=name.substring(name.lastIndexOf(':')+1);
        if(!DIRECT.contains(name) || args.length<2) return;
        if(args[1].startsWith("@")) { e.setCancelled(true); say(e.getPlayer(),"Use an exact player name for direct messages."); return; }
        Player recipient=Bukkit.getPlayerExact(args[1]);
        if(recipient!=null && !preferences(recipient).messages()) { e.setCancelled(true); say(e.getPlayer(),"That player has direct messages disabled."); }
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        Player p=e.getPlayer(); Bukkit.getScheduler().runTask(this,()-> {
            if(!p.isOnline()) return;
            apply(p); for(Player viewer:Bukkit.getOnlinePlayers()) visibility(viewer); refreshBoards();
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void joinNotice(PlayerJoinEvent e) {
        Component message=e.joinMessage(); if(message==null) return;
        e.joinMessage(null); sendNotice(e.getPlayer(),message);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void quit(PlayerQuitEvent e) {
        Player p=e.getPlayer(); Component message=e.quitMessage();
        if(message!=null) { e.quitMessage(null); sendNotice(p,message); }
        restoreBooster(p); releaseBoard(p); clicked.remove(p.getUniqueId()); excludingMonsters.remove(p.getUniqueId());spawnRanges.remove(p.getUniqueId());
        for(Scoreboard board:boards.values()) { Team team=board.getEntryTeam(p.getName()); if(team!=null) team.removeEntry(p.getName()); }
    }
    private void sendNotice(Player subject,Component message) {
        Bukkit.getConsoleSender().sendMessage(message);
        for(Player viewer:Bukkit.getOnlinePlayers()) if(preferences(viewer).joinMessages()
                && (preferences(viewer).players() || viewer==subject) && viewer.canSee(subject)) viewer.sendMessage(message);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void spawnRange(PlayerNaturallySpawnCreaturesEvent event) {
        spawnRanges.put(event.getPlayer().getUniqueId(),new SpawnRange(event.isCancelled()?0:Math.max(0,event.getSpawnRadius())*16,Bukkit.getCurrentTick()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void naturalMonster(CreatureSpawnEvent event) {
        if(excludingMonsters.isEmpty() || event.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.NATURAL || event.getEntity().getSpawnCategory()!=SpawnCategory.MONSTER) return;
        Location spawn=event.getLocation();List<MonsterSpawnPolicy.Candidate> candidates=new ArrayList<>();int tick=Bukkit.getCurrentTick();
        for(Player player:spawn.getWorld().getPlayers()) {
            SpawnRange range=spawnRanges.get(player.getUniqueId());
            int blocks=range!=null && range.tick()==tick?range.blocks():Math.max(0,Math.min(8,Math.min(player.getViewDistance(),player.getWorld().getSimulationDistance())))*16;
            Location at=player.getLocation();
            candidates.add(new MonsterSpawnPolicy.Candidate(at.getX(),at.getY(),at.getZ(),blocks,
                excludingMonsters.contains(player.getUniqueId()),!player.isDead() && player.getGameMode()!=GameMode.SPECTATOR && player.getAffectsSpawning()));
        }
        if(MonsterSpawnPolicy.cancel(true,true,spawn.getX(),spawn.getY(),spawn.getZ(),candidates)) event.setCancelled(true);
    }
    @EventHandler public void world(PlayerChangedWorldEvent e) { apply(e.getPlayer()); }
    @EventHandler public void enabled(PluginEnableEvent e) {
        if(e.getPlugin().getName().equals("UltimateRanks")) { bindRanks(); refreshBoards(); }
    }
    @EventHandler public void disabled(PluginDisableEvent e) {
        if(e.getPlugin()==ranks) { ranks=null; rankName=rankPrefix=null; for(Player p:Bukkit.getOnlinePlayers()) releaseBoard(p); }
    }
}
