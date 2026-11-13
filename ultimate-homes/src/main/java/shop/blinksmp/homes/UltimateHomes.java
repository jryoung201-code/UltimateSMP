package shop.blinksmp.homes;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
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
import shop.blinksmp.homes.HomeStore.Home;

public final class UltimateHomes extends JavaPlugin implements Listener,TabCompleter {
    private enum View { LIST,ACTIONS,DELETE }
    private static final Set<Material> HAZARDS=EnumSet.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.CACTUS,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH,Material.WITHER_ROSE,Material.POINTED_DRIPSTONE);
    private static final class Menu implements InventoryHolder {
        final UUID owner;final View view;final int page;final List<Home> homes;final Home home;
        Inventory inventory;
        Menu(UUID owner,View view,int page,List<Home> homes,Home home) { this.owner=owner;this.view=view;this.page=page;this.homes=List.copyOf(homes);this.home=home; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class Trip {
        final Player player;final Home home;final Location origin,destination;final long due;final boolean remote;
        boolean loading;long loadDeadline;int lastSeconds=-1;
        Trip(Player p,Home h,Location destination,int delay,boolean remote) { player=p;home=h;origin=p.getLocation().clone();this.destination=destination.clone();this.remote=remote;due=System.nanoTime()+delay*1_000_000_000L; }
    }
    private HomeStore store;
    private final Map<UUID,Trip> trips=new HashMap<>();
    private BukkitTask timer;
    private int serverCap,delay;
    @Override public void onEnable() {
        saveDefaultConfig();serverCap=Math.max(1,Math.min(10000,getConfig().getInt("server-max-homes",1000)));
        delay=Math.max(0,Math.min(30,getConfig().getInt("teleport-warmup-seconds",3)));
        try { store=new HomeStore(getDataFolder().toPath().resolve("players")); }
        catch(IOException ex) { getLogger().severe("Cannot initialize home storage: "+ex);Bukkit.getPluginManager().disablePlugin(this);return; }
        for(String name:List.of("home","sethome","delhome","homecancel")) Objects.requireNonNull(getCommand(name)).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,this);timer=Bukkit.getScheduler().runTaskTimer(this,this::pulse,5,5);
        getLogger().info("UltimateHomes ready. Home limits use the highest granted maxhomes.<number> permission.");
    }
    public String exportNetworkModule(UUID player,String kind) throws IOException {
        if(!kind.equals("homes"))throw new IllegalArgumentException("Not a homes module");
        return store.exportNetworkModule(player);
    }
    public void importNetworkModule(UUID player,String kind,String yaml) throws IOException {
        if(!kind.equals("homes"))throw new IllegalArgumentException("Not a homes module");
        store.importNetworkModule(player,yaml);
        Trip pending=trips.get(player);
        if(pending!=null)cancel(player,"Home changed on another server; teleport cancelled.");
    }
    private void profileChanged(UUID player) {
        org.bukkit.plugin.Plugin network=Bukkit.getPluginManager().getPlugin("BlinkNetwork");
        if(network==null || !network.isEnabled())return;
        try{network.getClass().getMethod("profileChanged",UUID.class,String.class).invoke(network,player,"homes");}
        catch(ReflectiveOperationException ex){getLogger().warning("BlinkNetwork home sync unavailable: "+ex.getClass().getSimpleName());}
    }
    private org.bukkit.plugin.Plugin network() {
        org.bukkit.plugin.Plugin plugin=Bukkit.getPluginManager().getPlugin("BlinkNetwork");
        return plugin!=null && plugin.isEnabled()?plugin:null;
    }
    private World networkHomeWorld(String dimension) {
        var plugin=network();if(plugin==null)return null;
        try{return (World)plugin.getClass().getMethod("localHomeWorld",String.class).invoke(plugin,dimension);}
        catch(ReflectiveOperationException ex){return null;}
    }
    private boolean networkRemote(Home h) {
        var plugin=network();if(plugin==null)return false;
        try{return (Boolean)plugin.getClass().getMethod("isHomeRemote",String.class,double.class,double.class)
            .invoke(plugin,h.dimension(),h.x(),h.z());}
        catch(ReflectiveOperationException ex){return false;}
    }
    private boolean networkHomeTeleport(Player p,Home h) {
        var plugin=network();if(plugin==null)return false;
        try{return (Boolean)plugin.getClass().getMethod("networkHomeTeleport",Player.class,String.class,double.class,double.class,double.class,float.class,float.class)
            .invoke(plugin,p,h.dimension(),h.x(),h.y(),h.z(),h.yaw(),h.pitch());}
        catch(ReflectiveOperationException ex){getLogger().warning("Network home transfer failed: "+ex.getClass().getSimpleName());return false;}
    }
    private void say(CommandSender sender,String text) { sender.sendMessage(Component.text("[Homes] ",NamedTextColor.GOLD).append(Component.text(text,NamedTextColor.YELLOW))); }
    private int limit(Player p) {
        Map<String,Boolean> nodes=new HashMap<>();
        // Enumerate explicit/effective numeric nodes, then consult their resolved values to honor denials.
        for(var permission:p.getEffectivePermissions()) {
            String node=permission.getPermission().toLowerCase(Locale.ROOT);
            if(node.startsWith("maxhomes.")) nodes.put(node,p.hasPermission(node));
        }
        return HomeLimits.resolve(nodes,serverCap);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)) { say(sender,"Use home commands in-game.");return true; }
        if(!p.hasPermission("ultimatehomes.use")) { say(p,"You do not have permission.");return true; }
        try {
            if(command.getName().equals("homecancel")) {
                if(args.length!=0) { say(p,"Usage: /homecancel");return true; }
                if(!cancel(p.getUniqueId(),"Home teleport cancelled.")) say(p,"No home teleport is pending.");return true;
            }
            if(args.length>1) { say(p,"Use /"+label+" <name>.");return true; }
            switch(command.getName()) {
                case "home" -> {
                    if(args.length==0) list(p,0);
                    else { Home h=store.get(p.getUniqueId(),args[0]);if(h==null) say(p,"Home not found. Use /homes to view your homes.");else begin(p,h); }
                }
                case "sethome" -> save(p,args.length==0?"home":args[0]);
                case "delhome" -> {
                    if(args.length==0) { say(p,"Usage: /delhome <name>");return true; }
                    String name=HomeLimits.name(args[0]);
                    if(store.delete(p.getUniqueId(),name)) { profileChanged(p.getUniqueId());cancelIfHome(p,name);say(p,"Deleted home '"+name+"'."); }
                    else say(p,"Home not found.");
                }
            }
        } catch(IllegalArgumentException ex) { say(p,ex.getMessage()); }
        catch(IOException ex) { failure(p,ex); }
        return true;
    }
    private void failure(Player p,IOException error) { say(p,"Could not read/save your homes. Existing data was preserved; ask staff to check console.");getLogger().severe("Home data error for "+p.getUniqueId()+": "+error); }
    private void save(Player p,String rawName) throws IOException {
        if(p.isDead() || p.isSleeping() || p.isInsideVehicle()) throw new IllegalArgumentException("Be alive, awake and outside a vehicle to set a home.");
        String name=HomeLimits.name(rawName);Location l=p.getLocation();
        String homeDimension=switch(p.getWorld().getEnvironment()){case NETHER->"Nether";case THE_END->"End";default->"Overworld";};
        Home h=new Home(name,p.getWorld().getUID(),p.getWorld().getName(),homeDimension,l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch());
        store.put(p.getUniqueId(),h,limit(p));profileChanged(p.getUniqueId());cancelIfHome(p,name);
        say(p,"Saved home '"+name+"' ("+store.homes(p.getUniqueId()).size()+"/"+limit(p)+").");
    }
    private ItemStack icon(Material material,String title,String... lore) {
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.displayName(Component.text(title,NamedTextColor.YELLOW));
        meta.lore(Arrays.stream(lore).map(s->Component.text(s,NamedTextColor.GRAY)).toList());item.setItemMeta(meta);return item;
    }
    private void open(Player p,Menu m,String title,int size) {
        m.inventory=Bukkit.createInventory(m,size,Component.text(title));p.openInventory(m.inventory);
    }
    private void list(Player p,int requestedPage) throws IOException {
        List<Home> homes=new ArrayList<>(store.homes(p.getUniqueId()).values());int page=Math.max(0,Math.min(requestedPage,Math.max(0,(homes.size()-1)/45)));
        Menu m=new Menu(p.getUniqueId(),View.LIST,page,homes,null);open(p,m,"Your homes ("+homes.size()+"/"+limit(p)+")",54);
        for(int i=page*45;i<Math.min(homes.size(),(page+1)*45);i++) {
            Home h=homes.get(i);m.inventory.setItem(i-page*45,icon(Material.RED_BED,h.name(),h.worldName()+" | "+(int)h.x()+", "+(int)h.y()+", "+(int)h.z(),"Click to teleport or manage."));
        }
        if(page>0) m.inventory.setItem(45,icon(Material.ARROW,"Previous page"));
        m.inventory.setItem(47,icon(Material.LIME_CONCRETE,"Create home here","Creates home1, home2, etc. at your position.","Use /sethome <name> for a custom name."));
        m.inventory.setItem(49,icon(Material.PAPER,"Page "+(page+1)+" | Limit: "+limit(p),"Highest granted maxhomes.<number> permission.","/sethome <name> | /home <name> | /delhome <name>"));
        m.inventory.setItem(51,icon(Material.BARRIER,"Close"));
        if((page+1)*45<homes.size()) m.inventory.setItem(53,icon(Material.ARROW,"Next page"));
    }
    private void actions(Player p,Home h) throws IOException {
        checkSnapshot(p,h);Menu m=new Menu(p.getUniqueId(),View.ACTIONS,0,List.of(),h);open(p,m,"Home: "+h.name(),27);
        m.inventory.setItem(11,icon(Material.ENDER_PEARL,"Teleport to "+h.name(),"Stand still and avoid damage during warmup."));
        m.inventory.setItem(15,icon(Material.BARRIER,"Delete home","A confirmation screen opens first."));
        m.inventory.setItem(22,icon(Material.ARROW,"Back to homes"));
    }
    private void confirmDelete(Player p,Home h) throws IOException {
        checkSnapshot(p,h);Menu m=new Menu(p.getUniqueId(),View.DELETE,0,List.of(),h);open(p,m,"Delete: "+h.name()+"?",27);
        m.inventory.setItem(11,icon(Material.RED_CONCRETE,"Confirm delete: "+h.name(),"This deletes only this saved home."));
        m.inventory.setItem(15,icon(Material.LIME_CONCRETE,"Keep home"));
    }
    private void checkSnapshot(Player p,Home home) throws IOException {
        if(!home.equals(store.get(p.getUniqueId(),home.name()))) throw new IllegalArgumentException("This home changed or was deleted. Reopen /homes.");
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p) || !m.owner.equals(p.getUniqueId()) || event.getClick()!=ClickType.LEFT) return;
        int slot=event.getRawSlot();
        Bukkit.getScheduler().runTask(this,()-> {
            if(!isEnabled() || !p.isOnline() || p.getOpenInventory().getTopInventory()!=m.inventory || !p.hasPermission("ultimatehomes.use")) return;
            try {
                if(m.view==View.LIST) {
                    if(slot>=0 && slot<45 && m.page*45+slot<m.homes.size()) actions(p,m.homes.get(m.page*45+slot));
                    else if(slot==45 && m.page>0) list(p,m.page-1);
                    else if(slot==53 && (m.page+1)*45<m.homes.size()) list(p,m.page+1);
                    else if(slot==51) p.closeInventory();
                    else if(slot==47) {
                        SortedMap<String,Home> homes=store.homes(p.getUniqueId());int number=1;while(homes.containsKey("home"+number)) number++;
                        save(p,"home"+number);list(p,m.page);
                    }
                } else if(m.view==View.ACTIONS) {
                    if(slot==11) { checkSnapshot(p,m.home);p.closeInventory();begin(p,m.home); }
                    else if(slot==15) confirmDelete(p,m.home);
                    else if(slot==22) list(p,0);
                } else if(m.view==View.DELETE) {
                    if(slot==11) { checkSnapshot(p,m.home);store.delete(p.getUniqueId(),m.home.name());profileChanged(p.getUniqueId());cancelIfHome(p,m.home.name());say(p,"Deleted home '"+m.home.name()+"'.");list(p,0); }
                    else if(slot==15) actions(p,m.home);
                }
            } catch(IllegalArgumentException ex) { say(p,ex.getMessage()); }
            catch(IOException ex) { failure(p,ex); }
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event) { if(event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true); }
    private boolean available(Player p) { return p.isOnline() && !p.isDead() && !p.isSleeping() && !p.isInsideVehicle() && p.getPassengers().isEmpty() && p.hasPermission("ultimatehomes.use"); }
    private boolean moved(Trip t) { Location l=t.player.getLocation();return !l.getWorld().equals(t.origin.getWorld()) || l.distanceSquared(t.origin)>.0001; }
    private void begin(Player p,Home h) throws IOException {
        checkSnapshot(p,h);if(!available(p)) throw new IllegalArgumentException("Be alive, awake, outside vehicles and without passengers to teleport.");
        if(trips.containsKey(p.getUniqueId())) throw new IllegalArgumentException("A home teleport is already pending. Use /homecancel first.");
        World world=Bukkit.getWorld(h.world());
        World networkWorld=networkHomeWorld(h.dimension());
        if(networkWorld!=null)world=networkWorld;
        boolean remote=networkRemote(h);
        if(world==null && remote)world=p.getWorld(); // Placeholder only; remote destination is routed by BlinkNetwork.
        if(world==null)throw new IllegalArgumentException("That home's dimension has no network destination.");
        Location destination=new Location(world,h.x(),h.y(),h.z(),h.yaw(),h.pitch());Trip trip=new Trip(p,h,destination,delay,remote);trips.put(p.getUniqueId(),trip);
        say(p,"Teleporting to '"+h.name()+"' in "+delay+" seconds. Stay still; /homecancel cancels.");
        if(delay==0) load(trip);
    }
    private void cancelIfHome(Player p,String name) { Trip t=trips.get(p.getUniqueId());if(t!=null && t.home.name().equals(name)) cancel(p.getUniqueId(),"Home changed; pending teleport cancelled."); }
    private boolean cancel(UUID id,String reason) { Trip t=trips.remove(id);if(t==null) return false;if(t.player.isOnline()) { t.player.sendActionBar(Component.empty());say(t.player,reason); }return true; }
    private void pulse() {
        long now=System.nanoTime();for(Trip t:new ArrayList<>(trips.values())) {
            if(!available(t.player) || moved(t)) { cancel(t.player.getUniqueId(),"Teleport cancelled: you moved or became unavailable.");continue; }
            if(t.loading) { if(now-t.loadDeadline>=0) cancel(t.player.getUniqueId(),"Home chunk loading timed out. Please try again.");continue; }
            if(now-t.due>=0) { load(t);continue; }
            int seconds=(int)((t.due-now+999_999_999L)/1_000_000_000L);
            if(seconds!=t.lastSeconds) { t.lastSeconds=seconds;t.player.sendActionBar(Component.text("Home teleport in "+seconds+"... Stay still!",NamedTextColor.GOLD)); }
        }
    }
    private void load(Trip t) {
        if(trips.get(t.player.getUniqueId())!=t) return;
        if(t.remote){
            try {
                checkSnapshot(t.player,t.home);
                if(!available(t.player)||moved(t)) { cancel(t.player.getUniqueId(),"Home transfer cancelled.");return; }
                trips.remove(t.player.getUniqueId());t.player.sendActionBar(Component.empty());
                if(!networkHomeTeleport(t.player,t.home))say(t.player,"Home's network server is unavailable.");
            }catch(IOException ex){cancel(t.player.getUniqueId(),"Home data unavailable.");failure(t.player,ex);}
            return;
        }
        t.loading=true;t.loadDeadline=System.nanoTime()+30_000_000_000L;t.player.sendActionBar(Component.text("Loading home...",NamedTextColor.GOLD));
        World world=t.destination.getWorld();
        if(world==null || Bukkit.getWorld(world.getUID())!=world) { cancel(t.player.getUniqueId(),"The home's world is no longer available.");return; }
        if(t.destination.getY()<=world.getMinHeight() || t.destination.getY()+1.8>=world.getMaxHeight() || !world.getWorldBorder().isInside(t.destination)) { cancel(t.player.getUniqueId(),"Home is outside the world's safe height or border.");return; }
        Set<Long> chunks=new HashSet<>();List<CompletableFuture<Chunk>> futures=new ArrayList<>();
        try {
        for(double dx:new double[]{-.3,.3}) for(double dz:new double[]{-.3,.3}) {
            int cx=((int)Math.floor(t.destination.getX()+dx))>>4,cz=((int)Math.floor(t.destination.getZ()+dz))>>4;
            long key=((long)cx<<32)^(cz&0xffffffffL);if(chunks.add(key)) futures.add(world.getChunkAtAsync(cx,cz,false));
        }
        } catch(RuntimeException ex) { getLogger().warning("Home chunk request failed: "+ex);cancel(t.player.getUniqueId(),"Could not load the home. Please try again.");return; }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored,error)-> {
            if(!isEnabled()) return;
            try { Bukkit.getScheduler().runTask(this,()->finish(t,error)); }
            catch(org.bukkit.plugin.IllegalPluginAccessException ignoredDisable) {}
        });
    }
    private boolean safe(Location l) {
        World world=l.getWorld();if(world==null || Bukkit.getWorld(world.getUID())!=world) return false;
        int minY=l.getBlockY(),maxY=(int)Math.floor(l.getY()+1.8);
        if(minY<=world.getMinHeight() || maxY>=world.getMaxHeight()) return false;
        for(double dx:new double[]{-.3,.3}) for(double dz:new double[]{-.3,.3}) {
            int x=(int)Math.floor(l.getX()+dx),z=(int)Math.floor(l.getZ()+dz);
            if(!world.isChunkLoaded(x>>4,z>>4) || !world.getWorldBorder().isInside(new Location(world,l.getX()+dx,l.getY(),l.getZ()+dz))) return false;
            Block ground=world.getBlockAt(x,minY-1,z);if(!ground.getType().isSolid() || HAZARDS.contains(ground.getType())) return false;
            for(int y=minY;y<=maxY;y++) { Block b=world.getBlockAt(x,y,z);if(!b.isPassable() || b.isLiquid() || HAZARDS.contains(b.getType())) return false; }
        }
        return true;
    }
    private void finish(Trip t,Throwable error) {
        UUID id=t.player.getUniqueId();if(!isEnabled() || trips.get(id)!=t) return;
        if(error!=null) { getLogger().warning("Could not load home chunk for "+id+": "+error);cancel(id,"Could not load the home. Please try again.");return; }
        try {
            checkSnapshot(t.player,t.home);
            if(!available(t.player) || moved(t) || !safe(t.destination)) { cancel(id,"Teleport cancelled: unsafe destination, missing chunk, or you moved. Save the home on clear, solid ground.");return; }
            trips.remove(id);t.player.sendActionBar(Component.empty());
            if(t.player.teleport(t.destination,PlayerTeleportEvent.TeleportCause.PLUGIN)) say(t.player,"Teleported to '"+t.home.name()+"'.");
            else say(t.player,"Teleport blocked by the server or another plugin.");
        } catch(IOException ex) { cancel(id,"Teleport cancelled because home data could not be read.");failure(t.player,ex); }
        catch(RuntimeException ex) { if(!cancel(id,"Teleport failed. Please try again.") && t.player.isOnline()) say(t.player,"Teleport failed. Please try again.");getLogger().warning(ex.toString()); }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent event) {
        Trip t=trips.get(event.getPlayer().getUniqueId());if(t!=null && event.getTo()!=null && (!event.getTo().getWorld().equals(t.origin.getWorld()) || event.getTo().distanceSquared(t.origin)>.0001)) cancel(event.getPlayer().getUniqueId(),"Home teleport cancelled: you moved.");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageEvent event) { if(event.getEntity() instanceof Player p && event.getFinalDamage()>0) cancel(p.getUniqueId(),"Home teleport cancelled: you took damage."); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event) { cancel(event.getPlayer().getUniqueId(),"Home teleport cancelled: you teleported elsewhere."); }
    @EventHandler public void death(PlayerDeathEvent event) { cancel(event.getEntity().getUniqueId(),"Home teleport cancelled: you died."); }
    @EventHandler public void quit(PlayerQuitEvent event) { UUID id=event.getPlayer().getUniqueId();cancel(id,"Home teleport cancelled.");store.unload(id); }
    @Override public void onDisable() {
        if(timer!=null) timer.cancel();for(UUID id:new ArrayList<>(trips.keySet())) cancel(id,"Home teleport cancelled: server/plugin stopping.");
        for(Player p:Bukkit.getOnlinePlayers()) if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) { p.getOpenInventory().getTopInventory().clear();p.closeInventory(); }
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!(sender instanceof Player p) || !p.hasPermission("ultimatehomes.use") || args.length!=1 || command.getName().equals("homecancel")) return List.of();
        try { return store.homes(p.getUniqueId()).keySet().stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList(); }
        catch(IOException ex) { return List.of(); }
    }
}
