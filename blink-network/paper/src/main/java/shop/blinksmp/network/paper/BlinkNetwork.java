package shop.blinksmp.network.paper;

import shop.blinksmp.network.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BlinkNetwork extends JavaPlugin implements Listener,CommandExecutor {
    private final UUID instance=UUID.randomUUID();
    private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
    private final Map<AsyncPlayerPreLoginEvent,Session> loginClaims=new ConcurrentHashMap<>();
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->new Thread(r,"BlinkNetwork-DB"));
    private final AtomicBoolean refreshing=new AtomicBoolean();
    private volatile Map<Region,String> regions=Map.of();
    private volatile Set<String> online=Set.of();
    private volatile long refreshed;
    private volatile boolean ready,active,stopping;
    private NetworkStore db;
    private String id,dimension,bootstrap;private World world;private boolean importing;
    private static final class Session {
        final UUID token=UUID.randomUUID();volatile byte[] data;
        volatile boolean frozen=true,loaded,saving,transferring,closed,joined;
        volatile UUID ticket;volatile long freezeAt=System.currentTimeMillis();
    }
    @Override public void onEnable(){
        saveDefaultConfig();Objects.requireNonNull(getCommand("blinknet")).setExecutor(this);
        if(!getConfig().getBoolean("enabled")){getLogger().info("Disabled in config; /blinknet status is available.");return;}
        active=true;getServer().getPluginManager().registerEvents(this,this);
        try {
            if(!Bukkit.getOnlinePlayers().isEmpty())throw new IllegalStateException("Start with no players; /reload is unsupported");
            if(!getConfig().getBoolean("proxy-security-configured"))throw new IllegalArgumentException("Configure Velocity modern forwarding and backend access first");
            id=getConfig().getString("server-id");dimension=Region.dimensionOf(id);bootstrap=getConfig().getString("bootstrap-server");Region.dimensionOf(bootstrap);
            importing=getConfig().getBoolean("import-local-players")&&id.equals(bootstrap);
            world=Objects.requireNonNull(Bukkit.getWorld(getConfig().getString("world")),"Configured world does not exist");
            World.Environment expected=switch(dimension){case "Nether"->World.Environment.NETHER;case "End"->World.Environment.THE_END;default->World.Environment.NORMAL;};
            if(world.getEnvironment()!=expected)throw new IllegalArgumentException("World environment does not match server ID");
            db=new NetworkStore(env("BLINK_NETWORK_JDBC_URL","database.jdbc-url"),env("BLINK_NETWORK_DB_USER","database.username"),env("BLINK_NETWORK_DB_PASSWORD","database.password"));
            getServer().getMessenger().registerOutgoingPluginChannel(this,Protocol.CHANNEL);
            io.execute(()->{try{db.verify();db.register(id);if(!db.heartbeat(id,instance))throw new IllegalStateException("Duplicate server ID is running");refresh();ready=true;}catch(Exception e){problem("Startup failed; joins blocked",e);}});
            Bukkit.getScheduler().runTaskTimer(this,()->{
                if(ready&&refreshing.compareAndSet(false,true))io.execute(()->{try{if(!db.heartbeat(id,instance))throw new IllegalStateException("Duplicate server ID");refresh();}catch(Exception e){ready=false;problem("Network unavailable; joins and transfers blocked",e);main(()->Bukkit.getOnlinePlayers().forEach(p->{Session s=sessions.get(p.getUniqueId());if(s!=null){s.frozen=true;p.kickPlayer("Network unavailable. Please reconnect later.");}}));}finally{refreshing.set(false);}});
                for(var row:sessions.entrySet()){Session s=row.getValue();if(!s.joined&&System.currentTimeMillis()-s.freezeAt>30_000&&sessions.remove(row.getKey(),s))releaseUnloaded(row.getKey(),s);}
                for(Player p:Bukkit.getOnlinePlayers()) {
                    Session s=sessions.get(p.getUniqueId());
                    if(s!=null&&s.frozen&&System.currentTimeMillis()-s.freezeAt>30_000)p.kickPlayer("Network loading timed out. Contact staff if reconnecting fails.");
                }
            },100,100);
            Bukkit.getScheduler().runTaskTimer(this,()->{if(ready)for(Player p:Bukkit.getOnlinePlayers()){Session s=sessions.get(p.getUniqueId());if(s!=null&&s.loaded&&!s.frozen&&!s.saving&&!s.transferring&&!p.isDead())checkpoint(p,s);}},600,600);
        }catch(Exception e){problem("Invalid setup; joins blocked until corrected and restarted",e);}
    }
    private String env(String key,String fallback){return System.getenv().getOrDefault(key,getConfig().getString(fallback,""));}
    private void refresh()throws Exception{
        Map<Region,String> next=db.assignments();Set<String> ok=new HashSet<>();for(String server:new HashSet<>(next.values()))if(db.ready(server))ok.add(server);
        regions=next;online=Set.copyOf(ok);refreshed=System.currentTimeMillis();
    }
    private void main(Runnable task){if(!stopping&&isEnabled())Bukkit.getScheduler().runTask(this,task);}
    private void problem(String context,Exception e){getLogger().severe(context+" ("+e.getClass().getSimpleName()+"). See setup/troubleshooting documentation; credentials are not logged.");}
    private boolean blocked(Player p){Session s=sessions.get(p.getUniqueId());return active&&(s==null||s.frozen);}
    @EventHandler(priority=EventPriority.LOWEST) public void prelogin(AsyncPlayerPreLoginEvent e){
        if(!active)return;
        if(!ready){e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,"Network is not ready. Please contact staff.");return;}
        Session s=new Session();
        if(sessions.putIfAbsent(e.getUniqueId(),s)!=null){e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,"Your previous session is still closing.");return;}
        loginClaims.put(e,s);
        try {
            s.data=db.claim(e.getUniqueId(),id,s.token,importing);
            if(s.data==null&&!importing)throw new IllegalStateException("No import authorization");
        } catch(Exception ex){sessions.remove(e.getUniqueId(),s);releaseUnloaded(e.getUniqueId(),s);e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,"Network data could not be loaded safely. Contact staff.");problem("Login rejected",ex);}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void preloginResult(AsyncPlayerPreLoginEvent e){
        Session s=loginClaims.remove(e);
        if(s!=null&&e.getLoginResult()!=AsyncPlayerPreLoginEvent.Result.ALLOWED&&sessions.remove(e.getUniqueId(),s))releaseUnloaded(e.getUniqueId(),s);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void loginResult(PlayerLoginEvent e){
        if(e.getResult()!=PlayerLoginEvent.Result.ALLOWED){Session s=sessions.remove(e.getPlayer().getUniqueId());if(s!=null)releaseUnloaded(e.getPlayer().getUniqueId(),s);}
    }
    private void releaseUnloaded(UUID player,Session s){io.execute(()->{try{if(s.data==null)db.abandonEmpty(player,s.token);else db.save(player,s.token,s.data,true);}catch(Exception ex){problem("Login lock retained; manual recovery may be required",ex);}});}
    @EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent e){
        if(!active)return;Player p=e.getPlayer();Session s=sessions.get(p.getUniqueId());
        if(!ready||s==null){p.kickPlayer("Network is not ready.");return;}
        s.joined=true;s.freezeAt=System.currentTimeMillis();
        main(()->{
            if(!p.isOnline()||sessions.get(p.getUniqueId())!=s)return;
            try {
                if(p.isDead())p.spigot().respawn();
                if(s.data==null){
                    if(p.getWorld()!=world)throw new IllegalStateException("Import player is outside the assigned dimension");
                    Region r=Region.at(dimension,p.getX(),p.getZ());if(!id.equals(regions.get(r)))throw new IllegalStateException("Import spawn region must be assigned to this server");
                    s.data=PlayerSnapshot.capture(p,dimension,p.getLocation()).bytes();s.loaded=true;checkpointInitial(p,s);return;
                }
                PlayerSnapshot snapshot=PlayerSnapshot.decode(s.data);
                if(!dimension.equals(snapshot.dimension()))throw new IllegalStateException("Snapshot dimension differs from destination");
                Location dest=snapshot.location(world);
                if(!id.equals(regions.get(Region.at(dimension,dest.getX(),dest.getZ()))))throw new IllegalStateException("Destination region is not owned by this server");
                world.getChunkAtAsync(dest,true).thenAccept(chunk->main(()->{
                    if(!p.isOnline()||sessions.get(p.getUniqueId())!=s)return;
                    try {
                        if(!safe(dest))throw new IllegalStateException("Destination is obstructed or outside world bounds");
                        // Own frozen teleport is permitted only around this synchronous call.
                        restoring.add(p.getUniqueId());boolean moved;
                        try{moved=p.teleport(dest,PlayerTeleportEvent.TeleportCause.PLUGIN);}finally{restoring.remove(p.getUniqueId());}
                        if(!moved)throw new IllegalStateException("Destination teleport was cancelled");
                        snapshot.apply(p);s.loaded=true;checkpointInitial(p,s);
                    }catch(Exception ex){problem("Restore failed; state preserved",ex);p.kickPlayer("Destination could not be restored safely. Contact staff.");}
                })).exceptionally(ex->{main(()->p.kickPlayer("Destination chunk could not load."));return null;});
            }catch(Exception ex){problem("Restore rejected; state preserved",ex);p.kickPlayer("Network state needs staff attention.");}
        });
    }
    private final Set<UUID> restoring=new HashSet<>();
    private boolean owned(Location l){return !active||(l.getWorld()==world&&id.equals(regions.get(Region.at(dimension,l.getX(),l.getZ()))));}
    private boolean safe(Location l){return l.getY()>=world.getMinHeight()&&l.getY()<world.getMaxHeight()-1&&world.getWorldBorder().isInside(l)&&l.getBlock().isPassable()&&l.clone().add(0,1,0).getBlock().isPassable()&&l.getBlock().getType()!=Material.LAVA&&l.getBlock().getType()!=Material.FIRE;}
    private void checkpointInitial(Player p,Session s){
        s.data=PlayerSnapshot.capture(p,dimension,p.getLocation()).bytes();byte[] bytes=s.data;
        io.execute(()->{try{if(!db.save(p.getUniqueId(),s.token,bytes,false))throw new IllegalStateException("Lost session lock");main(()->{if(p.isOnline()&&sessions.get(p.getUniqueId())==s){s.frozen=false;p.sendMessage("BlinkSMP network data loaded.");}});}catch(Exception ex){problem("Initial save failed",ex);main(()->p.kickPlayer("Network save failed. Your data is locked for recovery."));}});
    }
    private void checkpoint(Player p,Session s){
        try{s.data=PlayerSnapshot.capture(p,dimension,p.getLocation()).bytes();}catch(Exception e){return;}
        byte[] bytes=s.data;s.saving=true;
        io.execute(()->{try{if(!db.save(p.getUniqueId(),s.token,bytes,false))throw new IllegalStateException("Lost session lock");}catch(Exception ex){main(()->{s.frozen=true;s.freezeAt=System.currentTimeMillis();p.kickPlayer("Network save failed. Reconnect later.");});problem("Checkpoint failed",ex);}finally{s.saving=false;}});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e){
        Player p=e.getPlayer();if(!active||restoring.contains(p.getUniqueId()))return;
        if(blocked(p)){e.setCancelled(true);return;}
        if(e.getTo()==null)return;
        if(e.getTo().getWorld()!=world){e.setCancelled(true);p.sendActionBar("This destination needs a network dimension route.");return;}
        Region to=Region.at(dimension,e.getTo().getX(),e.getTo().getZ());String owner=regions.get(to);
        if(id.equals(owner))return;
        e.setCancelled(true);
        Session s=sessions.get(p.getUniqueId());
        if(s!=null&&!s.frozen&&!s.transferring) {
            if(owner==null||!ready||System.currentTimeMillis()-refreshed>15_000||!online.contains(owner)){p.sendActionBar("This region is unassigned or its server is unavailable.");return;}
            transfer(p,s,owner,dimension,e.getTo());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(PlayerPortalEvent e){teleport(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){
        if(!active||restoring.contains(e.getPlayer().getUniqueId()))return;
        if(blocked(e.getPlayer())){e.setCancelled(true);return;}
        Location to=e.getTo();if(to==null)return;
        String destDimension=switch(to.getWorld().getEnvironment()){case NETHER->"Nether";case THE_END->"End";default->"Overworld";};
        String owner=regions.get(Region.at(destDimension,to.getX(),to.getZ()));
        if(id.equals(owner)&&to.getWorld()==world)return;
        e.setCancelled(true);
        if(owner!=null&&online.contains(owner)&&ready&&System.currentTimeMillis()-refreshed<=15_000)transfer(e.getPlayer(),sessions.get(e.getPlayer().getUniqueId()),owner,destDimension,to);
        else e.getPlayer().sendMessage("That destination has no available network server.");
    }
    private void transfer(Player p,Session s,String target,String destDimension,Location dest){
        if(s==null||s.frozen||s.transferring||s.saving||p.isDead()||p.isInsideVehicle()||!p.getPassengers().isEmpty()||p.isSleeping())return;
        if(p.getOpenInventory().getTopInventory().getType()!=InventoryType.CRAFTING){p.sendActionBar("Close your inventory before crossing.");return;}
        if(!p.getItemOnCursor().isEmpty()||Arrays.stream(p.getOpenInventory().getTopInventory().getContents()).anyMatch(item->item!=null&&!item.isEmpty())){p.sendActionBar("Empty your cursor and crafting grid before crossing.");return;}
        try {
            s.frozen=true;s.freezeAt=System.currentTimeMillis();s.transferring=true;s.ticket=UUID.randomUUID();
            byte[] source=PlayerSnapshot.capture(p,dimension,p.getLocation()).bytes();
            byte[] destination=PlayerSnapshot.capture(p,destDimension,dest).bytes();s.data=source;
            UUID ticket=s.ticket;
            io.execute(()->{
                try {
                    if(!db.prepare(p.getUniqueId(),s.token,target,ticket,source,destination))throw new IllegalStateException("Destination unavailable or session fenced");
                    main(()->{if(p.isOnline()&&sessions.get(p.getUniqueId())==s){try{p.sendPluginMessage(this,Protocol.CHANNEL,new Protocol(p.getUniqueId(),ticket,target).bytes());}catch(Exception ex){problem("Proxy request failed",ex);}}});
                }catch(Exception ex){problem("Transfer preparation failed",ex);cancel(p,s,ticket);}
            });
            Bukkit.getScheduler().runTaskLater(this,()->cancel(p,s,ticket),300);
        }catch(Exception ex){s.frozen=false;s.transferring=false;problem("Transfer rejected",ex);}
    }
    private void cancel(Player p,Session s,UUID ticket){
        if(!ticket.equals(s.ticket))return;
        io.execute(()->{
            try {
                boolean released=db.cancel(p.getUniqueId(),s.token,ticket,!p.isOnline());
                if(!released){NetworkStore.Route route=db.route(p.getUniqueId());
                    if(route!=null&&route.state().equals("active")&&id.equals(route.owner())&&!s.closed){released=true;}
                }
                boolean owns=released;
                main(()->{if(!ticket.equals(s.ticket))return;s.ticket=null;s.transferring=false;
                    if(p.isOnline()&&sessions.get(p.getUniqueId())==s){if(owns){s.frozen=false;p.sendMessage("Transfer did not complete; you remain on this server.");}else p.kickPlayer("Transfer state changed. Please reconnect.");}
                });
            }catch(Exception ex){problem("Transfer rollback uncertain; lock retained",ex);main(()->p.kickPlayer("Transfer could not be verified. Contact staff."));}
        });
    }
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e){
        Session s=sessions.remove(e.getPlayer().getUniqueId());if(s==null)return;s.closed=true;
        if(s.transferring)return; // Destination owns the prepared snapshot. Never overwrite it on quit.
        if(s.loaded)try{s.data=PlayerSnapshot.capture(e.getPlayer(),dimension,e.getPlayer().getLocation()).bytes();}catch(Exception ex){problem("Quit capture failed; retaining previous checkpoint",ex);}
        byte[] bytes=s.data;if(bytes!=null)io.execute(()->{try{db.save(e.getPlayer().getUniqueId(),s.token,bytes,true);}catch(Exception ex){problem("Quit save failed; lock retained",ex);}});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void click(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p&&blocked(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&blocked(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void drop(PlayerDropItemEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p&&blocked(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player p&&blocked(p))e.setCancelled(true);if(e instanceof EntityDamageByEntityEvent d&&d.getDamager() instanceof Player p&&blocked(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interact(PlayerInteractEvent e){if(blocked(e.getPlayer())||(e.getClickedBlock()!=null&&!owned(e.getClickedBlock().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interactEntity(PlayerInteractEntityEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breaking(BlockBreakEvent e){if(blocked(e.getPlayer())||!owned(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void placing(BlockPlaceEvent e){if(blocked(e.getPlayer())||!owned(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void swap(PlayerSwapHandItemsEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void consume(PlayerItemConsumeEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void command(PlayerCommandPreprocessEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void chat(AsyncChatEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("status")){sender.sendMessage("BlinkNetwork 0.1.0: "+(ready?"ready":active?"BLOCKED — check configuration/database":"disabled")+"; ID="+id+"; regions="+regions.size());return true;}
        if(!(sender instanceof ConsoleCommandSender)){sender.sendMessage("Setup commands are console-only.");return true;}
        if(db==null){sender.sendMessage("Configure the database and enabled: true, then restart first.");return true;}
        String[] a=args.clone();io.execute(()->{try{
            switch(a[0].toLowerCase(Locale.ROOT)){
                case "schema"->{db.initialize();}
                case "register"->{db.register(a[1]);}
                case "enable"->{db.enable(a[1],true);}
                case "disable"->{db.enable(a[1],false);}
                case "assign"->{db.assign(new Region(a[1],Integer.parseInt(a[2]),Integer.parseInt(a[3])),a[4]);}
                case "recover"->{if(a.length!=3||!a[2].equals("ALL_PLAYER_SERVER_PROCESSES_STOPPED"))throw new IllegalArgumentException("Stop all copies of the player's server process first");db.recover(UUID.fromString(a[1]));}
                default->throw new IllegalArgumentException("Unknown command");
            }main(()->sender.sendMessage("Operation completed. Restart after schema setup; assignments refresh within 5 seconds."));
        }catch(Exception ex){main(()->sender.sendMessage("Operation rejected. Usage: blinknet schema | register/enable/disable <ID> | assign <Overworld|Nether|End> <regionX> <regionZ> <ID> | recover <UUID> ALL_PLAYER_SERVER_PROCESSES_STOPPED"));problem("Console operation rejected",ex);}});return true;
    }
    @Override public void onDisable(){
        stopping=true;ready=false;
        // Capture synchronously, persist asynchronously, then wait a bounded time during shutdown.
        if(db!=null)for(Player p:Bukkit.getOnlinePlayers()){
            Session s=sessions.remove(p.getUniqueId());if(s==null||s.transferring)continue;
            if(s.loaded)try{s.data=PlayerSnapshot.capture(p,dimension,p.getLocation()).bytes();}catch(Exception ignored){}
            byte[] data=s.data;if(data!=null)io.execute(()->{try{db.save(p.getUniqueId(),s.token,data,true);}catch(Exception ex){problem("Shutdown save failed; lock retained",ex);}});
        }
        io.shutdown();try{if(!io.awaitTermination(20,TimeUnit.SECONDS))getLogger().severe("Shutdown persistence timed out; retained locks may need recovery.");}catch(InterruptedException ex){Thread.currentThread().interrupt();}
        if(db!=null)db.close();
    }
}
