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
    private volatile Map<String,NetworkStore.Border> networkBorders=Map.of();
    private final Map<String,Long> appliedBorderRevisions=new HashMap<>();
    private final Map<String,World> worlds=new HashMap<>();
    private volatile boolean maintenance,queueAvailable;private boolean initialized;private boolean restartDispatch;private String queueServer;
    private static final String MAINTENANCE="This server is under maintenance. Please try again later.";
    private volatile Set<String> online=Set.of();
    private volatile long refreshed;
    private volatile boolean ready,active,stopping;
    private NetworkStore db;
    private ProfileSync profiles;
    private boolean seedProfiles;
    private String id,dimension,bootstrap;private World world;private boolean importing;
    private TerritoryBorderParticles particleBorders;
    private static final class Session {
        final UUID token=UUID.randomUUID();volatile byte[] data;
        volatile boolean frozen=true,loaded,saving,transferring,closed,joined,profileRefreshing;
        volatile UUID ticket;volatile String transferTarget;volatile long freezeAt=System.currentTimeMillis();
        Location lastOwned;boolean borderQueued,navigationQueued;long automaticAfter,navigationAfter;final SwitchReplayGuard switchRequests=new SwitchReplayGuard();
        volatile Map<String,NetworkStore.Module> incomingModules=Map.of();
        // Owned by the serialized I/O queue after the async prelogin claim.
        final Map<String,Long> profileVersions=new HashMap<>();
        final Map<String,byte[]> profileBaseline=new HashMap<>();
    }
    @Override public void onEnable(){
        saveDefaultConfig();
        if(getConfig().getString("role","world").equalsIgnoreCase("queue")){getServer().getPluginManager().registerEvents(new QueueLobby(this),this);return;}
        Objects.requireNonNull(getCommand("blinknet")).setExecutor(this);
        Objects.requireNonNull(getCommand("overworld")).setExecutor(this);Objects.requireNonNull(getCommand("blinkserver")).setExecutor(this);
        if(!getConfig().getBoolean("enabled")){getLogger().info("Disabled in config; /blinknet status is available.");return;}
        queueServer=getConfig().getString("queue-id",getConfig().getString("queue-server","BlinkSMP-Queue-01"));
        active=true;getServer().getPluginManager().registerEvents(this,this);
        try {
            if(!Bukkit.getOnlinePlayers().isEmpty())throw new IllegalStateException("Start with no players; /reload is unsupported");
            if(!getConfig().getBoolean("proxy-security-configured"))throw new IllegalArgumentException("Configure Velocity modern forwarding and backend access first");
            id=getConfig().getString("server-id");dimension=Region.dimensionOf(id);bootstrap=getConfig().getString("bootstrap-server");Region.dimensionOf(bootstrap);
            importing=getConfig().getBoolean("import-local-players")&&id.equals(bootstrap);
            world=Objects.requireNonNull(Bukkit.getWorld(getConfig().getString("world")),"Configured world does not exist");
            particleBorders=new TerritoryBorderParticles(getConfig());
            World.Environment expected=switch(dimension){case "Nether"->World.Environment.NETHER;case "End"->World.Environment.THE_END;default->World.Environment.NORMAL;};
            if(world.getEnvironment()!=expected)throw new IllegalArgumentException("World environment does not match server ID");
            worlds.put(dimension,world);
            if(getConfig().getBoolean("all-dimensions",true))for(String dim:List.of("Overworld","Nether","End")){
                World w=Bukkit.getWorld(getConfig().getString("worlds."+dim,switch(dim){case "Nether"->"world_nether";case "End"->"world_the_end";default->"world";}));
                if(w==null||!dim.equals(dimension(w)))throw new IllegalArgumentException("Missing or wrong dimension world: "+dim);
                worlds.put(dim,w);
            }
            db=new NetworkStore(env("BLINK_NETWORK_JDBC_URL","database.jdbc-url"),env("BLINK_NETWORK_DB_USER","database.username"),env("BLINK_NETWORK_DB_PASSWORD","database.password"));
            profiles=new ProfileSync(this);
            seedProfiles=profiles.active()&&id.equals(bootstrap)&&getConfig().getBoolean("profile-sync.seed-missing-from-local",false);
            getServer().getMessenger().registerOutgoingPluginChannel(this,Protocol.CHANNEL);
            getServer().getMessenger().registerIncomingPluginChannel(this,SwitchRequest.CHANNEL,(channel,player,data)->proxySwitch(player,data));
            io.execute(()->{try{db.verify();db.register(id);if(!db.heartbeat(id,instance))throw new IllegalStateException("Duplicate server ID is running");db.dimensions(id,worlds.size()==3);db.maintenance(id,false);initialized=true;refresh();ready=true;}catch(Exception e){problem("Startup failed; joins blocked",e);}});
            Bukkit.getScheduler().runTaskTimer(this,()->{
                if(refreshing.compareAndSet(false,true))io.execute(()->{
                    try {
                        if(!ready){db.verify();db.register(id);}
                        if(!db.heartbeat(id,instance))throw new IllegalStateException("Duplicate server ID is running");
                        if(!initialized){db.dimensions(id,worlds.size()==3);db.maintenance(id,false);initialized=true;}
                        refresh();
                        if(!ready)getLogger().info("BlinkNetwork reconnected and is ready.");
                        ready=true;
                    }catch(Exception e){
                        boolean wasReady=ready;ready=false;
                        problem(wasReady?"Network unavailable; joins and transfers blocked":"Network startup retry failed; will try again",e);
                        if(wasReady)main(()->Bukkit.getOnlinePlayers().forEach(p->{Session s=sessions.get(p.getUniqueId());if(s!=null){s.frozen=true;p.kickPlayer("Network unavailable. Please reconnect later.");}}));
                    }finally{refreshing.set(false);}
                });
                for(var row:sessions.entrySet()){Session s=row.getValue();if(!s.joined&&System.currentTimeMillis()-s.freezeAt>30_000&&sessions.remove(row.getKey(),s))releaseUnloaded(row.getKey(),s);}
                for(Player p:Bukkit.getOnlinePlayers()) {
                    Session s=sessions.get(p.getUniqueId());
                    if(s!=null&&s.frozen&&System.currentTimeMillis()-s.freezeAt>30_000)p.kickPlayer("Network loading timed out. Contact staff if reconnecting fails.");
                }
            },100,100);
            Bukkit.getScheduler().runTaskTimer(this,()->{if(ready)for(Player p:Bukkit.getOnlinePlayers()){Session s=sessions.get(p.getUniqueId());if(s!=null&&s.loaded&&!s.frozen&&!s.saving&&!s.transferring&&!s.profileRefreshing&&!p.isDead())checkpoint(p,s);}},600,600);
            Bukkit.getScheduler().runTaskTimer(this,()->{if(ready&&profiles.active())pollProfileChanges();if(ready&&!maintenance)publishRtp();},100,100);
            // Particle borders are private to nearby players and never change blocks.
            if(particleBorders.enabled()){
                int period=Math.max(5,Math.min(100,getConfig().getInt("particle-border.interval-ticks",10)));
                Bukkit.getScheduler().runTaskTimer(this,()->{
                    if(!ready)return;
                    Map<Region,String> current=regions;
                    for(Player player:Bukkit.getOnlinePlayers()){
                        Session session=sessions.get(player.getUniqueId());
                        if(session==null||!session.loaded||session.frozen||session.transferring||!worlds.containsValue(player.getWorld()))continue;
                        String dim=dimension(player.getWorld());
                        if(!id.equals(current.get(Region.at(dim,player.getX(),player.getZ()))))continue;
                        particleBorders.show(player,current,id,dim);
                    }
                },period,period);
            }
        }catch(Exception e){problem("Invalid setup; joins blocked until corrected and restarted",e);}
    }
    private String env(String key,String fallback){return System.getenv().getOrDefault(key,getConfig().getString(fallback,""));}
    private void refresh()throws Exception{
        queueAvailable=db.queueReady(queueServer);
        Map<Region,String> next=db.assignments();Set<String> ok=new HashSet<>();for(String server:new HashSet<>(next.values()))if(db.ready(server))ok.add(server);
        Map<String,NetworkStore.Border> incomingBorders=db.borders();
        regions=next;online=Set.copyOf(ok);networkBorders=incomingBorders;refreshed=System.currentTimeMillis();
        for(var row:incomingBorders.entrySet()){NetworkStore.Border border=row.getValue();if(worlds.containsKey(row.getKey())&&!Objects.equals(appliedBorderRevisions.get(row.getKey()),border.revision()))main(()->applyWorldBorder(border));}
    }
    private void applyWorldBorder(NetworkStore.Border border){
        if(!ready&&stopping)return;
        try {
            WorldBorder wb=worlds.get(border.dimension()).getWorldBorder();
            // Set the size before moving its center so the final placement is always valid.
            wb.setSize(border.enabled()?border.sizeBlocks():59_999_968);
            wb.setCenter(border.enabled()?border.centerX():0,border.enabled()?border.centerZ():0);
            appliedBorderRevisions.put(border.dimension(),border.revision());
        }catch(Exception ex){problem("Could not apply network world border",ex);}
    }
    private boolean withinBorder(String targetDimension,double x,double z){
        NetworkStore.Border border=networkBorders.get(targetDimension);
        return border==null||border.contains(x,z);
    }
    private void main(Runnable task){if(!stopping&&isEnabled())Bukkit.getScheduler().runTask(this,task);}
    private void problem(String context,Exception e){getLogger().severe(context+" ("+e.getClass().getSimpleName()+"). See setup/troubleshooting documentation; credentials are not logged.");}
    private boolean blocked(Player p){Session s=sessions.get(p.getUniqueId());return active&&(!ready||System.currentTimeMillis()-refreshed>15_000||s==null||s.frozen);}
    @EventHandler(priority=EventPriority.LOWEST) public void prelogin(AsyncPlayerPreLoginEvent e){
        if(!active||e.getLoginResult()!=AsyncPlayerPreLoginEvent.Result.ALLOWED)return;
        if(maintenance){e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,MAINTENANCE);return;}
        if(!ready){e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,"Network is not ready. Please contact staff.");return;}
        Session s=new Session();
        if(sessions.putIfAbsent(e.getUniqueId(),s)!=null){e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,"Your previous session is still closing.");return;}
        loginClaims.put(e,s);
        try {
            s.data=db.claim(e.getUniqueId(),id,s.token,importing);
            if(s.data==null&&!importing)throw new IllegalStateException("No import authorization");
            if(profiles.active()){
                s.incomingModules=db.modules(e.getUniqueId());
                for(var row:s.incomingModules.entrySet()){
                    s.profileVersions.put(row.getKey(),row.getValue().revision());
                    s.profileBaseline.put(row.getKey(),row.getValue().payload());
                }
                if(s.data!=null){
                    for(String kind:List.of("ranks","sanctions"))if(profiles.includes(kind)){
                        NetworkStore.Module module=s.incomingModules.get(kind);
                        if(module!=null || !seedProfiles)
                            profiles.importRank(e.getUniqueId(),kind,module==null?ProfileSync.empty(e.getUniqueId(),kind):ProfileSync.text(module.payload()));
                    }
                }
            }
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
                    if(!worlds.containsValue(p.getWorld()))throw new IllegalStateException("Import player is outside configured worlds");
                    Region r=Region.at(dimension(p.getWorld()),p.getX(),p.getZ());if(!id.equals(regions.get(r)))throw new IllegalStateException("Import spawn region must be assigned to this server");
                    s.data=PlayerSnapshot.capture(p,dimension(sourceLocation(p,s).getWorld()),sourceLocation(p,s)).bytes();s.loaded=true;checkpointInitial(p,s);return;
                }
                PlayerSnapshot snapshot=PlayerSnapshot.decode(s.data);
                World arrival=Objects.requireNonNull(worlds.get(snapshot.dimension()),"Destination world missing");
                Location requested=snapshot.location(arrival);
                TransferSafety.Point landing=TransferSafety.landing(regions,id,snapshot.dimension(),requested.getX(),requested.getZ(),networkBorders.get(snapshot.dimension()));
                Location dest=requested.clone();dest.setX(landing.x());dest.setZ(landing.z());
                arrival.getChunkAtAsync(dest,true).thenAccept(chunk->main(()->{
                    if(!p.isOnline()||sessions.get(p.getUniqueId())!=s)return;
                    try {
                        Location resolved=snapshot.yaml.getBoolean("portal-arrival",false)?safePortalLanding(dest):safeLanding(dest);
                        if(snapshot.yaml.getBoolean("rtp-arrival",false)&&!safeFloor(resolved))throw new IllegalStateException("RTP landing changed; retry from queue");
                        snapshot.yaml.set("rtp-arrival",null);snapshot.yaml.set("portal-arrival",null);
                        snapshot.relocate(resolved);
                        // Own frozen teleport is permitted only around this synchronous call.
                        restoring.add(p.getUniqueId());boolean moved;
                        try{moved=p.teleport(resolved,PlayerTeleportEvent.TeleportCause.PLUGIN);}finally{restoring.remove(p.getUniqueId());}
                        if(!moved)throw new IllegalStateException("Destination teleport was cancelled");
                        restoreProfiles(p,s,true);
                        snapshot.apply(p);
                        restoreProfiles(p,s,false);
                        s.lastOwned=resolved.clone();s.data=snapshot.bytes();
                        s.loaded=true;checkpointInitial(p,s);
                    }catch(Exception ex){problem("Restore failed; state preserved",ex);p.kickPlayer("Destination could not be restored safely. Contact staff.");}
                })).exceptionally(ex->{main(()->p.kickPlayer("Destination chunk could not load."));return null;});
            }catch(Exception ex){problem("Restore rejected; state preserved",ex);p.kickPlayer("Network state needs staff attention.");}
        });
    }
    private final Set<UUID> restoring=new HashSet<>();
    private static String dimension(World w){return switch(w.getEnvironment()){case NETHER->"Nether";case THE_END->"End";default->"Overworld";};}
    private boolean owned(Location l){return !active||(worlds.containsValue(l.getWorld())&&withinBorder(dimension(l.getWorld()),l.getX(),l.getZ())&&id.equals(regions.get(Region.at(dimension(l.getWorld()),l.getX(),l.getZ()))));}
    private boolean safe(Location l){World w=l.getWorld();return l.getY()>=w.getMinHeight()&&l.getY()<w.getMaxHeight()-1&&w.getWorldBorder().isInside(l)&&l.getBlock().isPassable()&&l.clone().add(0,1,0).getBlock().isPassable()&&!hazard(l.getBlock().getType())&&!hazard(l.clone().add(0,1,0).getBlock().getType());}
    private static boolean hazard(Material m){return m==Material.LAVA||m==Material.WATER||m==Material.FIRE||m==Material.SOUL_FIRE||m==Material.CACTUS||m==Material.MAGMA_BLOCK||m==Material.CAMPFIRE||m==Material.SOUL_CAMPFIRE||m==Material.POWDER_SNOW||m==Material.SWEET_BERRY_BUSH||m==Material.WITHER_ROSE;}
    private boolean safeFloor(Location l){if(!owned(l)||!safe(l))return false;Material floor=l.clone().subtract(0,1,0).getBlock().getType();return floor.isSolid()&&!hazard(floor)&&(l.getWorld().getEnvironment()!=World.Environment.NETHER||l.getY()<126);}
    private void restoreProfiles(Player p,Session s,boolean vanilla) throws Exception {
        if(profiles==null||!profiles.active())return;
        // A bootstrap import retains the local plugin files. Existing network records are authoritative.
        if(s.incomingModules.isEmpty() && s.data==null)return;
        for(String kind:List.of("advancements","statistics","homes","settings")){
            boolean isVanilla=kind.equals("advancements")||kind.equals("statistics");
            if(isVanilla!=vanilla||!profiles.includes(kind))continue;
            NetworkStore.Module module=s.incomingModules.get(kind);
            if(module==null&&seedProfiles)continue;
            String payload=module==null?ProfileSync.empty(p.getUniqueId(),kind):ProfileSync.text(module.payload());
            profiles.apply(p,kind,payload);
        }
    }
    private Map<String,byte[]> captureProfiles(Player p) throws Exception {
        if(profiles==null||!profiles.active())return Map.of();
        Map<String,byte[]> result=new LinkedHashMap<>();
        for(String kind:List.of("ranks","sanctions","homes","settings","statistics","advancements"))
            if(profiles.includes(kind))result.put(kind,ProfileSync.bytes(profiles.capture(p.getUniqueId(),kind)));
        return Map.copyOf(result);
    }
    /** While a live remote rank/sanction is being applied, never persist a stale local copy on disconnect. */
    private static Map<String,byte[]> safeDisconnectModules(Session s,Map<String,byte[]> captured){
        if(!s.profileRefreshing)return captured;
        Map<String,byte[]> safe=new LinkedHashMap<>(captured);
        safe.remove("ranks");safe.remove("sanctions");
        return Map.copyOf(safe);
    }
    private void persistProfiles(UUID player,Session s,Map<String,byte[]> updated) throws Exception {
        for(var row:updated.entrySet()) {
            byte[] previous=s.profileBaseline.get(row.getKey());
            if(Arrays.equals(previous,row.getValue()))continue;
            long next=db.writeModule(player,row.getKey(),s.profileVersions.getOrDefault(row.getKey(),0L),row.getValue());
            s.profileVersions.put(row.getKey(),next);
            s.profileBaseline.put(row.getKey(),row.getValue());
        }
    }
    /** Called on the primary server thread by the opt-in Ultimate plugins after an edit. */
    public void profileChanged(UUID player,String kind) {
        if(profiles==null||!profiles.includes(kind)||!ready||stopping)return;
        if(!Bukkit.isPrimaryThread()){main(()->profileChanged(player,kind));return;}
        try {
            byte[] payload=ProfileSync.bytes(profiles.capture(player,kind));
            Session s=sessions.get(player);
            io.execute(()->{
                try {
                    if(s!=null)persistProfiles(player,s,Map.of(kind,payload));
                    else {
                        Map<String,NetworkStore.Module> current=db.modules(player);
                        NetworkStore.Module old=current.get(kind);
                        if(old==null||!Arrays.equals(old.payload(),payload))
                            db.writeModule(player,kind,old==null?0:old.revision(),payload);
                    }
                }catch(Exception ex){problem("Player profile edit could not sync; inspect affected player",ex);
                    if(s!=null)main(()->{Player p=Bukkit.getPlayer(player);if(p!=null){s.frozen=true;p.kickPlayer("Player profile sync failed. Contact staff.");}});
                }
            });
        } catch(Exception ex){problem("Player profile capture rejected",ex);}
    }
    public World localHomeWorld(String targetDimension){
        return active&&ready?worlds.get(targetDimension):null;
    }
    public boolean isHomeRemote(String targetDimension,double x,double z){
        if(!active||!ready||!Double.isFinite(x)||!Double.isFinite(z))return false;
        String owner=regions.get(Region.at(targetDimension,x,z));
        return owner!=null&&!id.equals(owner);
    }
    public boolean networkHomeTeleport(Player p,String targetDimension,double x,double y,double z,float yaw,float pitch){
        if(!Bukkit.isPrimaryThread()||!active||!ready||blocked(p)||!Float.isFinite(yaw)||!Float.isFinite(pitch))return false;
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return false;
        String owner;
        try{owner=regions.get(Region.at(targetDimension,x,z));}
        catch(IllegalArgumentException ex){return false;}
        if(!withinBorder(targetDimension,x,z))return false;
        String issue=destinationIssue(owner);if(issue!=null){p.sendMessage(issue);return false;}
        World local=worlds.get(targetDimension);
        if(owner.equals(id)){if(local==null)return false;return p.teleport(new Location(local,x,y,z,yaw,pitch),PlayerTeleportEvent.TeleportCause.PLUGIN);}
        Session s=sessions.get(p.getUniqueId());
        if(s==null||s.frozen||s.transferring)return false;
        transfer(p,s,owner,targetDimension,new Location(worlds.getOrDefault(targetDimension,world),x,y,z,yaw,pitch));
        return s.transferring;
    }
    private void pollProfileChanges(){
        for(Player p:Bukkit.getOnlinePlayers()){
            UUID player=p.getUniqueId();Session s=sessions.get(player);
            if(s==null||!s.loaded||s.frozen||s.transferring||s.profileRefreshing)continue;
            io.execute(()->{
                try{
                    var remote=db.modules(player);
                    Map<String,NetworkStore.Module> changed=new LinkedHashMap<>();
                    for(String kind:List.of("ranks","sanctions")){
                        if(!profiles.includes(kind))continue;
                        NetworkStore.Module value=remote.get(kind);
                        if(value!=null&&value.revision()>s.profileVersions.getOrDefault(kind,0L))
                            changed.put(kind,value);
                    }
                    if(changed.isEmpty())return;
                    s.profileRefreshing=true;
                    main(()->{
                        if(!p.isOnline()||sessions.get(player)!=s||s.frozen||s.transferring){
                            s.profileRefreshing=false;return;
                        }
                        try{
                            for(var entry:changed.entrySet())
                                profiles.importRank(player,entry.getKey(),ProfileSync.text(entry.getValue().payload()));
                            // Release refreshing only after the I/O thread updates both CAS baselines.
                            io.execute(()->{
                                for(var entry:changed.entrySet()){
                                    s.profileVersions.put(entry.getKey(),entry.getValue().revision());
                                    s.profileBaseline.put(entry.getKey(),entry.getValue().payload());
                                }
                                s.profileRefreshing=false;
                            });
                        }catch(Exception ex){
                            problem("Live sanction/rank refresh failed",ex);
                            s.frozen=true;s.profileRefreshing=false;
                            p.kickPlayer("Network profile is unavailable.");
                        }
                    });
                }catch(Exception ex){problem("Profile refresh failed",ex);}
            });
        }
    }
    private void checkpointInitial(Player p,Session s){
        Map<String,byte[]> modules;
        try{modules=captureProfiles(p);}catch(Exception ex){problem("Initial profile capture failed",ex);p.kickPlayer("Network profile capture failed.");return;}
        s.data=PlayerSnapshot.capture(p,dimension(sourceLocation(p,s).getWorld()),sourceLocation(p,s)).bytes();byte[] bytes=s.data;
        io.execute(()->{try{persistProfiles(p.getUniqueId(),s,modules);if(!db.save(p.getUniqueId(),s.token,bytes,false))throw new IllegalStateException("Lost session lock");main(()->{if(p.isOnline()&&sessions.get(p.getUniqueId())==s){s.automaticAfter=System.currentTimeMillis()+TransferSafety.COOLDOWN_MILLIS;s.frozen=false;p.sendMessage("BlinkSMP network data loaded.");}});}catch(Exception ex){problem("Initial save failed",ex);main(()->p.kickPlayer("Network save failed. Your data is locked for recovery."));}});
    }
    private void checkpoint(Player p,Session s){
        try{s.data=PlayerSnapshot.capture(p,dimension(sourceLocation(p,s).getWorld()),sourceLocation(p,s)).bytes();}catch(Exception e){return;}
        byte[] bytes=s.data;Map<String,byte[]> modules;
        try{modules=captureProfiles(p);}catch(Exception ex){problem("Checkpoint profile capture failed",ex);return;}
        s.saving=true;
        io.execute(()->{try{persistProfiles(p.getUniqueId(),s,modules);if(!db.save(p.getUniqueId(),s.token,bytes,false))throw new IllegalStateException("Lost session lock");}catch(Exception ex){main(()->{s.frozen=true;s.freezeAt=System.currentTimeMillis();p.kickPlayer("Network save failed. Reconnect later.");});problem("Checkpoint failed",ex);}finally{s.saving=false;}});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e){
        Player p=e.getPlayer();if(!active||restoring.contains(p.getUniqueId()))return;
        if(blocked(p)){e.setCancelled(true);return;}
        if(e.getTo()==null)return;
        String dimension=dimension(e.getTo().getWorld());
        if(!worlds.containsValue(e.getTo().getWorld())){e.setCancelled(true);p.sendActionBar(MAINTENANCE);return;}
        if(!withinBorder(dimension,e.getTo().getX(),e.getTo().getZ())){e.setCancelled(true);p.sendActionBar("You reached the "+dimension+" world border.");return;}
        Region to=Region.at(dimension,e.getTo().getX(),e.getTo().getZ());String owner=regions.get(to);
        if(id.equals(owner))return;
        e.setCancelled(true);
        Session s=sessions.get(p.getUniqueId());
        if(s!=null&&!s.frozen&&!s.transferring) {
            String issue=destinationIssue(owner);if(issue!=null){p.sendActionBar(issue);return;}
            if(s.borderQueued||!TransferSafety.automaticAllowed(System.currentTimeMillis(),s.automaticAfter,e.getFrom().getX(),e.getFrom().getZ(),e.getTo().getX(),e.getTo().getZ()))return;
            Location from=e.getFrom().clone(),destination=e.getTo().clone();
            // A cancelled event rewinds to getFrom(). Wait for that rewind before freezing/snapshotting.
            s.borderQueued=true;
            main(()->{
                s.borderQueued=false;
                if(!p.isOnline()||sessions.get(p.getUniqueId())!=s||s.frozen||s.transferring)return;
                if(!owned(from)||p.getWorld()!=from.getWorld()||p.getLocation().distanceSquared(from)>4)return;
                transfer(p,s,owner,dimension,destination,from);
            });
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void rememberPosition(PlayerMoveEvent e){
        Session s=sessions.get(e.getPlayer().getUniqueId());
        if(s!=null&&s.loaded&&!s.frozen&&e.getTo()!=null&&owned(e.getTo()))s.lastOwned=e.getTo().clone();
    }
    private Location sourceLocation(Player p,Session s){
        Location current=p.getLocation();
        if(owned(current))return current;
        if(s.lastOwned!=null&&owned(s.lastOwned))return s.lastOwned.clone();
        throw new IllegalStateException("No committed position in owned territory");
    }
    private Location safeLanding(Location requested){
        // Remain inside the selected territory; never route to a neighbor while resolving a collision.
        for(int dy:new int[]{0,1,2,-1,3,-2,4,-3,5,-4}){
            Location candidate=requested.clone().add(0,dy,0);
            if(owned(candidate)&&safe(candidate))return candidate;
        }
        throw new IllegalStateException("No unobstructed landing inside destination territory");
    }
    private Location safePortalLanding(Location requested){
        // No pads overwrite builds: find a floor in already-loaded nearby territory or reject safely.
        for(int radius=0;radius<=8;radius+=2)for(int dx=-radius;dx<=radius;dx+=2)for(int dz=-radius;dz<=radius;dz+=2){
            if(radius>0&&Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            Location base=requested.clone().add(dx,0,dz);
            if(!base.getWorld().isChunkLoaded(base.getBlockX()>>4,base.getBlockZ()>>4))continue;
            for(int offset=0;offset<=96;offset++)for(int sign:new int[]{1,-1}){
                Location test=base.clone().add(0,offset*sign,0);
                if(safeFloor(test))return test;
            }
        }
        throw new IllegalStateException("No safe portal floor in owned loaded terrain");
    }
    private void retreat(Player p,Session s){
        if(s.lastOwned==null)return;
        try {
            Location old=s.lastOwned.clone();String dimension=dimension(old.getWorld());TransferSafety.Point inset=TransferSafety.landing(regions,id,dimension,old.getX(),old.getZ(),networkBorders.get(dimension));
            Location dest=old.clone();dest.setX(inset.x());dest.setZ(inset.z());
            if(!dest.getWorld().isChunkLoaded(dest.getBlockX()>>4,dest.getBlockZ()>>4))dest=old;
            if(!safe(dest))dest=old;
            if(!owned(dest)||!safe(dest))return;
            restoring.add(p.getUniqueId());try{if(p.teleport(dest,PlayerTeleportEvent.TeleportCause.PLUGIN))s.lastOwned=dest.clone();}finally{restoring.remove(p.getUniqueId());}
        }catch(Exception ex){problem("Could not retreat from failed border transfer",ex);}
    }
    private void rescue(CommandSender sender,String[] args){
        if(!ready||args.length!=5){sender.sendMessage("Usage: blinknet rescue <UUID> <x> <y> <z> (player must be offline)");return;}
        try {
            UUID player=UUID.fromString(args[1]);
            Location requested=new Location(world,Double.parseDouble(args[2]),Double.parseDouble(args[3]),Double.parseDouble(args[4]));
            if(!Double.isFinite(requested.getY()))throw new IllegalArgumentException("Non-finite height");
            TransferSafety.Point inset=TransferSafety.landing(regions,id,dimension,requested.getX(),requested.getZ(),networkBorders.get(dimension));
            requested.setX(inset.x());requested.setZ(inset.z());
            if(requested.getY()<world.getMinHeight()||requested.getY()>=world.getMaxHeight()-1)throw new IllegalArgumentException("Outside world height");
            world.getChunkAtAsync(requested,false).thenAccept(chunk->main(()->{
                if(chunk==null){sender.sendMessage("Rescue rejected: choose an already generated safe location.");return;}
                try {
                    Location verified=safeLanding(requested);
                    if(Bukkit.getPlayer(player)!=null){sender.sendMessage("Rescue rejected: player is still online.");return;}
                    io.execute(()->{try{db.rescueOffline(player,id,verified.getX(),verified.getY(),verified.getZ());main(()->sender.sendMessage("Rescued "+player+" to "+id+" at "+verified.getX()+", "+verified.getY()+", "+verified.getZ()+". Inventory and profiles preserved."));}
                        catch(Exception ex){problem("Offline rescue rejected",ex);main(()->sender.sendMessage("Rescue rejected. Player must have an OFFLINE saved state. An active/handoff lock is never bypassed; see recovery instructions."));}});
                }catch(Exception ex){sender.sendMessage("Rescue rejected: no clear landing in this server's territory.");}
            })).exceptionally(ex->{main(()->sender.sendMessage("Rescue rejected: destination chunk could not load."));return null;});
        }catch(Exception ex){sender.sendMessage("Rescue rejected: use a UUID and finite coordinates in this server's assigned territory.");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(PlayerPortalEvent e){if(dimensionPluginHandles(e))return;teleport(e);}
    /** API marker required by BlinkDimensions to prevent old-version double transfers. */
    public boolean delegatesPortalsToBlinkDimensions(){return false;}
    private boolean dimensionPluginHandles(PlayerPortalEvent e){return false;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){
        if(e instanceof PlayerPortalEvent portalEvent && dimensionPluginHandles(portalEvent))return;
        if(!active||restoring.contains(e.getPlayer().getUniqueId()))return;
        if(blocked(e.getPlayer())){e.setCancelled(true);return;}
        Location to=e.getTo();if(to==null)return;
        String destDimension=switch(to.getWorld().getEnvironment()){case NETHER->"Nether";case THE_END->"End";default->"Overworld";};
        if(!withinBorder(destDimension,to.getX(),to.getZ())){e.setCancelled(true);e.getPlayer().sendMessage("That destination is outside the "+destDimension+" world border.");return;}
        String owner=regions.get(Region.at(destDimension,to.getX(),to.getZ()));
        if(id.equals(owner)&&worlds.containsValue(to.getWorld())&&!maintenance)return;
        e.setCancelled(true);
        if(owner==null&&e instanceof PlayerPortalEvent&&destDimension.equals("Overworld")&&e.getFrom().getWorld().getEnvironment()==World.Environment.NETHER){
            e.getPlayer().sendActionBar("Portal exit is outside assigned territory. Finding a safe Overworld landing...");travelOverworld(e.getPlayer(),id,false);return;
        }
        String issue=destinationIssue(owner);
        if(issue==null)transfer(e.getPlayer(),sessions.get(e.getPlayer().getUniqueId()),owner,destDimension,to,null,e instanceof PlayerPortalEvent);
        else e.getPlayer().sendMessage(issue);
    }
    private void transfer(Player p,Session s,String target,String destDimension,Location dest){
        transfer(p,s,target,destDimension,dest,null);
    }
    private void transfer(Player p,Session s,String target,String destDimension,Location dest,Location committedFrom){transfer(p,s,target,destDimension,dest,committedFrom,false);}
    private void transfer(Player p,Session s,String target,String destDimension,Location dest,Location committedFrom,boolean portal){
        if(maintenance){p.sendMessage(MAINTENANCE);return;}
        if(s==null||s.frozen||s.transferring||s.saving||s.profileRefreshing||p.isDead()||p.isInsideVehicle()||!p.getPassengers().isEmpty()||p.isSleeping())return;
        if(p.getOpenInventory().getTopInventory().getType()!=InventoryType.CRAFTING){p.sendActionBar("Close your inventory before crossing.");return;}
        if(!p.getItemOnCursor().isEmpty()||Arrays.stream(p.getOpenInventory().getTopInventory().getContents()).anyMatch(item->item!=null&&!item.isEmpty())){p.sendActionBar("Empty your cursor and crafting grid before crossing.");return;}
        try {
            s.frozen=true;s.freezeAt=System.currentTimeMillis();s.transferring=true;s.ticket=UUID.randomUUID();s.transferTarget=target;
            Location rollback=committedFrom==null?sourceLocation(p,s):committedFrom.clone();
            if(!owned(rollback))throw new IllegalStateException("Rollback position is not owned by this server");
            s.lastOwned=rollback.clone();
            byte[] source=PlayerSnapshot.capture(p,dimension(rollback.getWorld()),rollback).bytes();
            PlayerSnapshot arriving=PlayerSnapshot.capture(p,destDimension,dest);if(portal)arriving.yaml.set("portal-arrival",true);
            byte[] destination=arriving.bytes();s.data=source;
            Map<String,byte[]> modules=captureProfiles(p);
            UUID ticket=s.ticket;
            io.execute(()->{
                try {
                    persistProfiles(p.getUniqueId(),s,modules);
                    if(!db.prepare(p.getUniqueId(),s.token,target,ticket,source,destination))throw new IllegalStateException("Destination unavailable or session fenced");
                    main(()->{if(p.isOnline()&&sessions.get(p.getUniqueId())==s){try{p.sendPluginMessage(this,Protocol.CHANNEL,new Protocol(p.getUniqueId(),ticket,target).bytes());}catch(Exception ex){problem("Proxy request failed",ex);}}});
                }catch(Exception ex){problem("Transfer preparation failed",ex);cancel(p,s,ticket);}
            });
            Bukkit.getScheduler().runTaskLater(this,()->cancel(p,s,ticket),300);
        }catch(Exception ex){s.frozen=false;s.transferring=false;problem("Transfer rejected",ex);}
    }
    private void cancel(Player p,Session s,UUID ticket){
        if(!Bukkit.isPrimaryThread()){main(()->cancel(p,s,ticket));return;}
        if(!ticket.equals(s.ticket))return;
        boolean connected=p.isOnline();
        io.execute(()->{
            try {
                boolean released=db.cancel(p.getUniqueId(),s.token,ticket,!connected);
                if(!released)released=db.save(p.getUniqueId(),s.token,s.data,!connected); // Exact-session fence, including failed preparation.

                // The target may have claimed the session before the source timeout fired.
                // Never kick the source or overwrite the authoritative destination snapshot.
                NetworkStore.Route route=released?null:db.route(p.getUniqueId());
                boolean destinationClaimed=route!=null&&route.destinationHasState(s.transferTarget);
                if(destinationClaimed){
                    getLogger().info("Destination "+s.transferTarget+" has claimed the transfer; source leaves its session untouched.");
                    main(()->{
                        if(!ticket.equals(s.ticket))return;
                        s.ticket=null;
                        s.freezeAt=System.currentTimeMillis();
                        // Still frozen and transferring until Velocity drops the source.
                        if(p.isOnline()&&sessions.get(p.getUniqueId())==s)
                            p.sendMessage("Destination received your data; completing transfer.");
                    });
                    return;
                }
                boolean owns=released;
                main(()->{if(!ticket.equals(s.ticket))return;s.ticket=null;s.transferring=false;s.transferTarget=null;
                    if(p.isOnline()&&sessions.get(p.getUniqueId())==s){if(owns){
                        retreat(p,s);s.automaticAfter=System.currentTimeMillis()+TransferSafety.COOLDOWN_MILLIS;
                        s.frozen=false;p.sendMessage("Transfer did not complete; you remain on this server.");
                    }else p.kickPlayer("Transfer could not be verified. Please reconnect.");}
                    else if(owns)io.execute(()->{try{db.save(p.getUniqueId(),s.token,s.data,true);}catch(Exception ex){problem("Disconnected handoff cleanup retained lock",ex);}});
                });
            }catch(Exception ex){problem("Transfer rollback uncertain; lock retained",ex);main(()->p.kickPlayer("Transfer could not be verified. Contact staff."));}
        });
    }
    @EventHandler(priority=EventPriority.MONITOR) public void death(PlayerDeathEvent e){
        if(!active)return;Player p=e.getEntity();Session s=sessions.get(p.getUniqueId());if(s==null||!s.loaded||s.transferring)return;
        main(()->{if(p.isOnline()&&sessions.get(p.getUniqueId())==s&&!s.frozen){
            try{PlayerSnapshot snapshot=PlayerSnapshot.capture(p,dimension(sourceLocation(p,s).getWorld()),sourceLocation(p,s));
                if(!e.getKeepLevel()){snapshot.yaml.set("level",e.getNewLevel());snapshot.yaml.set("exp",0.0);snapshot.yaml.set("total-exp",e.getNewTotalExp());}
                s.data=snapshot.bytes();byte[] bytes=s.data;
                io.execute(()->{try{if(!db.save(p.getUniqueId(),s.token,bytes,false))throw new IllegalStateException();}catch(Exception ex){problem("Death checkpoint failed",ex);main(()->p.kickPlayer("Network save failed. Contact staff before reconnecting."));}});
            }catch(Exception ex){problem("Death snapshot failed",ex);p.kickPlayer("Network state needs staff attention.");}
        }});
    }
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent e){
        Session s=sessions.remove(e.getPlayer().getUniqueId());if(s==null)return;s.closed=true;
        if(s.transferring)return; // Destination owns the prepared snapshot. Never overwrite it on quit.
        Map<String,byte[]> modules=Map.of();
        if(s.loaded)try{s.data=PlayerSnapshot.capture(e.getPlayer(),dimension(sourceLocation(e.getPlayer(),s).getWorld()),sourceLocation(e.getPlayer(),s)).bytes();modules=safeDisconnectModules(s,captureProfiles(e.getPlayer()));}catch(Exception ex){problem("Quit capture failed; retaining previous checkpoint",ex);}
        byte[] bytes=s.data;Map<String,byte[]> savedModules=modules;io.execute(()->{try{persistProfiles(e.getPlayer().getUniqueId(),s,savedModules);if(bytes==null)db.abandonEmpty(e.getPlayer().getUniqueId(),s.token);else db.save(e.getPlayer().getUniqueId(),s.token,bytes,true);}catch(Exception ex){problem("Quit save failed; lock retained",ex);}});
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
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hunger(FoodLevelChangeEvent e){if(e.getEntity() instanceof Player p&&blocked(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void held(PlayerItemHeldEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void swap(PlayerSwapHandItemsEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void consume(PlayerItemConsumeEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void command(PlayerCommandPreprocessEvent e){
        String c=e.getMessage().trim().toLowerCase(Locale.ROOT);
        if(active&&!restartDispatch&&(c.equals("/restart")||c.equals("/spigot:restart"))&&e.getPlayer().hasPermission("bukkit.command.restart")){e.setCancelled(true);scheduleRestart(10);return;}
        if(blocked(e.getPlayer()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void chat(AsyncChatEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}
    private String destinationIssue(String owner){return DestinationPolicy.issue(ready,System.currentTimeMillis()-refreshed<=15_000,maintenance,owner,owner!=null&&online.contains(owner));}
    private void proxySwitch(Player player,byte[] bytes){
        if(!Bukkit.isPrimaryThread()){main(()->proxySwitch(player,bytes));return;}
        Session session=sessions.get(player.getUniqueId());if(session==null||!session.loaded||blocked(player))return;
        try{
            SwitchRequest request=SwitchRequest.read(bytes,session.token,System.currentTimeMillis());
            if(!request.player().equals(player.getUniqueId())||!session.switchRequests.admit(request.nonce(),request.expiresAt(),System.currentTimeMillis()))return;
            if(!player.hasPermission("blinknetwork.travel")){player.sendMessage("You cannot switch world servers.");return;}
            travelOverworld(player,request.target(),true);
        }catch(Exception ignored){} // Unauthenticated or replayed client messages never initiate a transfer.
    }
    private void travelOverworld(Player player,String target,boolean exactServer){
        Session session=sessions.get(player.getUniqueId());
        if(!active||session==null||!session.loaded||blocked(player)){player.sendMessage("Your network data is still loading.");return;}
        if(session.navigationQueued||session.transferring||session.saving||System.currentTimeMillis()<session.navigationAfter){player.sendActionBar("Please wait a moment before travelling again.");return;}
        String issue=DestinationPolicy.issue(ready,System.currentTimeMillis()-refreshed<=15_000,maintenance,target,online.contains(target));
        if(issue!=null){player.sendMessage(issue);return;}
        session.navigationQueued=true;session.navigationAfter=System.currentTimeMillis()+3000;
        io.execute(()->{
            try{
                List<NetworkStore.Rtp> points=db.rtpPoints("");NetworkStore.Rtp selected=null;
                for(var point:points)if(point.server().equals(target)){selected=point;break;}
                if(selected==null&&!exactServer&&!points.isEmpty())selected=points.getFirst();
                NetworkStore.Rtp point=selected;
                main(()->{
                    session.navigationQueued=false;
                    if(!player.isOnline()||sessions.get(player.getUniqueId())!=session||blocked(player)||session.transferring)return;
                    if(point==null){player.sendMessage("No safe Overworld landing is available on that server yet. Try again shortly.");return;}
                    World normal=worlds.get("Overworld");Location dest=new Location(normal==null?world:normal,point.x(),point.y(),point.z(),player.getYaw(),player.getPitch());
                    if(point.server().equals(id)){
                        if(normal==null){player.sendMessage("This server has no configured Overworld.");return;}
                        normal.getChunkAtAsync(dest,true).thenAccept(chunk->main(()->{
                            if(!player.isOnline()||sessions.get(player.getUniqueId())!=session||blocked(player)||session.transferring)return;
                            try{Location safe=safePortalLanding(dest);if(!player.teleport(safe,PlayerTeleportEvent.TeleportCause.PLUGIN))player.sendMessage("Overworld travel was cancelled.");}
                            catch(Exception ex){player.sendMessage("That Overworld landing changed. Try again shortly.");}
                        }));
                    }else transfer(player,session,point.server(),"Overworld",dest,null,true);
                });
            }catch(Exception ex){problem("Overworld travel lookup failed",ex);main(()->{session.navigationQueued=false;player.sendMessage("Network travel lookup failed. Please try again shortly.");});}
        });
    }
    private boolean rtpLoading;
    private void publishRtp(){
        // At most one terrain sample per cycle; random assigned regions, never foreign chunks.
        if(rtpLoading)return;
        List<Region> candidates=new ArrayList<>();
        for(var row:regions.entrySet())if(row.getKey().dimension().equals("Overworld")&&id.equals(row.getValue())&&worlds.containsKey("Overworld"))candidates.add(row.getKey());
        if(candidates.isEmpty())return;
        Region r=candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));World w=worlds.get(r.dimension());
        int x=r.x()*144+8+ThreadLocalRandom.current().nextInt(128),z=r.z()*144+8+ThreadLocalRandom.current().nextInt(128);
        rtpLoading=true;
        w.getChunkAtAsync(x>>4,z>>4,true).thenAccept(chunk->main(()->{
            try {
                for(int y=w.getEnvironment()==World.Environment.NETHER?125:w.getMaxHeight()-2;y>w.getMinHeight();y--){
                    Location dest=new Location(w,x+0.5,y,z+0.5);
                    if(safeFloor(dest)){NetworkStore.Rtp point=new NetworkStore.Rtp(id,r.dimension(),dest.getX(),y,dest.getZ());io.execute(()->{try{db.publishRtp(point);}catch(Exception ex){problem("RTP sample publish failed",ex);}});break;}
                }
            }finally{rtpLoading=false;}
        })).exceptionally(ex->{rtpLoading=false;return null;});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void restartCommand(org.bukkit.event.server.ServerCommandEvent e){
        String c=e.getCommand().trim().toLowerCase(Locale.ROOT);
        if(active&&!restartDispatch&&(c.equals("restart")||c.equals("spigot:restart"))){e.setCancelled(true);scheduleRestart(10);}
    }
    private void scheduleRestart(int seconds){
        if(seconds<10||seconds>86400||maintenance||!ready)throw new IllegalArgumentException();
        maintenance=true;
        io.execute(()->{try{db.maintenance(id,true);main(()->{
            final int[] remaining={seconds};
            new org.bukkit.scheduler.BukkitRunnable(){public void run(){
                if(remaining[0]<=0){cancel();drainAndRestart();return;}
                if(remaining[0]<=10||remaining[0]%60==0)Bukkit.broadcastMessage("Server is restarting in "+remaining[0]+" seconds.");
                remaining[0]--;
            }}.runTaskTimer(this,0,20);
        });}catch(Exception ex){maintenance=false;problem("Restart schedule failed",ex);}});
    }
    private void drainAndRestart(){
        Bukkit.broadcastMessage("Restarting......");
        final int[] attempts={0};
        new org.bukkit.scheduler.BukkitRunnable(){public void run(){
            if(++attempts[0]>=120){cancel();getLogger().severe("Restart paused: virtual queue unavailable or player transfers unfinished. Fix the problem then use blinknet resume.");return;}
            if(!queueAvailable)return;
            boolean pending=false;
            for(Player p:Bukkit.getOnlinePlayers()){
                Session s=sessions.get(p.getUniqueId());
                if(s==null)continue;
                if(!s.transferring){pending=true;drain(p,s);}
                else pending=true;
            }
            if(!pending){cancel();restartDispatch=true;Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"restart");return;}
        }}.runTaskTimer(this,0,20);
    }
    private void drain(Player p,Session s){
        if(!s.loaded||s.saving||s.profileRefreshing||s.transferring)return;
        try {
            // Close containers before capture; cursor items return to inventory through Bukkit.
            p.closeInventory();
            if(!p.getItemOnCursor().isEmpty()){p.sendMessage("Please empty your cursor so the server can restart safely.");return;}
            Location from=sourceLocation(p,s);byte[] data=PlayerSnapshot.capture(p,dimension(from.getWorld()),from).bytes();
            Map<String,byte[]> modules=captureProfiles(p);UUID ticket=UUID.randomUUID();
            s.frozen=true;s.transferring=true;s.ticket=ticket;s.data=data;s.freezeAt=System.currentTimeMillis();
            io.execute(()->{try{
                persistProfiles(p.getUniqueId(),s,modules);
                if(!db.queue(p.getUniqueId(),s.token,id,ticket,data))throw new IllegalStateException("Lost queue session");
                main(()->{s.ticket=null;if(p.isOnline())try{p.sendPluginMessage(this,Protocol.CHANNEL,new Protocol(p.getUniqueId(),ticket,queueServer).bytes());}catch(java.io.IOException ex){problem("Queue packet failed; saved state retained",ex);}});
            }catch(Exception ex){problem("Queue save failed",ex);main(()->{s.transferring=false;s.frozen=false;s.ticket=null;});}});
        }catch(Exception ex){problem("Queue capture failed",ex);}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equalsIgnoreCase("overworld")||command.getName().equalsIgnoreCase("blinkserver")){
            if(!(sender instanceof Player player)){sender.sendMessage("Use this command in game.");return true;}
            if(!player.hasPermission("blinknetwork.travel")){sender.sendMessage("You cannot switch world servers.");return true;}
            if(command.getName().equalsIgnoreCase("overworld")){travelOverworld(player,id,false);return true;}
            if(args.length!=1){sender.sendMessage("Usage: /blinkserver <server-ID>");return true;}
            try{Region.dimensionOf(args[0]);travelOverworld(player,args[0],true);}catch(Exception ex){sender.sendMessage("Use a registered BlinkSMP gameplay server ID.");}return true;
        }
        if(args.length==0||args[0].equalsIgnoreCase("status")){sender.sendMessage("BlinkNetwork 0.4.1: "+(ready?"ready":active?"BLOCKED — check configuration/database":"disabled")+"; ID="+id+"; regions="+regions.size());return true;}
        if(!(sender instanceof ConsoleCommandSender)){sender.sendMessage("Setup commands are console-only.");return true;}
        if(db==null){sender.sendMessage("Configure the database and enabled: true, then restart first.");return true;}
        if(args[0].equalsIgnoreCase("restart")){try{scheduleRestart(args.length>1?Integer.parseInt(args[1]):10);sender.sendMessage("Restart scheduled; new arrivals blocked.");}catch(Exception ex){sender.sendMessage("Usage: blinknet restart <10..86400 seconds>");}return true;}
        if(args[0].equalsIgnoreCase("resume")){io.execute(()->{try{db.maintenance(id,false);maintenance=false;}catch(Exception ex){problem("Resume failed",ex);}});return true;}
        if(args[0].equalsIgnoreCase("rescue")){rescue(sender,args);return true;}
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
        }catch(Exception ex){main(()->sender.sendMessage("Operation rejected. Usage: blinknet rescue <UUID> <x> <y> <z> | schema | register/enable/disable <ID> | assign <Overworld|Nether|End> <regionX> <regionZ> <ID> | recover <UUID> ALL_PLAYER_SERVER_PROCESSES_STOPPED"));problem("Console operation rejected",ex);}});return true;
    }
    @Override public void onDisable(){
        stopping=true;ready=false;
        // Capture synchronously, persist asynchronously, then wait a bounded time during shutdown.
        if(db!=null)for(Player p:Bukkit.getOnlinePlayers()){
            Session s=sessions.remove(p.getUniqueId());if(s==null||s.transferring)continue;
            Map<String,byte[]> modules=Map.of();
            if(s.loaded)try{s.data=PlayerSnapshot.capture(p,dimension(sourceLocation(p,s).getWorld()),sourceLocation(p,s)).bytes();modules=safeDisconnectModules(s,captureProfiles(p));}catch(Exception ignored){}
            p.kickPlayer("BlinkSMP network is shutting down. Please reconnect shortly.");
            byte[] data=s.data;Map<String,byte[]> savedModules=modules;if(data!=null)io.execute(()->{try{persistProfiles(p.getUniqueId(),s,savedModules);db.save(p.getUniqueId(),s.token,data,true);}catch(Exception ex){problem("Shutdown save failed; lock retained",ex);}});
        }
        io.shutdown();try{if(!io.awaitTermination(20,TimeUnit.SECONDS))getLogger().severe("Shutdown persistence timed out; retained locks may need recovery.");}catch(InterruptedException ex){Thread.currentThread().interrupt();}
        if(db!=null)db.close();
    }
}

