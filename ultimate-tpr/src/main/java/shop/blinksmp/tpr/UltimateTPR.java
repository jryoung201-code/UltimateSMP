package shop.blinksmp.tpr;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class UltimateTPR extends JavaPlugin implements Listener,TabCompleter {
    private static final Set<Material> HAZARDS=EnumSet.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.CACTUS,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH,Material.WITHER_ROSE,Material.POINTED_DRIPSTONE);
    private static final class Warmup {
        final RequestBook.Request request;
        final Player mover,target;
        final Location origin,destination;
        final long due;
        int lastSeconds=-1;
        Warmup(RequestBook.Request request,Player mover,Player target,int seconds) {
            this.request=request;this.mover=mover;this.target=target;
            origin=mover.getLocation().clone();destination=target.getLocation().clone();due=System.nanoTime()+seconds*1_000_000_000L;
        }
    }
    private final RequestBook requests=new RequestBook();
    private final Map<UUID,Long> cooldowns=new HashMap<>();
    private final Map<UUID,Warmup> warming=new HashMap<>(); // both participants point to the same warmup
    private BukkitTask timer;
    private int timeout,cooldown,warmup,maxIncoming;
    private boolean crossWorld;
    private int pulses;
    @Override public void onEnable() {
        saveDefaultConfig();timeout=setting("request-timeout-seconds",30,5,120);cooldown=setting("request-cooldown-seconds",5,1,60);
        warmup=setting("warmup-seconds",3,0,30);maxIncoming=setting("max-incoming-requests",5,1,20);crossWorld=getConfig().getBoolean("allow-cross-world",true);
        for(String name:List.of("tpr","tprhere","tpraccept","tprcancel","tprdeny")) Objects.requireNonNull(getCommand(name)).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,this);timer=Bukkit.getScheduler().runTaskTimer(this,this::pulse,5,5);
        getLogger().info("UltimateTPR ready. Every teleport requires the recipient's acceptance.");
    }
    private int setting(String key,int value,int min,int max) { return Math.max(min,Math.min(max,getConfig().getInt(key,value))); }
    private void tell(CommandSender p,String message) { p.sendMessage(Component.text("[TPR] ",NamedTextColor.GOLD).append(Component.text(message,NamedTextColor.YELLOW))); }
    private void notifyBoth(Warmup w,String message) { if(w.mover.isOnline()) tell(w.mover,message);if(w.target.isOnline()) tell(w.target,message); }
    private boolean available(Player p) { return p.isOnline() && !p.isDead() && !p.isSleeping() && !p.isInsideVehicle() && p.getPassengers().isEmpty() && p.hasPermission("ultimatetpr.use"); }
    private boolean compatible(Player a,Player b) { return available(a) && available(b) && a.canSee(b) && b.canSee(a) && (crossWorld || a.getWorld().equals(b.getWorld())); }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)) { tell(sender,"Use teleport requests in-game.");return true; }
        if(!p.hasPermission("ultimatetpr.use")) { tell(p,"You do not have permission.");return true; }
        String action=command.getName().toLowerCase(Locale.ROOT);UUID id=p.getUniqueId();
        if(action.equals("tprcancel")) {
            if(args.length!=0) { tell(p,"Usage: /tprCancel");return true; }
            Warmup active=warming.get(id);RequestBook.Request outgoing=requests.cancel(id);
            if(active!=null) cancel(active,"Teleport cancelled.");
            if(outgoing!=null) { tell(p,"Your outgoing teleport request was cancelled.");Player other=Bukkit.getPlayer(outgoing.to());if(other!=null) tell(other,p.getName()+" cancelled their teleport request."); }
            if(active==null && outgoing==null) tell(p,"You have no outgoing request or teleport warmup. Use /tprDeny to decline an incoming request.");return true;
        }
        if(action.equals("tpraccept") || action.equals("tprdeny")) {
            if(args.length>1) { tell(p,"Usage: /"+label+" [player]");return true; }
            List<RequestBook.Request> incoming=requests.incoming(id,System.nanoTime());
            if(args.length==1) incoming=incoming.stream().filter(r->Optional.ofNullable(Bukkit.getPlayer(r.from())).map(Player::getName).orElse("").equalsIgnoreCase(args[0])).toList();
            if(incoming.isEmpty()) { tell(p,"No matching active request. It may have expired or been cancelled.");return true; }
            if(incoming.size()>1) { tell(p,"You have multiple requests. Use /"+label+" <player> to choose.");return true; }
            RequestBook.Request request=incoming.get(0);Player other=Bukkit.getPlayer(request.from());
            if(action.equals("tprdeny")) {
                requests.take(id,request.from(),System.nanoTime());tell(p,"Teleport request declined.");if(other!=null) tell(other,p.getName()+" declined your request.");return true;
            }
            if(other==null || !compatible(p,other)) { tell(p,"Both players must be online, visible, alive, awake and out of vehicles. Cross-world requests must be allowed.");return true; }
            if(warming.containsKey(id) || warming.containsKey(other.getUniqueId())) { tell(p,"One of you already has a teleport warming up. Use /tprCancel or wait.");return true; }
            Player mover=request.here()?p:other,target=request.here()?other:p;
            Warmup w=new Warmup(request,mover,target,warmup);
            if(!safe(w.destination)) { tell(p,"The destination is unsafe. Stand on safe, solid ground and request again.");return true; }
            if(requests.take(id,request.from(),System.nanoTime()).isEmpty()) { tell(p,"That request expired. Ask for another one.");return true; }
            requests.removeParticipant(id);requests.removeParticipant(other.getUniqueId());warming.put(id,w);warming.put(other.getUniqueId(),w);
            notifyBoth(w,"Accepted! "+mover.getName()+" will teleport to "+target.getName()+"'s accepted position in "+warmup+" seconds.");
            tell(mover,"Stay still and avoid damage. Either player can use /tprCancel.");
            if(warmup==0) finish(w);return true;
        }
        if(args.length!=1) { tell(p,"Usage: /"+label+" <player>");return true; }
        Player target=Bukkit.getPlayerExact(args[0]);
        if(target==null || target.equals(p) || !p.canSee(target) || !target.canSee(p)) { tell(p,"Choose another online, visible player. Include their full Bedrock name/prefix.");return true; }
        if(!compatible(p,target)) { tell(p,"Both players must be alive, awake, out of vehicles and allowed to teleport between these worlds.");return true; }
        if(warming.containsKey(id) || warming.containsKey(target.getUniqueId())) { tell(p,"One of you already has a teleport warming up. Use /tprCancel or wait.");return true; }
        long now=System.nanoTime();Long previous=cooldowns.get(id);
        if(previous!=null && now-previous<cooldown*1_000_000_000L) { tell(p,"Wait a few seconds before sending another request.");return true; }
        boolean here=action.equals("tprhere");
        try {
            RequestBook.Request replaced=requests.send(id,target.getUniqueId(),here,now,timeout*1_000_000_000L,maxIncoming);
            cooldowns.put(id,now);
            if(replaced!=null) { Player old=Bukkit.getPlayer(replaced.to());if(old!=null && !old.equals(target)) tell(old,p.getName()+" replaced their earlier request."); }
        } catch(IllegalArgumentException ex) { tell(p,ex.getMessage());return true; }
        tell(p,"Request sent to "+target.getName()+". Expires in "+timeout+" seconds. /tprCancel cancels it.");
        tell(target,here?p.getName()+" asks YOU to teleport to THEM.":p.getName()+" asks to teleport to YOU.");
        tell(target,"/tprAccept "+p.getName()+" accepts; /tprDeny "+p.getName()+" declines. Expires in "+timeout+" seconds.");
        target.sendMessage(Component.text("[ACCEPT] ",NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/tprAccept "+p.getName()))
            .append(Component.text("[DENY]",NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/tprDeny "+p.getName()))));
        return true;
    }
    private boolean safe(Location position) {
        World world=position.getWorld();if(world==null || Bukkit.getWorld(world.getUID())!=world) return false;
        int x=position.getBlockX(),y=position.getBlockY(),z=position.getBlockZ();
        if(y<=world.getMinHeight() || y+1>=world.getMaxHeight() || !world.isChunkLoaded(x>>4,z>>4) || !world.getWorldBorder().isInside(position)) return false;
        // Check the player's full width as well as their central block. No chunk is loaded by these checks.
        for(double dx:new double[]{-.3,.3}) for(double dz:new double[]{-.3,.3}) {
            int bx=(int)Math.floor(position.getX()+dx),bz=(int)Math.floor(position.getZ()+dz);
            if(!world.isChunkLoaded(bx>>4,bz>>4)) return false;
            Location corner=new Location(world,position.getX()+dx,position.getY(),position.getZ()+dz);
            if(!world.getWorldBorder().isInside(corner)) return false;
            Block floor=world.getBlockAt(bx,y-1,bz);
            if(!floor.getType().isSolid() || HAZARDS.contains(floor.getType())) return false;
            for(int by=y;by<=(int)Math.floor(position.getY()+1.8);by++) {
                Block space=world.getBlockAt(bx,by,bz);
                if(!space.isPassable() || space.isLiquid() || HAZARDS.contains(space.getType())) return false;
            }
        }
        return true;
    }
    private boolean moved(Warmup w) { Location current=w.mover.getLocation();return !current.getWorld().equals(w.origin.getWorld()) || current.distanceSquared(w.origin)>.0001; }
    private void pulse() {
        long now=System.nanoTime();
        if(++pulses%4==0) {
            for(RequestBook.Request request:requests.expire(now)) { Player p=Bukkit.getPlayer(request.from());if(p!=null) tell(p,"Your teleport request expired."); }
            cooldowns.entrySet().removeIf(e->now-e.getValue()>60_000_000_000L);
        }
        for(Warmup w:new HashSet<>(warming.values())) {
            if(!compatible(w.mover,w.target) || moved(w)) { cancel(w,"Teleport cancelled: a player became unavailable or the teleporting player moved.");continue; }
            if(now-w.due>=0) { finish(w);continue; }
            int seconds=(int)((w.due-now+999_999_999L)/1_000_000_000L);
            if(seconds!=w.lastSeconds) { w.lastSeconds=seconds;w.mover.sendActionBar(Component.text("Teleporting in "+seconds+"... Stay still!",NamedTextColor.GOLD)); }
        }
    }
    private void unlink(Warmup w) { warming.remove(w.mover.getUniqueId(),w);warming.remove(w.target.getUniqueId(),w);if(w.mover.isOnline()) w.mover.sendActionBar(Component.empty()); }
    private void cancel(Warmup w,String reason) { if(w!=null && warming.get(w.mover.getUniqueId())==w) { unlink(w);notifyBoth(w,reason); } }
    private void finish(Warmup w) {
        if(warming.get(w.mover.getUniqueId())!=w) return;
        if(!compatible(w.mover,w.target) || moved(w) || !safe(w.destination)) { cancel(w,"Teleport cancelled: the destination or players are no longer safe/available.");return; }
        unlink(w); // Remove before the normal teleport event so it does not cancel itself.
        try {
            if(w.mover.teleport(w.destination,PlayerTeleportEvent.TeleportCause.PLUGIN)) notifyBoth(w,"Teleport complete!");
            else notifyBoth(w,"Teleport was blocked by the server or another plugin.");
        } catch(RuntimeException ex) { notifyBoth(w,"Teleport failed. Try again from a safe location.");getLogger().warning("Teleport failed for "+w.mover.getUniqueId()+": "+ex); }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent event) {
        Warmup w=warming.get(event.getPlayer().getUniqueId());
        if(w!=null && w.mover.equals(event.getPlayer()) && event.getTo()!=null && (!event.getTo().getWorld().equals(w.origin.getWorld()) || event.getTo().distanceSquared(w.origin)>.0001)) cancel(w,"Teleport cancelled: the teleporting player moved.");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageEvent event) {
        if(event.getFinalDamage()>0 && event.getEntity() instanceof Player p) { Warmup w=warming.get(p.getUniqueId());if(w!=null && w.mover.equals(p)) cancel(w,"Teleport cancelled: the teleporting player took damage."); }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event) { cancel(warming.get(event.getPlayer().getUniqueId()),"Teleport cancelled: a participant teleported elsewhere."); }
    @EventHandler public void death(PlayerDeathEvent event) { UUID id=event.getEntity().getUniqueId();cancel(warming.get(id),"Teleport cancelled: a participant died.");requests.removeParticipant(id); }
    @EventHandler public void quit(PlayerQuitEvent event) { UUID id=event.getPlayer().getUniqueId();cancel(warming.get(id),"Teleport cancelled: a participant disconnected.");requests.removeParticipant(id);cooldowns.remove(id); }
    @Override public void onDisable() { if(timer!=null) timer.cancel();for(Warmup w:new HashSet<>(warming.values())) cancel(w,"Teleport cancelled: server/plugin stopping.");requests.clear();cooldowns.clear(); }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!(sender instanceof Player p) || !p.hasPermission("ultimatetpr.use") || args.length!=1 || command.getName().equals("tprcancel")) return List.of();
        List<String> names=new ArrayList<>();
        if(command.getName().equals("tpraccept") || command.getName().equals("tprdeny")) {
            for(RequestBook.Request r:requests.incoming(p.getUniqueId(),System.nanoTime())) { Player other=Bukkit.getPlayer(r.from());if(other!=null && p.canSee(other)) names.add(other.getName()); }
        } else for(Player other:Bukkit.getOnlinePlayers()) if(!p.equals(other) && p.canSee(other) && other.canSee(p)) names.add(other.getName());
        return names.stream().filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).distinct().sorted().toList();
    }
}
