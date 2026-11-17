package shop.ultimatesmp.spawn;
import java.util.*;import java.util.concurrent.*;
import org.bukkit.*;import org.bukkit.attribute.Attribute;import org.bukkit.command.*;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.player.*;import org.bukkit.event.entity.*;import org.bukkit.event.block.*;import org.bukkit.event.inventory.*;import org.bukkit.util.Vector;
/** Consensual own-gear duels, restricted to the spawn host and an isolated generated arena. */
final class SpawnDuels implements Listener,CommandExecutor,TabCompleter {
 private final UltimateSpawn plugin;private final World spawn;private World arena;private final DuelBook book=new DuelBook();
 private final Map<UUID,DuelVitals> saved=new HashMap<>();private final Set<UUID> returning=new HashSet<>(),moving=new HashSet<>(),arrived=new HashSet<>(),restored=new HashSet<>();private final Map<UUID,Long> retry=new HashMap<>();
 private DuelReturnStore disk;private boolean enabled,stopping;private long preparationDeadline;private int lastCountdown=-1;
 private final long requestMillis,countdownMillis,durationMillis;
 SpawnDuels(UltimateSpawn plugin,World spawn){
  this.plugin=plugin;this.spawn=spawn;requestMillis=1000L*clamp(plugin.getConfig().getInt("duels.request-seconds",30),10,120);countdownMillis=1000L*clamp(plugin.getConfig().getInt("duels.countdown-seconds",3),1,10);durationMillis=1000L*clamp(plugin.getConfig().getInt("duels.max-seconds",300),30,1800);
  if(spawn==null||!plugin.getConfig().getBoolean("duels.enabled",true))return;
  try{disk=new DuelReturnStore(plugin.getDataFolder().toPath().resolve("duel-returns.yml"));saved.putAll(disk.read());returning.addAll(saved.keySet());arena=DuelArena.open(plugin);enabled=true;
   Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getScheduler().runTaskTimer(plugin,this::pulse,10,10);plugin.getLogger().info("Spawn duels ready; pending returns: "+returning.size());
  }catch(Exception ex){plugin.getLogger().severe("Duels unavailable ("+ex.getClass().getSimpleName()+"). Check the arena name and duel-returns.yml; spawn remains available.");}
 }
 private static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
 private void tell(Player p,String text){p.sendMessage("[Duel] "+text);}
 private boolean inSpawn(Player p){return spawn!=null&&p.getWorld()==spawn;}
 private boolean available(Player p){return inSpawn(p)&&plugin.activitySpawn()!=null&&p.getGameMode()==GameMode.SURVIVAL&&!saved.containsKey(p.getUniqueId())&&plugin.activityReady(p);}
 private boolean protectedPlayer(Player p){return saved.containsKey(p.getUniqueId());}
 private boolean arenaOccupied(){return arena!=null&&arena.getPlayers().stream().anyMatch(p->saved.containsKey(p.getUniqueId()));}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!(sender instanceof Player p)){sender.sendMessage("Use /duel in-game at spawn.");return true;}
  if(!enabled){tell(p,"Duels are available on the spawn server. Use /spawn first; ask staff if duels remain unavailable.");return true;}
  if(!p.hasPermission("ultimatespawn.duel")){tell(p,"You do not have permission to duel.");return true;}
  if(args.length==1&&args[0].equalsIgnoreCase("leave")&&protectedPlayer(p)){
   if(book.match()!=null&&book.match().contains(p.getUniqueId()))finish(book.match().opponent(p.getUniqueId()),p.getName()+" surrendered.");else returning.add(p.getUniqueId());return true;
  }
  if(!inSpawn(p)){tell(p,"You can only request or accept duels inside spawn. Use /spawn first.");return true;}
  if(args.length==0){tell(p,"/duel <player> · /duel accept [player] · /duel deny · /duel cancel. During a duel: /duel leave.");return true;}
  if(args.length==1&&(args[0].equalsIgnoreCase("deny")||args[0].equalsIgnoreCase("cancel"))){
   var r=book.cancel(p.getUniqueId());tell(p,r==null?"You have no pending duel request.":"Duel request cancelled.");if(r!=null){Player other=Bukkit.getPlayer(r.from().equals(p.getUniqueId())?r.to():r.from());if(other!=null)tell(other,p.getName()+" cancelled the duel request.");}return true;
  }
  if(!available(p)){tell(p,"Be in Survival at spawn with your network data ready, and finish any pending spawn teleport or duel return.");return true;}
  if(args[0].equalsIgnoreCase("accept")){
   if(args.length>2){tell(p,"Usage: /duel accept [player]");return true;}
   var request=book.incoming(p.getUniqueId(),System.currentTimeMillis());if(request==null){tell(p,"No incoming request, or it expired.");return true;}
   Player challenger=Bukkit.getPlayer(request.from());if(challenger==null||!available(challenger)){book.cancel(p.getUniqueId());tell(p,"The challenger must still be at spawn and ready.");return true;}
   if(args.length==2&&!challenger.getName().equalsIgnoreCase(args[1])){tell(p,"That player has not sent you this request.");return true;}
   if(book.match()!=null||arenaOccupied()){tell(p,"The arena is busy. Try again when the current duel finishes.");return true;}
   start(challenger,p);return true;
  }
  if(args.length!=1){tell(p,"Usage: /duel <player>");return true;}
  Player target=Bukkit.getPlayerExact(args[0]);if(target==null||!p.canSee(target)||!target.canSee(p)||!available(target)){tell(p,"That player must be online in Survival at spawn and ready.");return true;}
  if(arenaOccupied()||!book.request(p.getUniqueId(),target.getUniqueId(),System.currentTimeMillis(),requestMillis)){tell(p,"You cannot duel yourself; the arena or one of you already has a duel/request pending. Use /duel cancel to cancel a request.");return true;}
  tell(p,"Request sent to "+target.getName()+". It expires in "+requestMillis/1000+" seconds.");tell(target,p.getName()+" challenged you! Use /duel accept "+p.getName()+" or /duel deny. Bring your own gear; consumables and durability are used normally.");return true;
 }
 private void start(Player first,Player second){
  var token=book.accept(second.getUniqueId(),first.getUniqueId(),System.currentTimeMillis());if(token==null)return;
  try{saved.put(first.getUniqueId(),DuelVitals.capture(first));saved.put(second.getUniqueId(),DuelVitals.capture(second));disk.save(saved);}
  catch(Exception ex){saved.remove(first.getUniqueId());saved.remove(second.getUniqueId());book.finish();tell(first,"Could not save the duel return markers. Duel cancelled.");tell(second,"Could not save the duel return markers. Duel cancelled.");return;}
  preparationDeadline=System.currentTimeMillis()+15000;lastCountdown=-1;clearProjectiles();first.closeInventory();second.closeInventory();
  tell(first,"Preparing arena… /duel leave to surrender.");tell(second,"Preparing arena… /duel leave to surrender.");
  CompletableFuture<Boolean> a=travel(first,DuelArena.side(arena,true)),b=travel(second,DuelArena.side(arena,false));
  a.thenCombine(b,(x,y)->Boolean.TRUE.equals(x)&&Boolean.TRUE.equals(y)).whenComplete((ok,error)->main(()->{
   if(book.match()!=token)return;
   if(error!=null||!Boolean.TRUE.equals(ok)||!first.isOnline()||!second.isOnline()||first.getWorld()!=arena||second.getWorld()!=arena){finish(null,"Arena travel failed. Returning to spawn.");return;}
   for(Player p:List.of(first,second)){for(var effect:List.copyOf(p.getActivePotionEffects()))p.removePotionEffect(effect.getType());p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue());p.setFoodLevel(20);p.setSaturation(5);p.setAbsorptionAmount(0);p.setFireTicks(0);p.setFallDistance(0);p.setVelocity(new Vector());}
   book.arm(System.currentTimeMillis(),countdownMillis,durationMillis);
  }));
 }
 private CompletableFuture<Boolean> travel(Player p,Location target){
  moving.add(p.getUniqueId());CompletableFuture<Boolean> future=plugin.activityTravel(p,target);
  future.whenComplete((ok,error)->main(()->moving.remove(p.getUniqueId())));return future;
 }
 private void main(Runnable run){if(stopping||!plugin.isEnabled())return;if(Bukkit.isPrimaryThread())run.run();else Bukkit.getScheduler().runTask(plugin,()->{if(!stopping)run.run();});}
 private void pulse(){
  long now=System.currentTimeMillis();book.expire(now);var m=book.match();
  if(m!=null){Player a=Bukkit.getPlayer(m.first()),b=Bukkit.getPlayer(m.second());
   if(a==null||b==null)finish(a==null?m.second():m.first(),"Opponent disconnected.");
   else if(m.starts()==Long.MAX_VALUE){if(now>preparationDeadline)finish(null,"Arena preparation timed out.");}
   else if(a.getWorld()!=arena||b.getWorld()!=arena)finish(null,"A player left the arena.");
   else if(now>=m.ends())finish(null,"Time is up. The duel is a draw.");
   else{int left=(int)Math.max(0,(m.starts()-now+999)/1000);if(left!=lastCountdown){lastCountdown=left;for(Player p:List.of(a,b)){tell(p,left>0?"Starting in "+left+"…":"Fight!");p.sendActionBar(left>0?"Duel starts in "+left:"Fight! /duel leave to surrender");}}}
  }
  for(UUID id:List.copyOf(returning)){
   Player p=Bukkit.getPlayer(id);if(p==null||p.isDead()||moving.contains(id)||now<retry.getOrDefault(id,0L)||!plugin.activityNetworkReady(p))continue;
   retry.put(id,now+2000);
   if(restored.contains(id)&&p.getWorld()==spawn){
    try{Map<UUID,DuelVitals> next=new HashMap<>(saved);next.remove(id);disk.save(next);saved.remove(id);returning.remove(id);restored.remove(id);retry.remove(id);tell(p,"Returned to spawn.");}
    catch(Exception ex){plugin.getLogger().warning("Duel return marker retained for "+id+" ("+ex.getClass().getSimpleName()+").");}continue;
   }
   if(arrived.contains(id)&&p.getWorld()==spawn){
    try{saved.get(id).apply(p);travel(p,p.getLocation()).whenComplete((ok,error)->main(()->{if(p.isOnline()&&returning.contains(id)&&error==null&&Boolean.TRUE.equals(ok)){arrived.remove(id);restored.add(id);}}));}
    catch(Exception ex){plugin.getLogger().warning("Could not restore duel vitals for "+id+"; return marker retained.");}continue;
   }
   Location target=plugin.activitySpawn();if(target==null)continue;
   travel(p,target).whenComplete((ok,error)->main(()->{
    if(!returning.contains(id)||!p.isOnline())return;
    if(error!=null||!Boolean.TRUE.equals(ok)||p.getWorld()!=spawn){tell(p,"Spawn return is pending; your inventory is retained. Retrying…");return;}
    arrived.add(id);
   }));
  }
 }
 private void finish(UUID winner,String reason){
  var match=book.finish();if(match==null)return;returning.add(match.first());returning.add(match.second());lastCountdown=-1;clearProjectiles();
  Player won=winner==null?null:Bukkit.getPlayer(winner);String result=reason+(won==null?"":" "+won.getName()+" wins!");
  for(UUID id:List.of(match.first(),match.second())){Player p=Bukkit.getPlayer(id);if(p!=null){p.setFireTicks(0);p.setVelocity(new Vector());tell(p,result+" Returning to spawn…");}}
 }
 private void clearProjectiles(){if(arena!=null)for(Entity e:arena.getEntities())if(e instanceof Projectile||e instanceof AreaEffectCloud||e instanceof TNTPrimed)e.remove();}
 private Player attacker(EntityDamageEvent e){Entity cause=e.getDamageSource().getCausingEntity();if(cause instanceof Player p)return p;if(e instanceof EntityDamageByEntityEvent hit){if(hit.getDamager() instanceof Player p)return p;if(hit.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof Player p)return p;}return null;}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageEvent e){
  Player attacker=attacker(e);Player victim=e.getEntity() instanceof Player p?p:null;var m=book.match();long now=System.currentTimeMillis();
  if(attacker!=null&&protectedPlayer(attacker)&&(victim==null||!DuelBook.combatAllowed(m,attacker.getUniqueId(),victim.getUniqueId(),now))){e.setCancelled(true);return;}
  if(victim==null||!protectedPlayer(victim))return;
  if(m==null||!m.contains(victim.getUniqueId())||now<m.starts()||now>=m.ends()||victim.getWorld()!=arena||(attacker!=null&&!DuelBook.combatAllowed(m,attacker.getUniqueId(),victim.getUniqueId(),now))){e.setCancelled(true);return;}
  if(e.getFinalDamage()>=victim.getHealth()){e.setCancelled(true);finish(m.opponent(victim.getUniqueId()),victim.getName()+" was defeated.");}
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void move(PlayerMoveEvent e){
  if(e instanceof PlayerTeleportEvent||e.getTo()==null||moving.contains(e.getPlayer().getUniqueId())||!protectedPlayer(e.getPlayer()))return;var m=book.match();
  if((m!=null&&m.starts()>System.currentTimeMillis()||returning.contains(e.getPlayer().getUniqueId()))&&WarmupPolicy.moved(e.getFrom(),e.getTo())){e.setCancelled(true);return;}
  if(e.getTo().getWorld()==arena&&(e.getTo().getY()<78||Math.abs(e.getTo().getX())>25||Math.abs(e.getTo().getZ())>25)&&m!=null)finish(m.opponent(e.getPlayer().getUniqueId()),e.getPlayer().getName()+" left the fighting area.");
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void teleport(PlayerTeleportEvent e){
  if(moving.contains(e.getPlayer().getUniqueId()))return;
  var match=book.match();
  // Let the network restore its authoritative snapshot before the pending return runs.
  if(returning.contains(e.getPlayer().getUniqueId())&&(match==null||!match.contains(e.getPlayer().getUniqueId()))&&!plugin.activityNetworkReady(e.getPlayer()))return;
  if(protectedPlayer(e.getPlayer())||e.getTo()!=null&&e.getTo().getWorld()==arena)e.setCancelled(true);
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void commands(PlayerCommandPreprocessEvent e){
  if(!protectedPlayer(e.getPlayer()))return;String[] words=e.getMessage().substring(1).trim().split("\\s+");String label=words[0].toLowerCase(Locale.ROOT);label=label.substring(label.lastIndexOf(':')+1);
  if(!(label.equals("duel")&&words.length==2&&words[1].equalsIgnoreCase("leave"))){e.setCancelled(true);tell(e.getPlayer(),"Use /duel leave to surrender and return to spawn.");}
 }
 @EventHandler public void quit(PlayerQuitEvent e){book.cancel(e.getPlayer().getUniqueId());var m=book.match();if(m!=null&&m.contains(e.getPlayer().getUniqueId()))finish(m.opponent(e.getPlayer().getUniqueId()),e.getPlayer().getName()+" disconnected.");moving.remove(e.getPlayer().getUniqueId());arrived.remove(e.getPlayer().getUniqueId());restored.remove(e.getPlayer().getUniqueId());}
 @EventHandler(priority=EventPriority.HIGHEST)public void death(PlayerDeathEvent e){if(!protectedPlayer(e.getEntity()))return;e.setKeepInventory(true);e.getDrops().clear();e.setKeepLevel(true);e.setDroppedExp(0);var m=book.match();if(m!=null&&m.contains(e.getEntity().getUniqueId()))finish(m.opponent(e.getEntity().getUniqueId()),e.getEntity().getName()+" was defeated.");returning.add(e.getEntity().getUniqueId());}
 @EventHandler(priority=EventPriority.HIGHEST)public void respawn(PlayerRespawnEvent e){if(protectedPlayer(e.getPlayer())){Location target=plugin.activitySpawn();if(target!=null)e.setRespawnLocation(target);returning.add(e.getPlayer().getUniqueId());}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void drop(PlayerDropItemEvent e){if(protectedPlayer(e.getPlayer()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p&&protectedPlayer(p))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void open(InventoryOpenEvent e){if(e.getPlayer() instanceof Player p&&protectedPlayer(p)&&e.getInventory().getType()!=InventoryType.CRAFTING&&e.getInventory().getType()!=InventoryType.PLAYER)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void place(BlockPlaceEvent e){if(e.getBlock().getWorld()==arena)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breakBlock(BlockBreakEvent e){if(e.getBlock().getWorld()==arena)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void bucket(PlayerBucketEmptyEvent e){if(e.getBlock().getWorld()==arena)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void fill(PlayerBucketFillEvent e){if(e.getBlock().getWorld()==arena)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interact(PlayerInteractEvent e){if(e.getPlayer().getWorld()!=arena)return;e.setUseInteractedBlock(Event.Result.DENY);if(e.getItem()!=null){String n=e.getItem().getType().name();if(n.equals("END_CRYSTAL")||n.endsWith("SPAWN_EGG")||n.equals("ARMOR_STAND")||n.contains("MINECART")||n.endsWith("BOAT")||n.endsWith("RAFT")||n.equals("FLINT_AND_STEEL")||n.equals("FIRE_CHARGE"))e.setUseItemInHand(Event.Result.DENY);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void explode(EntityExplodeEvent e){if(e.getEntity().getWorld()==arena){e.blockList().clear();e.setCancelled(true);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void explodeBlock(BlockExplodeEvent e){if(e.getBlock().getWorld()==arena){e.blockList().clear();e.setCancelled(true);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void launch(ProjectileLaunchEvent e){if(e.getEntity().getShooter() instanceof Player p&&protectedPlayer(p)){var match=book.match();if(match==null||!match.contains(p.getUniqueId())||System.currentTimeMillis()<match.starts()||returning.contains(p.getUniqueId()))e.setCancelled(true);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void splash(PotionSplashEvent e){if(e.getEntity().getWorld()!=arena)return;var m=book.match();for(LivingEntity entity:e.getAffectedEntities())if(!(entity instanceof Player p)||m==null||!m.contains(p.getUniqueId())||System.currentTimeMillis()<m.starts()||returning.contains(p.getUniqueId()))e.setIntensity(entity,0);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void cloud(AreaEffectCloudApplyEvent e){if(e.getEntity().getWorld()!=arena)return;var m=book.match();e.getAffectedEntities().removeIf(entity->!(entity instanceof Player p)||m==null||!m.contains(p.getUniqueId())||System.currentTimeMillis()<m.starts()||returning.contains(p.getUniqueId()));}
 @Override public List<String> onTabComplete(CommandSender sender,Command c,String label,String[] args){
  if(!(sender instanceof Player p)||!enabled)return List.of();String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
  if(protectedPlayer(p))return args.length==1&&"leave".startsWith(prefix)?List.of("leave"):List.of();
  if(!inSpawn(p)||!p.hasPermission("ultimatespawn.duel"))return List.of();List<String> out=new ArrayList<>();
  if(args.length==1){out.addAll(List.of("accept","deny","cancel"));for(Player other:spawn.getPlayers())if(other!=p&&p.canSee(other)&&other.canSee(p))out.add(other.getName());}
  if(args.length==2&&args[0].equalsIgnoreCase("accept")){var r=book.incoming(p.getUniqueId(),System.currentTimeMillis());if(r!=null){Player from=Bukkit.getPlayer(r.from());if(from!=null&&p.canSee(from))out.add(from.getName());}}
  return out.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
 }
 void shutdown(){if(!enabled)return;stopping=true;var m=book.finish();if(m!=null){returning.add(m.first());returning.add(m.second());}try{if(disk!=null)disk.save(saved);}catch(Exception ex){plugin.getLogger().severe("Duel return markers could not be saved; retain duel-returns.yml.");}}
}
