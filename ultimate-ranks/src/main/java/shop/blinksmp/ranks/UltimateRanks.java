package shop.blinksmp.ranks;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static shop.blinksmp.ranks.RankStore.*;

public final class UltimateRanks extends JavaPlugin implements Listener, TabCompleter {
    private RankStore store;
    private final Map<UUID,PermissionAttachment> attachments=new HashMap<>();
    private final Map<UUID,Component> oldTabs=new HashMap<>(), appliedTabs=new HashMap<>();
    private final Map<UUID,GameMode> spectatorModes=new HashMap<>();
    private final Set<UUID> vanished=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Integer> frozen=new HashMap<>();
    private final Map<UUID,Inventory> inspections=new HashMap<>();
    private volatile Map<Rank,Component> prefixes=Map.of();
    private static final LegacyComponentSerializer LEGACY=LegacyComponentSerializer.legacyAmpersand();
    private static final Set<String> MUTED_COMMANDS=Set.of("me","say","msg","tell","w","whisper","r","reply","pm","message","emsg","etell","ew","eme");

    @Override public void onEnable() {
        saveDefaultConfig();
        try { store=new RankStore(getDataFolder().toPath().resolve("players.yml")); }
        catch(Exception ex) {
            getLogger().severe("Cannot load players.yml; file preserved. Fix it before enabling UltimateRanks: "+ex);
            Bukkit.getPluginManager().disablePlugin(this); return;
        }
        Map<Rank,Component> colors=new EnumMap<>(Rank.class);
        for(Rank r:Rank.values()) colors.put(r,LEGACY.deserialize(getConfig().getString("prefixes."+r.name(),r.color+"["+r.label()+"] &r")));
        prefixes=Map.copyOf(colors);
        Bukkit.getPluginManager().registerEvents(this,this);
        for(String cmd:getDescription().getCommands().keySet()) Objects.requireNonNull(getCommand(cmd)).setTabCompleter(this);
        for(Player p:Bukkit.getOnlinePlayers()) { remember(p); refresh(p); }
        getLogger().info("Ready. Console: rank set <player-or-uuid> OWNER. No player is automatically made staff or OP.");
    }

    @Override public void onDisable() {
        for(Player p:Bukkit.getOnlinePlayers()) {
            for(UUID id:vanished) { Player hidden=Bukkit.getPlayer(id); if(hidden!=null) p.showPlayer(this,hidden); }
            PermissionAttachment a=attachments.remove(p.getUniqueId()); if(a!=null) p.removeAttachment(a);
            Component old=oldTabs.get(p.getUniqueId()), applied=appliedTabs.get(p.getUniqueId());
            if(oldTabs.containsKey(p.getUniqueId()) && Objects.equals(p.playerListName(),applied)) p.playerListName(old);
            GameMode previous=spectatorModes.get(p.getUniqueId()); if(previous!=null && p.getGameMode()==GameMode.SPECTATOR) p.setGameMode(previous);
            if(inspections.containsKey(p.getUniqueId())) p.closeInventory();
        }
        vanished.clear(); frozen.clear(); inspections.clear(); spectatorModes.clear();
    }

