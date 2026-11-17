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
    private ShopStore store;private volatile boolean ready,stopping;private JavaPlugin ranks,network;private Map<String,String> packages=Map.of();private String website;private String backend;
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
            Bukkit.getPluginManager().registerEvents(this,this);getCommand("shop").setTabCompleter(this);getCommand("ultimateshop").setTabCompleter(this);
            Bukkit.getScheduler().runTaskTimer(this,this::poll,1,20L*Math.max(5,getConfig().getInt("poll-seconds",10)));
            io.execute(()->{try{store.initialize();ready=true;getLogger().info("Shared UltimateShop records ready.");}catch(Exception e){getLogger().severe("Shop database setup failed ("+e.getClass().getSimpleName()+"). Check JDBC settings; no purchases granted.");}});
        }catch(Exception e){getLogger().severe("Cannot enable UltimateShop: "+e.getMessage());Bukkit.getPluginManager().disablePlugin(this);}
    }
    private void loadPackages() throws Exception {
        var u=URI.create(getConfig().getString("website","https://www.blinksmp.shop"));if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalArgumentException("website must be an HTTPS origin.");
        Map<String,String> next=new HashMap<>();var section=getConfig().getConfigurationSection("packages");if(section==null)throw new IllegalArgumentException("Missing packages.");
        for(String key:section.getKeys(false)){if(!key.matches("[a-z0-9-]{1,64}"))throw new IllegalArgumentException("Invalid package ID.");String rank=(String)ranks.getClass().getMethod("normalizeTemporaryRank",String.class).invoke(ranks,section.getString(key));if(!rank.startsWith("ULTIMATE_")&&!rank.startsWith("SUPPORTER"))throw new IllegalArgumentException("Shop packages cannot grant staff ranks.");next.put(key,rank);}
        packages=Map.copyOf(next);website="https://"+u.getRawAuthority();
    }
    private String credential(String env,String path){String value=System.getenv(env);return value==null||value.isBlank()?network.getConfig().getString(path,""):value;}
    private boolean usable(Player p){if(!p.isOnline())return false;if(network==null)return true;try{return network.isEnabled()&&(Boolean)network.getClass().getMethod("isPlayerReady",Player.class).invoke(network,p);}catch(Exception e){return false;}}
    private void main(Runnable r){if(!stopping)Bukkit.getScheduler().runTask(this,()->{if(!stopping)r.run();});}
    private void apply(Player p,Map<String,Long> values){if(!usable(p))return;Map<String,Long> rankValues=new HashMap<>();values.forEach((key,end)->{String rank=packages.get(key);if(rank!=null)rankValues.merge(rank,end,Math::max);});try{ranks.getClass().getMethod("setShopRankExpiries",UUID.class,Map.class).invoke(ranks,p.getUniqueId(),rankValues);}catch(Exception e){getLogger().severe("Rank integration failed; install UltimateRanks 1.3.3+.");}}
    private void poll(){if(!ready||!polling.compareAndSet(false,true))return;var players=Bukkit.getOnlinePlayers().stream().filter(this::usable).toList();io.execute(()->{try{store.heartbeat(backend);for(Player p:players){var values=store.read(p.getUniqueId(),p.getName());main(()->{if(Bukkit.getPlayer(p.getUniqueId())==p)apply(p,values);});}}catch(Exception e){getLogger().warning("Shop sync unavailable ("+e.getClass().getSimpleName()+"); existing ranks retain their original expiry.");}finally{polling.set(false);}});}
    @Override public boolean onCommand(CommandSender s,Command command,String label,String[] args){
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
        if(args.length>=2&&args.length<=4&&Set.of("give","revoke","status").contains(args[0].toLowerCase(Locale.ROOT))){String action=args[0].toLowerCase(Locale.ROOT);if(!action.equals("status")&&(args.length<3||!packages.containsKey(args[2]))){s.sendMessage("Unknown package. Packages: "+packages.keySet());return true;}long until=Long.MAX_VALUE;try{if(args.length==4){int days=Integer.parseInt(args[3]);if(!action.equals("give")||days<1||days>36500)throw new IllegalArgumentException();until=Math.addExact(System.currentTimeMillis(),days*86400000L);}}catch(Exception e){s.sendMessage("Days must be 1–36500.");return true;}
            long end=until;io.execute(()->{try{UUID id=store.resolve(args[1]);if(action.equals("give"))store.give(id,args[2],end);else if(action.equals("revoke"))store.revoke(id,args[2]);var state=store.read(id,null);main(()->{s.sendMessage("Shop "+action+": "+id+"; active packages "+state.keySet()+". Syncs across servers within the polling interval.");Player p=Bukkit.getPlayer(id);if(p!=null)apply(p,state);});}catch(Exception e){main(()->s.sendMessage(e instanceof IllegalArgumentException?e.getMessage():"Shop operation failed ("+e.getClass().getSimpleName()+"). No automatic retry."));}});return true;
        }
        s.sendMessage("/ultimateshop give <player|uuid> <package> [days] | revoke <player|uuid> <package> | status <player|uuid> | reload");return true;
    }
    @EventHandler public void quit(PlayerQuitEvent e){links.remove(e.getPlayer().getUniqueId());fetching.remove(e.getPlayer().getUniqueId());}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(c.getName().equals("shop")||!s.hasPermission("ultimateshop.admin"))return List.of();if(args.length==1)return List.of("give","revoke","status","reload").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();if(args.length==3)return packages.keySet().stream().filter(x->x.startsWith(args[2])).sorted().toList();return List.of();}
    @Override public void onDisable(){stopping=true;ready=false;io.shutdownNow();if(ranks!=null)for(Player p:Bukkit.getOnlinePlayers())try{ranks.getClass().getMethod("setShopRankExpiries",UUID.class,Map.class).invoke(ranks,p.getUniqueId(),Map.of());}catch(Exception ignored){}}
}
