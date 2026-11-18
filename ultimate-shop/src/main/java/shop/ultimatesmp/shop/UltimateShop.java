package shop.ultimatesmp.shop;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.SecureRandom;
import java.net.URI;

public final class UltimateShop extends JavaPlugin implements Listener, TabCompleter {
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"UltimateShop-DB");t.setDaemon(true);return t;});
    private final AtomicBoolean polling=new AtomicBoolean();
    private PaperDiscordBot discordBot;
    private ShopStore store;private volatile boolean ready,stopping;private JavaPlugin ranks,network;private Map<String,String> packages=Map.of();private String website;private String backend;
    private final Set<UUID> delivering=new HashSet<>();private Map<String,String> keyPackages=Map.of();
    private final Map<UUID,Long> links=new HashMap<>();private final Set<UUID> fetching=new HashSet<>();
    @Override public void onEnable(){
        saveDefaultConfig();
        try{
            ranks=(JavaPlugin)Bukkit.getPluginManager().getPlugin("UltimateRanks");ranks.getClass().getMethod("setShopRankExpiries",UUID.class,Map.class);
            network=(JavaPlugin)Bukkit.getPluginManager().getPlugin("BlinkNetwork");loadPackages();
            String url=getConfig().getString("database.jdbc-url",""),user=getConfig().getString("database.username",""),password=getConfig().getString("database.password","");
            if(getConfig().getBoolean("database.inherit-blinknetwork",true)){
                if(network==null||!network.isEnabled())throw new IllegalArgumentException("BlinkNetwork is required to inherit database settings.");
                url=credential("BLINK_NETWORK_JDBC_URL","database.jdbc-url");user=credential("BLINK_NETWORK_DB_USER","database.username");password=credential("BLINK_NETWORK_DB_PASSWORD","database.password");
            }
            if(!url.startsWith("jdbc:mariadb://")&&!url.startsWith("jdbc:mysql://"))throw new IllegalArgumentException("A shared MariaDB/MySQL JDBC URL is required.");
            backend=network!=null?network.getConfig().getString("server-id",""):getConfig().getString("backend-id","");if(!backend.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("Set a unique backend-id.");
            store=new ShopStore(url.replaceFirst("^jdbc:mysql:","jdbc:mariadb:"),user,password);
            Bukkit.getPluginManager().registerEvents(this,this);getCommand("shop").setTabCompleter(this);getCommand("link").setTabCompleter((sender,command,label,args)->List.of());getCommand("ultimateshop").setTabCompleter(this);
            Bukkit.getScheduler().runTaskTimer(this,this::poll,1,20L*Math.max(5,getConfig().getInt("poll-seconds",10)));
            polling.set(true);io.execute(()->{try{store.initialize();ready=true;getLogger().info("Shared UltimateShop records ready.");}catch(Exception e){getLogger().severe("Shop database setup failed ("+e.getClass().getSimpleName()+"). Check JDBC settings; no purchases granted.");}finally{polling.set(false);}});
            startDiscordBot();
        }catch(Exception e){getLogger().severe("Cannot enable UltimateShop: "+e.getMessage());Bukkit.getPluginManager().disablePlugin(this);}
    }
    private void startDiscordBot() {
        if (!getConfig().getBoolean("discord.enabled", false)) return;
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) token = getConfig().getString("discord.token", "");
        String guild = System.getenv("DISCORD_GUILD_ID");
        if (guild == null || guild.isBlank()) guild = getConfig().getString("discord.guild-id", "");
        if (token.isBlank() || !guild.matches("[0-9]{17,20}")) {
            getLogger().warning("Discord bot needs a token and a valid Discord server ID.");
            return;
        }
        try {
            discordBot = new PaperDiscordBot(getLogger(), store, guild);
            discordBot.start(token);
        } catch (Exception error) {
            if (discordBot != null) discordBot.close();
            discordBot = null;
            getLogger().warning("Discord bot startup failed (" + error.getClass().getSimpleName() + ").");
        }
    }

    private void loadPackages() throws Exception {
        var u=URI.create(getConfig().getString("website","https://www.blinksmp.shop"));if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalArgumentException("website must be an HTTPS origin.");
        Map<String,String> next=new HashMap<>();var section=getConfig().getConfigurationSection("packages");if(section==null)throw new IllegalArgumentException("Missing packages.");
        for(String key:section.getKeys(false)){if(!key.matches("[a-z0-9-]{1,64}"))throw new IllegalArgumentException("Invalid package ID.");String rank=(String)ranks.getClass().getMethod("normalizeTemporaryRank",String.class).invoke(ranks,section.getString(key));if(!rank.startsWith("ULTIMATE_")&&!rank.startsWith("SUPPORTER"))throw new IllegalArgumentException("Shop packages cannot grant staff ranks.");next.put(key,rank);}
        Map<String,String> keys=new HashMap<>();for(String rarity:List.of("common","uncommon","rare","legendary")){String crate=getConfig().getString("key-packages.key-"+rarity,rarity);if(!crate.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("Invalid key crate ID");keys.put("key-"+rarity,crate);}keyPackages=Map.copyOf(keys);
        packages=Map.copyOf(next);website="https://"+u.getRawAuthority();
    }
    private String credential(String env,String path){String value=System.getenv(env);return value==null||value.isBlank()?network.getConfig().getString(path,""):value;}
    private boolean usable(Player p){if(!p.isOnline())return false;if(network==null)return true;try{return network.isEnabled()&&(Boolean)network.getClass().getMethod("isPlayerReady",Player.class).invoke(network,p);}catch(Exception e){return false;}}
    private void main(Runnable r){if(!stopping)Bukkit.getScheduler().runTask(this,()->{if(!stopping)r.run();});}
    private void apply(Player p,Map<String,Long> values){if(!usable(p))return;Map<String,Long> rankValues=new HashMap<>();values.forEach((key,end)->{String rank=packages.get(key);if(rank!=null)rankValues.merge(rank,end,Math::max);});try{ranks.getClass().getMethod("setShopRankExpiries",UUID.class,Map.class).invoke(ranks,p.getUniqueId(),rankValues);}catch(Exception e){getLogger().severe("Rank integration failed; install UltimateRanks 1.3.3+.");}}
    private void poll(){if(stopping||store==null||!polling.compareAndSet(false,true))return;var players=Bukkit.getOnlinePlayers().stream().filter(this::usable).toList();Set<String> supported=supportedKeys();io.execute(()->{try{if(!ready){store.initialize();ready=true;}store.heartbeat(backend);store.keyHeartbeat(backend,supported);for(Player p:players){var values=store.read(p.getUniqueId(),p.getName());var pending=store.pending(p.getUniqueId());main(()->{if(Bukkit.getPlayer(p.getUniqueId())==p){apply(p,values);deliver(p,pending);}});}}catch(Exception e){getLogger().warning("Shop sync unavailable ("+e.getClass().getSimpleName()+"); existing ranks retain their original expiry.");}finally{polling.set(false);}});}
    private org.bukkit.plugin.Plugin crates(){var plugin=Bukkit.getPluginManager().getPlugin("UltimateCrates");return plugin!=null&&plugin.isEnabled()?plugin:null;}
    private Set<String> supportedKeys(){try{var c=crates();if(c==null)return Set.of();var ids=(Set<?>)c.getClass().getMethod("getShopCrateIds").invoke(c);Set<String> result=new HashSet<>();keyPackages.forEach((pack,id)->{if(ids.contains(id))result.add(pack);});return Set.copyOf(result);}catch(Exception e){return Set.of();}}
    private void deliver(Player player,List<ShopStore.Delivery> pending){
        if(!usable(player)||delivering.contains(player.getUniqueId()))return;
        var c=crates();if(c==null)return;
        for(var d:pending){String crate=keyPackages.get(d.pack());if(crate==null)continue;
            try{if(!(Boolean)c.getClass().getMethod("canReceiveShopKeys",Player.class,String.class,int.class).invoke(c,player,crate,d.amount()))continue;}catch(Exception e){return;}
            delivering.add(player.getUniqueId());String claim=UUID.randomUUID().toString();
            io.execute(()->{try{boolean acquired=store.claim(d,player.getUniqueId(),backend,claim);main(()->{
                if(!acquired){delivering.remove(player.getUniqueId());return;}
                if(Bukkit.getPlayer(player.getUniqueId())!=player||!usable(player)||!c.isEnabled()){finishDelivery(player,d,claim,false);return;}
                try{boolean given=(Boolean)c.getClass().getMethod("giveShopKeys",Player.class,String.class,int.class).invoke(c,player,crate,d.amount());finishDelivery(player,d,claim,given);}
                catch(Exception e){delivering.remove(player.getUniqueId());getLogger().severe("Shop key delivery "+d.id()+" requires review. Check the player's inventory before retrying; it will not be issued twice automatically.");}
            });}catch(Exception e){main(()->delivering.remove(player.getUniqueId()));getLogger().warning("Key delivery claim unavailable; delivery remains pending or held for review.");}});return;
        }
    }
    private void finishDelivery(Player p,ShopStore.Delivery d,String claim,boolean given){io.execute(()->{try{store.finish(d.id(),claim,given);main(()->{delivering.remove(p.getUniqueId());if(given&&Bukkit.getPlayer(p.getUniqueId())==p)p.sendMessage("[UltimateShop] Delivered "+d.amount()+" "+d.pack().substring(4)+" key(s).");});}catch(Exception e){main(()->delivering.remove(p.getUniqueId()));getLogger().severe("Key delivery "+d.id()+" acknowledgement failed. Review before retrying; no automatic duplicate grant.");}});}
    @Override public boolean onCommand(CommandSender s,Command command,String label,String[] args){
        if (command.getName().equals("link")) {
            if (!(s instanceof Player player)) {
                s.sendMessage("Run /link in Minecraft.");
                return true;
            }
            if (args.length != 1 || !args[0].matches("[0-9a-f]{32}")) {
                player.sendMessage("Run /link in Discord to get your private code.");
                return true;
            }
            if (!ready) {
                player.sendMessage("Discord linking is temporarily unavailable.");
                return true;
            }
            UUID playerId = player.getUniqueId();
            String code = args[0];
            io.execute(() -> {
                try {
                    boolean linked = store.linkDiscord(playerId, code);
                    main(() -> {
                        if (Bukkit.getPlayer(playerId) == player) player.sendMessage(linked
                                ? "Your Discord account is linked."
                                : "Code expired, invalid, or an account is already linked.");
                    });
                } catch (Exception error) {
                    getLogger().warning("Discord linking failed (" + error.getClass().getSimpleName() + ").");
                    main(() -> {
                        if (Bukkit.getPlayer(playerId) == player) player.sendMessage("Discord linking is temporarily unavailable.");
                    });
                }
            });
            return true;
        }
        if(command.getName().equals("shop")){
            if(!(s instanceof Player p)){s.sendMessage("Players: /shop. Console: /ultimateshop give <player|uuid> <package> [days]");return true;}
            if(!ready||!usable(p)){s.sendMessage("Shop/network data is loading. Try shortly.");return true;}
            long now=System.currentTimeMillis();if(fetching.contains(p.getUniqueId())||now<links.getOrDefault(p.getUniqueId(),0L)){s.sendMessage("Please wait 30 seconds before requesting another shop link.");return true;}
            byte[] random=new byte[24];new SecureRandom().nextBytes(random);String code=HexFormat.of().formatHex(random);links.put(p.getUniqueId(),now+30000);fetching.add(p.getUniqueId());
            io.execute(()->{try{store.link(p.getUniqueId(),p.getName(),code,now+600000);main(()->{fetching.remove(p.getUniqueId());if(Bukkit.getPlayer(p.getUniqueId())==p){p.sendMessage(Component.text("[UltimateShop] Open the shop (private link, expires in 10 minutes)").clickEvent(ClickEvent.openUrl(website+"/#store?code="+code)));p.sendMessage(Component.text("Link code: "+code).clickEvent(ClickEvent.copyToClipboard(code)));}});}catch(Exception e){main(()->{fetching.remove(p.getUniqueId());p.sendMessage("Shop link unavailable. Try later.");});}});return true;
        }
        if(!(s instanceof ConsoleCommandSender)&&!s.hasPermission("ultimateshop.admin")){s.sendMessage("You do not have permission.");return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("reload")){try{reloadConfig();loadPackages();s.sendMessage("Packages reloaded. Database changes require a restart.");poll();}catch(Exception e){s.sendMessage("Invalid shop configuration; previous package mapping retained.");}return true;}
        if(!ready){s.sendMessage("Shop database is not ready.");return true;}
        if(args.length==3&&args[0].equalsIgnoreCase("retry")&&args[2].equalsIgnoreCase("confirm")){try{UUID.fromString(args[1]);}catch(Exception e){s.sendMessage("Use a delivery UUID from /ultimateshop deliveries <player>.");return true;}io.execute(()->{try{boolean ok=store.retry(args[1]);main(()->s.sendMessage(ok?"Held delivery requeued after your inventory verification. This can duplicate items if they were already received.":"Only held claimed/review deliveries can be retried."));}catch(Exception e){main(()->s.sendMessage("Retry failed. Check database."));}});return true;}
        if(args.length==2&&args[0].equalsIgnoreCase("deliveries")){io.execute(()->{try{var rows=store.deliveries(store.resolve(args[1]));main(()->{s.sendMessage("Key deliveries (verify inventories before /ultimateshop retry <id> confirm):");rows.forEach(s::sendMessage);});}catch(Exception e){main(()->s.sendMessage("Could not read key deliveries."));}});return true;}
        if(args.length>=3&&args.length<=4&&args[0].equalsIgnoreCase("givekey")){String pack=args[2].startsWith("key-")?args[2]:"key-"+args[2];if(!keyPackages.containsKey(pack)){s.sendMessage("Use common, uncommon, rare or legendary.");return true;}int count;try{count=args.length==4?Integer.parseInt(args[3]):1;if(count<1||count>64)throw new IllegalArgumentException();}catch(Exception e){s.sendMessage("Amount must be 1-64.");return true;}io.execute(()->{try{UUID target=store.resolve(args[1]);store.keyGrant(target,pack,count);main(()->s.sendMessage("Queued "+count+" "+pack+" key(s). Delivered after the player is ready and has space."));}catch(Exception e){main(()->s.sendMessage("Could not queue keys. Check database/player UUID."));}});return true;}
        if(args.length>=2&&args.length<=4&&Set.of("give","revoke","status").contains(args[0].toLowerCase(Locale.ROOT))){String action=args[0].toLowerCase(Locale.ROOT);if(!action.equals("status")&&(args.length<3||!packages.containsKey(args[2]))){s.sendMessage("Unknown package. Packages: "+packages.keySet());return true;}long until=Long.MAX_VALUE;try{if(args.length==4){int days=Integer.parseInt(args[3]);if(!action.equals("give")||days<1||days>36500)throw new IllegalArgumentException();until=Math.addExact(System.currentTimeMillis(),days*86400000L);}}catch(Exception e){s.sendMessage("Days must be 1–36500.");return true;}
            long end=until;io.execute(()->{try{UUID id=store.resolve(args[1]);if(action.equals("give"))store.give(id,args[2],end);else if(action.equals("revoke"))store.revoke(id,args[2]);var state=store.read(id,null);main(()->{s.sendMessage("Shop "+action+": "+id+"; active packages "+state.keySet()+". Syncs across servers within the polling interval.");Player p=Bukkit.getPlayer(id);if(p!=null)apply(p,state);});}catch(Exception e){main(()->s.sendMessage(e instanceof IllegalArgumentException?e.getMessage():"Shop operation failed ("+e.getClass().getSimpleName()+"). No automatic retry."));}});return true;
        }
        s.sendMessage("/ultimateshop give <player|uuid> <package> [days] | givekey <player|uuid> <rarity> [amount] | deliveries <player|uuid> | revoke <player|uuid> <package> | status <player|uuid> | reload");return true;
    }
    @EventHandler public void hideLink(org.bukkit.event.player.PlayerCommandSendEvent event) {
        event.getCommands().removeIf(name -> name.equals("link") || name.equals("ultimateshop:link"));
    }

    @EventHandler public void quit(PlayerQuitEvent e){links.remove(e.getPlayer().getUniqueId());fetching.remove(e.getPlayer().getUniqueId());}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(c.getName().equals("shop")||!s.hasPermission("ultimateshop.admin"))return List.of();if(args.length==1)return List.of("give","givekey","deliveries","revoke","status","reload").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();if(args.length==3)return packages.keySet().stream().filter(x->x.startsWith(args[2])).sorted().toList();return List.of();}
    @Override public void onDisable(){if(discordBot!=null)discordBot.close();stopping=true;ready=false;io.shutdownNow();if(ranks!=null)for(Player p:Bukkit.getOnlinePlayers())try{ranks.getClass().getMethod("setShopRankExpiries",UUID.class,Map.class).invoke(ranks,p.getUniqueId(),Map.of());}catch(Exception ignored){}}
}