    private Rank rank(UUID id) { return store.get(id).rank(); }
    private Rank rank(CommandSender sender) { return sender instanceof Player p ? rank(p.getUniqueId()) : Rank.OWNER; }
    private boolean console(CommandSender s) { return s instanceof ConsoleCommandSender || s instanceof RemoteConsoleCommandSender; }
    private void require(boolean condition,String error) { if(!condition) throw new IllegalArgumentException(error); }
    private void say(CommandSender to,String text) { to.sendMessage(LEGACY.deserialize("&8[&bUltimate&8] &r"+text)); }
    private void audit(CommandSender actor,String action) {
        String text=actor.getName()+": "+action;
        getLogger().info(text);
        for(Player p:Bukkit.getOnlinePlayers()) if(rank(p.getUniqueId()).level>=Rank.HELPER.level) say(p,"&7"+text);
    }
    private void persist(UUID id,Entry e) {
        try { store.put(id,e); }
        catch(IOException ex) { getLogger().severe("Could not save player data: "+ex); throw new IllegalArgumentException("Save failed; action was not applied. Check disk space and console."); }
    }
    private void remember(Player p) {
        Entry e=store.get(p.getUniqueId());
        if(!e.name().equals(p.getName())) {
            try { persist(p.getUniqueId(),e.named(p.getName())); }
            catch(IllegalArgumentException ex) { getLogger().warning(ex.getMessage()); }
        }
    }
    private void refresh(Player p) {
        UUID id=p.getUniqueId(); Rank r=rank(id);
        PermissionAttachment old=attachments.remove(id); if(old!=null) p.removeAttachment(old);
        PermissionAttachment a=p.addAttachment(this); attachments.put(id,a);
        for(String cmd:Rules.MINIMUM.keySet()) a.setPermission("ultimateranks.command."+cmd,Rules.allowed(r,cmd));
        a.setPermission("ultimateranks.staff",r.level>=Rank.HELPER.level);
        for(Rank v:Rank.values()) a.setPermission("ultimateranks.rank."+v.name().toLowerCase(Locale.ROOT),r==v);
        for(String perm:getConfig().getStringList("extra-permissions."+r.name())) a.setPermission(perm,true);
        if(!oldTabs.containsKey(id)) oldTabs.put(id,p.playerListName());
        Component tab=prefixes.get(r).append(Component.text(p.getName()));
        p.playerListName(tab); appliedTabs.put(id,tab);
        if(vanished.contains(id) && !Rules.allowed(r,"vanish")) setVanish(p,false);
        if(spectatorModes.containsKey(id) && !Rules.allowed(r,"gsp")) {
            GameMode prior=spectatorModes.remove(id); if(p.getGameMode()==GameMode.SPECTATOR) p.setGameMode(prior);
        }
        for(Player viewer:Bukkit.getOnlinePlayers()) updateVisibility(viewer);
        p.updateCommands();
    }
    private void updateVisibility(Player viewer) {
        for(UUID id:vanished) {
            Player hidden=Bukkit.getPlayer(id); if(hidden==null || viewer==hidden) continue;
            if(rank(viewer.getUniqueId()).level>=Rank.TRIAL_MOD.level) viewer.showPlayer(this,hidden);
            else viewer.hidePlayer(this,hidden);
        }
    }
    private void setVanish(Player p,boolean enabled) {
        if(enabled) vanished.add(p.getUniqueId()); else vanished.remove(p.getUniqueId());
        for(Player viewer:Bukkit.getOnlinePlayers()) {
            if(!enabled) viewer.showPlayer(this,p); else updateVisibility(viewer);
        }
        say(p,enabled?"&aVanish enabled. Trial Mods and above can still see you.":"&eVanish disabled.");
    }
    private UUID resolve(CommandSender sender,String value) {
        Player online=Bukkit.getPlayerExact(value);
        if(online!=null) {
            require(console(sender) || !(sender instanceof Player viewer) || viewer.canSee(online),"Player not found.");
            return online.getUniqueId();
        }
        try { return UUID.fromString(value); } catch(IllegalArgumentException ignored) {}
        var matches=store.snapshot().entrySet().stream().filter(e->e.getValue().name().equalsIgnoreCase(value)).toList();
        require(matches.size()==1,matches.isEmpty()?"Player not known. Have them join once, or use their UUID.":"Name is ambiguous; use a UUID.");
        UUID id=matches.get(0).getKey(); Player p=Bukkit.getPlayer(id);
        require(p==null || console(sender) || !(sender instanceof Player viewer) || viewer.canSee(p),"Player not found.");
        return id;
    }
    private Player online(UUID id) { Player p=Bukkit.getPlayer(id); require(p!=null,"That player must be online."); return p; }
    private Player self(CommandSender s) { require(s instanceof Player,"Run this command in game."); return (Player)s; }
    private void protect(CommandSender s,UUID target) {
        require(console(s) || rank(s).above(rank(target)),"You can only target a lower rank.");
    }
    private void reverse(CommandSender s,UUID id,int issuer) {
        require(console(s) || Rules.canReverse(rank(s),rank(id),issuer),"You cannot override this staff action.");
    }
    private static String reason(String[] args,int from) {
        if(from>=args.length) return "Staff action";
        String text=String.join(" ",Arrays.copyOfRange(args,from,args.length));
        if(text.length()>200) throw new IllegalArgumentException("Reason must be at most 200 characters.");
        return text;
    }
    private Sanction sanction(CommandSender s,long duration,String reason) {
        return new Sanction(duration==0?0:Math.addExact(System.currentTimeMillis(),duration),reason,s.getName(),console(s)?7:rank(s).level);
    }
    private static String notice(String kind,Sanction s) {
        return kind+": "+s.reason()+"\nBy: "+s.actor()+"\nExpires: "+(s.until()==0?"Never":Instant.ofEpochMilli(s.until()).toString());
    }

