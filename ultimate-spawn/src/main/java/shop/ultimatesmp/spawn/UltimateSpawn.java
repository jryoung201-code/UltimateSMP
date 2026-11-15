package shop.ultimatesmp.spawn;
import com.zaxxer.hikari.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.*;import org.bukkit.command.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.entity.EntityDamageEvent;import org.bukkit.event.player.*;import org.bukkit.plugin.java.JavaPlugin;
public final class UltimateSpawn extends JavaPlugin implements Listener {
 private JavaPlugin network;private Method ready,travel;private World spawnWorld;private String id,host,worldName,url,user,password;
 private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"UltimateSpawn-DB");t.setDaemon(true);return t;});private final AtomicBoolean initializing=new AtomicBoolean();private volatile boolean stopping;private HikariDataSource pool;private volatile SpawnStore store;
 private final Map<UUID,UUID> loading=new HashMap<>();private final Map<UUID,Warmup> warming=new HashMap<>();private final Map<UUID,Long> cooldown=new HashMap<>();private int warmupSeconds,cooldownSeconds;
 private record Warmup(Player player,Location origin,SpawnPoint point,long due){}
 @Override public void onEnable(){saveDefaultConfig();network=(JavaPlugin)Bukkit.getPluginManager().getPlugin("BlinkNetwork");try{
  ready=network.getClass().getMethod("isPlayerReady",Player.class);travel=network.getClass().getMethod("networkWorldTeleport",Player.class,String.class,String.class,double.class,double.class,double.class,float.class,float.class);
  id=network.getConfig().getString("server-id","");host=getConfig().getString("spawn-server","").trim();if(host.isBlank())host=network.getConfig().getString("bootstrap-server","");worldName=getConfig().getString("world-name","ultimate_spawn");new SpawnPoint(host,worldName,0,70,0,0,0);
  url=env("BLINK_NETWORK_JDBC_URL","database.jdbc-url").replaceFirst("^jdbc:mysql:","jdbc:mariadb:");user=env("BLINK_NETWORK_DB_USER","database.username");password=env("BLINK_NETWORK_DB_PASSWORD","database.password");if(!url.startsWith("jdbc:mariadb:"))throw new IllegalArgumentException("MySQL/MariaDB database required");
  warmupSeconds=Math.max(0,Math.min(30,getConfig().getInt("warmup-seconds",3)));cooldownSeconds=Math.max(1,Math.min(60,getConfig().getInt("cooldown-seconds",5)));
  if(id.equals(host)){
   if(network.getConfig().getConfigurationSection("worlds")!=null&&network.getConfig().getConfigurationSection("worlds").getValues(false).containsValue(worldName))throw new IllegalArgumentException("Spawn must be a separate world");
   spawnWorld=new WorldCreator(worldName).environment(World.Environment.NORMAL).type(WorldType.FLAT).generateStructures(false).createWorld();if(spawnWorld==null)throw new IllegalStateException("Spawn world could not load");
   spawnWorld.setPVP(false);spawnWorld.setTime(6000);spawnWorld.setStorm(false);spawnWorld.setThundering(false);spawnWorld.setGameRule(GameRule.DO_MOB_SPAWNING,false);spawnWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);spawnWorld.setGameRule(GameRule.DO_WEATHER_CYCLE,false);spawnWorld.setGameRule(GameRule.KEEP_INVENTORY,true);spawnWorld.setGameRule(GameRule.RANDOM_TICK_SPEED,0);spawnWorld.setViewDistance(4);spawnWorld.setSimulationDistance(2);
   double size=Math.max(32,Math.min(4096,getConfig().getDouble("world-border-size",256)));spawnWorld.getWorldBorder().setCenter(0,0);spawnWorld.getWorldBorder().setSize(size);
   network.getClass().getMethod("registerAuxiliaryWorld",org.bukkit.plugin.Plugin.class,World.class).invoke(network,this,spawnWorld);
  }
  Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getPluginManager().registerEvents(new SpawnProtection(w->w==spawnWorld),this);
  Bukkit.getScheduler().runTaskTimer(this,this::pulse,5,5);Bukkit.getScheduler().runTaskTimer(this,this::initialize,1,200);getLogger().info("Spawn host: "+host+"; world: "+worldName+". Editing requires edit.spawn.");
 }catch(Exception ex){getLogger().severe("Spawn setup failed ("+ex.getClass().getSimpleName()+"). Requires BlinkNetwork 0.5.3, a separate world and the shared MariaDB database.");Bukkit.getPluginManager().disablePlugin(this);}}
 private String env(String key,String path){String value=System.getenv(key);return value==null||value.isBlank()?network.getConfig().getString(path,""):value;}
 private void main(Runnable action){if(!stopping&&isEnabled())Bukkit.getScheduler().runTask(this,()->{if(!stopping)action.run();});}
 private boolean ready(Player p){try{return p.isOnline()&&!p.isDead()&&!p.isSleeping()&&!p.isInsideVehicle()&&p.getPassengers().isEmpty()&&(Boolean)ready.invoke(network,p);}catch(Exception ex){return false;}}
 private void initialize(){if(stopping||store!=null||!initializing.compareAndSet(false,true))return;SpawnPoint initial=null;if(spawnWorld!=null){Location l=spawnWorld.getSpawnLocation();initial=point(l);}SpawnPoint defaults=initial;
  io.execute(()->{try{HikariConfig c=new HikariConfig();c.setPoolName("UltimateSpawn");c.setJdbcUrl(url);c.setUsername(user);c.setPassword(password);c.setDriverClassName(org.mariadb.jdbc.Driver.class.getName());c.setMaximumPoolSize(1);c.setMinimumIdle(0);c.setConnectionTimeout(3000);c.setValidationTimeout(2000);c.setInitializationFailTimeout(-1);c.addDataSourceProperty("connectTimeout","3000");c.addDataSourceProperty("socketTimeout","5000");pool=new HikariDataSource(c);SpawnStore candidate=new SpawnStore(pool);candidate.initialize();if(defaults!=null)candidate.save(defaults,true);store=candidate;getLogger().info("Shared network spawn is ready.");}
   catch(Exception ex){if(pool!=null){pool.close();pool=null;}getLogger().warning("Spawn database unavailable ("+ex.getClass().getSimpleName()+"); retrying.");}finally{initializing.set(false);}});
 }
 private SpawnPoint point(Location l){return new SpawnPoint(host,worldName,l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch());}
 private void tell(Player p,String text){p.sendMessage("[UltimateSMP] "+text);}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player p)){sender.sendMessage("Use this command in-game.");return true;}if(args.length!=0){tell(p,"Usage: /"+command.getName());return true;}
  if(command.getName().equals("setspawn")){
   if(!p.hasPermission("edit.spawn")){tell(p,"You need edit.spawn.");return true;}
   if(spawnWorld==null||p.getWorld()!=spawnWorld){tell(p,"Use /spawn first, then stand inside the spawn world.");return true;}
   if(!ready(p)||store==null||!safe(p.getLocation())){tell(p,"Stand on safe, solid ground while your network data is ready.");return true;}
   SpawnPoint next=point(p.getLocation());io.execute(()->{try{store.save(next,false);main(()->{spawnWorld.setSpawnLocation((int)Math.floor(next.x()),(int)Math.floor(next.y()),(int)Math.floor(next.z()),next.yaw());if(p.isOnline())tell(p,"Network spawn updated.");});}catch(Exception ex){main(()->tell(p,"Could not save spawn; previous point was preserved."));}});return true;
  }
  if(!p.hasPermission("ultimatespawn.use")){tell(p,"You do not have permission.");return true;}
  if(!ready(p)||store==null){tell(p,"Spawn or your network data is still loading. Try again shortly.");return true;}
  if(loading.containsKey(p.getUniqueId())||warming.containsKey(p.getUniqueId())){tell(p,"You already have a spawn teleport pending. Move to cancel the warmup.");return true;}
  long now=System.currentTimeMillis();if(now<cooldown.getOrDefault(p.getUniqueId(),0L)){tell(p,"Please wait before using /spawn again.");return true;}
  cooldown.put(p.getUniqueId(),now+cooldownSeconds*1000L);UUID nonce=UUID.randomUUID();loading.put(p.getUniqueId(),nonce);
  io.execute(()->{try{SpawnPoint target=store.load();main(()->{if(!nonce.equals(loading.get(p.getUniqueId()))||Bukkit.getPlayer(p.getUniqueId())!=p)return;loading.remove(p.getUniqueId());if(target==null||!target.server().equals(host)||!target.world().equals(worldName)){tell(p,"The spawn host is not ready or its configuration differs.");return;}if(!ready(p))return;
    warming.put(p.getUniqueId(),new Warmup(p,p.getLocation().clone(),target,System.currentTimeMillis()+warmupSeconds*1000L));tell(p,warmupSeconds==0?"Preparing spawn teleport…":"Teleporting to spawn in "+warmupSeconds+" seconds. Stay still and avoid damage.");});}
   catch(Exception ex){main(()->{if(nonce.equals(loading.get(p.getUniqueId()))){loading.remove(p.getUniqueId());if(p.isOnline())tell(p,"Spawn is temporarily unavailable.");}});}});return true;
 }
 private boolean safe(Location l){World w=l.getWorld();int y=l.getBlockY();return w!=null&&w.getWorldBorder().isInside(l)&&y>w.getMinHeight()&&y<w.getMaxHeight()-2&&l.getBlock().isPassable()&&!l.getBlock().isLiquid()&&l.clone().add(0,1,0).getBlock().isPassable()&&!l.clone().add(0,1,0).getBlock().isLiquid()&&w.getBlockAt(l.getBlockX(),y-1,l.getBlockZ()).getType().isSolid();}
 private void pulse(){for(Warmup warm:new ArrayList<>(warming.values())){Player p=warm.player();Location now=p.getLocation();if(!ready(p)||WarmupPolicy.moved(warm.origin(),now)){warming.remove(p.getUniqueId());if(p.isOnline())tell(p,"Spawn teleport cancelled.");continue;}if(System.currentTimeMillis()<warm.due())continue;warming.remove(p.getUniqueId());p.closeInventory();SpawnPoint target=warm.point();try{
   @SuppressWarnings("unchecked") CompletableFuture<Boolean> future=(CompletableFuture<Boolean>)travel.invoke(network,p,target.server(),target.world(),target.x(),target.y(),target.z(),target.yaw(),target.pitch());
   future.whenComplete((ok,error)->main(()->{if(p.isOnline())tell(p,error==null&&Boolean.TRUE.equals(ok)?"Travelling to spawn…":"Spawn is unavailable or the transfer could not start safely.");}));
  }catch(Exception ex){tell(p,"Spawn teleport could not start. Your inventory is unchanged.");}}}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player p&&warming.remove(p.getUniqueId())!=null)tell(p,"Spawn teleport cancelled: damage received.");}
 @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();loading.remove(id);warming.remove(id);cooldown.remove(id);}
 @Override public void onDisable(){stopping=true;Bukkit.getScheduler().cancelTasks(this);io.shutdown();try{io.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}if(pool!=null)pool.close();io.shutdownNow();warming.clear();loading.clear();}
}