    @Override public boolean onCommand(CommandSender s,Command cmd,String label,String[] original) {
        try {
            require(console(s) || s instanceof Player,"Only players and server console can run staff commands.");
            String action=cmd.getName().toLowerCase(Locale.ROOT); String[] args=original;
            if(action.equals("rank")) { ranks(s,args); return true; }
            if(action.equals("staff")) {
                if(args.length==0) {
                    List<String> list=Rules.MINIMUM.keySet().stream().filter(c->console(s)||Rules.allowed(rank(s),c)).sorted().toList();
                    say(s,"Commands: "+String.join(", ",list)); return true;
                }
                action=args[0].toLowerCase(Locale.ROOT); args=Arrays.copyOfRange(args,1,args.length);
                if(action.equals("rank")) { ranks(s,args); return true; }
            }
            require(console(s)||Rules.allowed(rank(s),action),"Your rank cannot use that staff command.");
            if(action.equals("staffchat")) {
                require(args.length>0,"Usage: /sc <message>");
                String message=reason(args,0);
                getLogger().info("[StaffChat] "+s.getName()+": "+message);
                for(Player p:Bukkit.getOnlinePlayers()) if(rank(p.getUniqueId()).level>=Rank.HELPER.level) say(p,"&b[Staff] "+s.getName()+": &f"+message);
                return true;
            }
            if(action.equals("vanish")) { Player p=self(s); setVanish(p,!vanished.contains(p.getUniqueId())); return true; }
            if(action.equals("gsp")) {
                Player p=self(s); require(!frozen.containsKey(p.getUniqueId()),"You are frozen.");
                if(spectatorModes.containsKey(p.getUniqueId())) p.setGameMode(spectatorModes.remove(p.getUniqueId()));
                else { spectatorModes.put(p.getUniqueId(),p.getGameMode()); p.setGameMode(GameMode.SPECTATOR); }
                say(s,"Game mode: "+p.getGameMode()); return true;
            }
            if((action.equals("heal")||action.equals("feed")) && args.length==0) args=new String[]{self(s).getName()};
            require(args.length>0,"Usage: /"+action+" <player> [duration] [reason]");
            UUID id=resolve(s,args[0]); Entry e=store.get(id);
            if(action.equals("tp")) {
                Player actor=self(s), target=online(id);
                require(!frozen.containsKey(actor.getUniqueId()),"You are frozen.");
                teleport(actor,target.getLocation(),s); return true;
            }
            if(action.equals("heal")||action.equals("feed")) {
                if(!(s instanceof Player p && p.getUniqueId().equals(id))) protect(s,id);
                Player p=online(id);
                if(action.equals("heal")) {
                    p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue()); p.setFireTicks(0);
                }
                p.setFoodLevel(20); p.setSaturation(20); say(s,action+" applied to "+p.getName()); return true;
            }
            protect(s,id);
            switch(action) {
                case "warnings" -> {
                    say(s,e.name()+" has "+e.warnings().size()+" retained warnings (showing latest 10).");
                    e.warnings().stream().skip(Math.max(0,e.warnings().size()-10)).forEach(w->say(s,w));
                }
                case "warn" -> {
                    String why=reason(args,1); List<String> history=new ArrayList<>(e.warnings());
                    history.add(Instant.now()+" | "+s.getName()+" | "+why);
                    while(history.size()>100) history.remove(0);
                    persist(id,new Entry(e.name(),e.rank(),e.mute(),e.ban(),history));
                    Player p=Bukkit.getPlayer(id); if(p!=null) say(p,"&eWarning: "+why);
                    audit(s,"warned "+e.name()+": "+why);
                }
                case "kick" -> { Player p=online(id); String why=reason(args,1); p.kick(Component.text("Kicked: "+why)); audit(s,"kicked "+e.name()+": "+why); }
                case "mute", "tempban", "ban" -> {
                    boolean mute=action.equals("mute"); long duration=0; int offset=1;
                    if(!action.equals("ban")) {
                        require(args.length>=2,"Usage: /"+action+" <player> <10m|2h|7d> [reason]");
                        duration=Rules.duration(args[1]); offset=2;
                        long max=mute?Rules.maxMute(rank(s)):Rules.maxTempban(rank(s));
                        require(console(s)||duration<=max,"Duration exceeds your rank's limit.");
                    }
                    Sanction existing=mute?e.mute():e.ban();
                    if(existing!=null && existing.active(System.currentTimeMillis())) reverse(s,id,existing.issuerLevel());
                    Sanction change=sanction(s,duration,reason(args,offset));
                    persist(id,mute?e.muted(change):e.banned(change));
                    Player p=Bukkit.getPlayer(id);
                    if(p!=null) { if(mute) p.sendMessage(Component.text(notice("Muted",change))); else p.kick(Component.text(notice("Banned",change))); }
                    audit(s,action+" "+e.name()+": "+change.reason()+" ("+(duration==0?"permanent":args[1])+")");
                }
                case "unmute", "unban" -> {
                    boolean mute=action.equals("unmute"); Sanction existing=mute?e.mute():e.ban();
                    require(existing!=null,"No matching UltimateRanks punishment.");
                    reverse(s,id,existing.issuerLevel()); persist(id,mute?e.muted(null):e.banned(null));
                    audit(s,action+" "+e.name());
                }
                case "freeze" -> {
                    Player p=online(id);
                    if(frozen.containsKey(id)) { reverse(s,id,frozen.get(id)); frozen.remove(id); say(p,"&aYou are unfrozen."); }
                    else { frozen.put(id,console(s)?7:rank(s).level); p.closeInventory(); say(p,"&cYou are frozen by staff. You can still chat."); }
                    audit(s,(frozen.containsKey(id)?"froze ":"unfroze ")+e.name());
                }
                case "invsee" -> {
                    Player viewer=self(s), target=online(id);
                    Inventory inventory=Bukkit.createInventory(null,54,Component.text("Inventory: "+target.getName()));
                    ItemStack[] items=target.getInventory().getContents();
                    for(int i=0;i<Math.min(items.length,54);i++) if(items[i]!=null) inventory.setItem(i,items[i].clone());
                    viewer.openInventory(inventory); inspections.put(viewer.getUniqueId(),inventory);
                    say(s,"Read-only snapshot. Reopen to refresh.");
                }
                case "bring" -> { Player actor=self(s),target=online(id); require(!frozen.containsKey(id),"Unfreeze the target first."); teleport(target,actor.getLocation(),s); }
                default -> throw new IllegalArgumentException("Unknown staff command. Use /staff.");
            }
        } catch(IllegalArgumentException|ArithmeticException ex) { say(s,"&c"+ex.getMessage()); }
        return true;
    }
    private void teleport(Player moved,Location to,CommandSender sender) {
        moved.teleportAsync(to).whenComplete((ok,error)->Bukkit.getScheduler().runTask(this,()-> {
            if(error!=null || !Boolean.TRUE.equals(ok)) say(sender,"&cTeleport failed or was cancelled.");
            else audit(sender,"teleported "+moved.getName());
        }));
    }
    private void ranks(CommandSender s,String[] args) {
        if(args.length==0 || args[0].equalsIgnoreCase("list")) {
            say(s,"OWNER > DEV > ADMIN > MOD > TRIAL MOD > HELPER > MEMBER");
            say(s,"/rank info [player] | /rank set <player> <rank> | /rank remove <player>"); return;
        }
        String sub=args[0].toLowerCase(Locale.ROOT);
        if(sub.equals("info")) {
            UUID id=args.length>=2?resolve(s,args[1]):self(s).getUniqueId(); Entry e=store.get(id);
            say(s,e.name()+": "+e.rank().label()); return;
        }
        require(sub.equals("set")||sub.equals("remove"),"Use /rank list, info, set or remove.");
        require(console(s)||Rules.allowed(rank(s),"rank"),"ADMIN or higher is required to manage ranks.");
        require(args.length>=(sub.equals("set")?3:2),"Usage: /rank set <player-or-uuid> <rank> or /rank remove <player>");
        UUID id=resolve(s,args[1]); Entry e=store.get(id);
        Rank assigned=sub.equals("remove")?Rank.MEMBER:Rank.parse(String.join(" ",Arrays.copyOfRange(args,2,args.length)));
        require(console(s)||Rules.canAssign(rank(s),e.rank(),assigned),"You may only manage and assign ranks below your own. Console assigns OWNER.");
        persist(id,e.ranked(assigned)); frozen.remove(id);
        Player p=Bukkit.getPlayer(id); if(p!=null) { refresh(p); say(p,"Your rank is now "+assigned.label()); }
        audit(s,"set "+e.name()+" to "+assigned.label());
    }

    @EventHandler(priority=EventPriority.HIGHEST) public void preLogin(AsyncPlayerPreLoginEvent event) {
        Sanction ban=store.get(event.getUniqueId()).ban();
        if(ban!=null && ban.active(System.currentTimeMillis())) event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,Component.text(notice("Banned",ban)));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void login(PlayerLoginEvent event) {
        Sanction ban=store.get(event.getPlayer().getUniqueId()).ban();
        if(ban!=null && ban.active(System.currentTimeMillis())) event.disallow(PlayerLoginEvent.Result.KICK_BANNED,notice("Banned",ban));
    }
    @EventHandler public void join(PlayerJoinEvent event) { remember(event.getPlayer()); refresh(event.getPlayer()); updateVisibility(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        if(vanished.remove(id)) {
            event.quitMessage(null);
            for(Player viewer:Bukkit.getOnlinePlayers()) viewer.showPlayer(this,event.getPlayer());
        }
        if(frozen.remove(id)!=null) getLogger().info(event.getPlayer().getName()+" disconnected while frozen.");
        GameMode prior=spectatorModes.remove(id); if(prior!=null && event.getPlayer().getGameMode()==GameMode.SPECTATOR) event.getPlayer().setGameMode(prior);
        attachments.remove(id); oldTabs.remove(id); appliedTabs.remove(id); inspections.remove(id);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chat(AsyncChatEvent event) {
        UUID id=event.getPlayer().getUniqueId(); Entry e=store.get(id);
        if(e.mute()!=null && e.mute().active(System.currentTimeMillis())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this,()->event.getPlayer().sendMessage(Component.text(notice("Muted",e.mute())))); return;
        }
        if(vanished.contains(id)) { event.setCancelled(true); Bukkit.getScheduler().runTask(this,()->say(event.getPlayer(),"Disable vanish to use public chat, or use /sc.")); return; }
        Component prefix=prefixes.get(e.rank());
        event.renderer((source,displayName,message,viewer)->prefix.append(displayName).append(Component.text(": ")).append(message));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void commands(PlayerCommandPreprocessEvent event) {
        UUID id=event.getPlayer().getUniqueId(); String word=event.getMessage().substring(1).split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        String bare=word.substring(word.lastIndexOf(':')+1);
        Sanction mute=store.get(id).mute();
        if(mute!=null && mute.active(System.currentTimeMillis()) && MUTED_COMMANDS.contains(bare)) { event.setCancelled(true); say(event.getPlayer(),"&cYou are muted."); }
        if(frozen.containsKey(id) && !MUTED_COMMANDS.contains(bare) && !bare.equals("help")) { event.setCancelled(true); say(event.getPlayer(),"&cYou are frozen. Contact staff in chat."); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent event) {
        if(!frozen.containsKey(event.getPlayer().getUniqueId()) || event.getTo()==null) return;
        Location f=event.getFrom(),t=event.getTo();
        if(!f.getWorld().equals(t.getWorld()) || f.getX()!=t.getX() || f.getY()!=t.getY() || f.getZ()!=t.getZ()) {
            Location stopped=f.clone(); stopped.setYaw(t.getYaw()); stopped.setPitch(t.getPitch()); event.setTo(stopped);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event) {
        if(frozen.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent event) {
        if(event.getEntity() instanceof Player p && (frozen.containsKey(p.getUniqueId())||vanished.contains(p.getUniqueId()))) event.setCancelled(true);
        if(event instanceof EntityDamageByEntityEvent hit) {
            Entity source=hit.getDamager(); if(source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source=shooter;
            if(source instanceof Player p && (frozen.containsKey(p.getUniqueId())||vanished.contains(p.getUniqueId()))) event.setCancelled(true);
        }
    }
    private boolean restricted(Player p) { return frozen.containsKey(p.getUniqueId()) || vanished.contains(p.getUniqueId()); }
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e) { if(restricted(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void interactEntity(PlayerInteractEntityEvent e) { if(restricted(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void interactAt(PlayerInteractAtEntityEvent e) { if(restricted(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void breakBlock(BlockBreakEvent e) { if(restricted(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void placeBlock(BlockPlaceEvent e) { if(restricted(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void drop(PlayerDropItemEvent e) { if(restricted(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void pickup(EntityPickupItemEvent e) { if(e.getEntity() instanceof Player p && restricted(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e) {
        UUID id=e.getWhoClicked().getUniqueId();
        if(frozen.containsKey(id) || vanished.contains(id) || e.getView().getTopInventory()==inspections.get(id)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e) {
        UUID id=e.getWhoClicked().getUniqueId();
        if(frozen.containsKey(id) || vanished.contains(id) || e.getView().getTopInventory()==inspections.get(id)) e.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent e) { inspections.remove(e.getPlayer().getUniqueId(),e.getInventory()); }
    @EventHandler public void completions(PlayerCommandSendEvent e) {
        e.getCommands().removeIf(c->{String bare=c.substring(c.lastIndexOf(':')+1); return Rules.MINIMUM.containsKey(bare)&&!Rules.allowed(rank(e.getPlayer().getUniqueId()),bare);});
    }
    @Override public List<String> onTabComplete(CommandSender s,Command command,String alias,String[] args) {
        if(!(s instanceof Player)&&!console(s)) return List.of();
        String action=command.getName(); int index=args.length-1; List<String> candidates=new ArrayList<>();
        if(action.equals("rank")) {
            if(index==0) candidates.addAll(console(s)||Rules.allowed(rank(s),"rank")?List.of("set","remove","info","list"):List.of("info","list"));
            else if(index==2 && args[0].equalsIgnoreCase("set")) for(Rank r:Rank.values()) if(console(s)||rank(s).above(r)) candidates.add(r.name());
            else if(index!=1 || args[0].equalsIgnoreCase("list")) return List.of();
        } else if(action.equals("staff") && index==0) candidates.addAll(Rules.MINIMUM.keySet().stream().filter(c->console(s)||Rules.allowed(rank(s),c)).toList());
        else {
            if(action.equals("staff")) { action=args[0].toLowerCase(Locale.ROOT); index--; }
            if(index!=0 || (!console(s)&&!Rules.allowed(rank(s),action)) || Set.of("staffchat","vanish","gsp").contains(action)) return List.of();
        }
        if(candidates.isEmpty()) for(Player p:Bukkit.getOnlinePlayers()) if(console(s)||!(s instanceof Player viewer)||viewer.canSee(p)) candidates.add(p.getName());
        String partial=args[args.length-1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(partial)).sorted().toList();
    }
}
