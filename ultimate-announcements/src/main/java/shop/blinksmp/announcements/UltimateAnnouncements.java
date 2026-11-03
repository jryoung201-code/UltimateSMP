package shop.blinksmp.announcements;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class UltimateAnnouncements extends JavaPlugin implements TabCompleter,Listener {
    enum Display { CHAT,TITLE,ACTIONBAR,BOSSBAR }
    record Notice(String id,Display display,List<String> lines,int duration) {}
    record Settings(boolean enabled,int interval,boolean random,String serverName,String prefix,
                    List<Notice> notices,int defaultSeconds,boolean restartChat,boolean restartTitle,
                    String countdown,String countdownTitle,String countdownSubtitle,String ending,String cancelled) {}
    record ActiveBar(Player player,BossBar bar,long expires) {}
    private Settings settings;
    private final Countdown countdown=new Countdown();
    private final Rotation rotation=new Rotation();
    private final Map<UUID,ActiveBar> bars=new HashMap<>();
    private final LegacyComponentSerializer colors=LegacyComponentSerializer.legacyAmpersand();
    private BukkitTask heartbeat;
    private boolean paused,stopping;
    private long tick,nextAutomatic,nextCountdownNanos;

    @Override public void onEnable() {
        saveDefaultConfig();
        try { settings=readSettings(); }
        catch(Exception ex) { getLogger().severe("Invalid config: "+ex.getMessage());Bukkit.getPluginManager().disablePlugin(this);return; }
        nextAutomatic=settings.interval();
        for(String name:List.of("announce","announcements","restartcountdown")) Objects.requireNonNull(getCommand(name)).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,this);
        heartbeat=Bukkit.getScheduler().runTaskTimer(this,this::pulse,20,20);
        getLogger().info("UltimateAnnouncements active: "+settings.notices().size()+" messages. Restart countdown saves and stops; your host must start the process again.");
    }
    private Settings readSettings() throws Exception {
        YamlConfiguration cfg=new YamlConfiguration();cfg.load(new File(getDataFolder(),"config.yml"));
        int interval=cfg.getInt("interval-seconds",300);
        if(interval<10 || interval>86400) throw new IllegalArgumentException("interval-seconds must be 10 to 86400");
        List<Notice> notices=new ArrayList<>();
        ConfigurationSection root=cfg.getConfigurationSection("announcements");
        if(root!=null) for(String id:root.getKeys(false)) {
            ConfigurationSection section=root.getConfigurationSection(id);
            if(section==null) throw new IllegalArgumentException("Announcement "+id+" must be a section");
            if(!section.getBoolean("enabled",true)) continue;
            Display display;
            try { display=Display.valueOf(section.getString("display","chat").toUpperCase(Locale.ROOT)); }
            catch(IllegalArgumentException ex) { throw new IllegalArgumentException("Unknown display for "+id); }
            List<String> lines=section.getStringList("messages");
            if(lines.isEmpty() || lines.size()>20 || lines.stream().anyMatch(s->s.length()>2048))
                throw new IllegalArgumentException("Announcement "+id+" needs 1-20 message lines of at most 2048 characters");
            int duration=section.getInt("duration-seconds",5);
            if(duration<1 || duration>60) throw new IllegalArgumentException("duration-seconds must be 1 to 60 for "+id);
            notices.add(new Notice(id,display,List.copyOf(lines),duration));
        }
        int seconds=cfg.getInt("restart.default-seconds",10);Countdown.seconds(Integer.toString(seconds));
        return new Settings(cfg.getBoolean("enabled",true),interval,cfg.getBoolean("random-order",false),
            cfg.getString("server-name","UltimateSMP"),cfg.getString("manual-prefix","&6&lANNOUNCEMENT &8» &f"),List.copyOf(notices),seconds,
            cfg.getBoolean("restart.chat",true),cfg.getBoolean("restart.title",true),
            cfg.getString("restart.countdown-message","&cServer is restarting in {seconds}..."),
            cfg.getString("restart.countdown-title","&c&l{seconds}"),cfg.getString("restart.countdown-subtitle","&eServer is restarting"),
            cfg.getString("restart.final-message","&c&lRestarting......"),cfg.getString("restart.cancelled-message","&aServer restart cancelled."));
    }
    private void pulse() {
        tick++;
        for(Iterator<ActiveBar> it=bars.values().iterator();it.hasNext();) {
            ActiveBar bar=it.next();
            if(tick>=bar.expires()) { bar.player().hideBossBar(bar.bar());it.remove(); }
        }
        if(stopping) return;
        if(countdown.active()) {
            if(System.nanoTime()-nextCountdownNanos>=0) {
                nextCountdownNanos+=1_000_000_000L; // Keep the original clock phase; scheduler jitter must not accumulate.
                int remaining=countdown.tick();
                if(remaining==0) finishRestart(); else restartNotice(remaining);
            }
            return;
        }
        if(tick<nextAutomatic) return;
        nextAutomatic=tick+settings.interval();
        if(paused || !settings.enabled() || settings.notices().isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        int index=rotation.next(settings.notices().size(),settings.random(),ThreadLocalRandom.current());
        broadcast(settings.notices().get(index));
    }
    private Component text(String raw,Player player,int seconds) {
        return colors.deserialize(raw.replace("{player}",player==null?"Console":player.getName())
            .replace("{server}",settings.serverName()).replace("{online}",Integer.toString(Bukkit.getOnlinePlayers().size()))
            .replace("{max_players}",Integer.toString(Bukkit.getMaxPlayers())).replace("{seconds}",Integer.toString(seconds)));
    }
    private void show(Notice notice,Player player) {
        List<String> lines=notice.lines();
        switch(notice.display()) {
            case CHAT -> lines.forEach(line->player.sendMessage(text(line,player,0)));
            case ACTIONBAR -> player.sendActionBar(text(String.join(" ",lines),player,0));
            case TITLE -> player.showTitle(Title.title(text(lines.get(0),player,0),text(String.join(" ",lines.subList(1,lines.size())),player,0),
                Title.Times.times(Duration.ofMillis(200),Duration.ofSeconds(notice.duration()),Duration.ofMillis(500))));
            case BOSSBAR -> {
                hideBar(player.getUniqueId());
                BossBar bar=BossBar.bossBar(text(String.join(" ",lines),player,0),1f,BossBar.Color.YELLOW,BossBar.Overlay.PROGRESS);
                player.showBossBar(bar);bars.put(player.getUniqueId(),new ActiveBar(player,bar,tick+notice.duration()));
            }
        }
    }
    private void broadcast(Notice notice) { for(Player player:Bukkit.getOnlinePlayers()) show(notice,player); }
    private void restartNotice(int remaining) {
        for(Player player:Bukkit.getOnlinePlayers()) {
            if(settings.restartChat()) player.sendMessage(text(settings.countdown(),player,remaining));
            if(settings.restartTitle()) player.showTitle(Title.title(text(settings.countdownTitle(),player,remaining),text(settings.countdownSubtitle(),player,remaining),
                Title.Times.times(Duration.ZERO,Duration.ofMillis(1100),Duration.ZERO)));
        }
    }
    private void finishRestart() {
        stopping=true;clearBars();
        for(Player player:Bukkit.getOnlinePlayers()) {
            try {
                if(settings.restartChat()) player.sendMessage(text(settings.ending(),player,0));
                if(settings.restartTitle()) player.showTitle(Title.title(text(settings.ending(),player,0),Component.empty()));
            } catch(RuntimeException ex) { getLogger().warning("Could not send final restart notice: "+ex); }
        }
        getLogger().warning("Restart countdown complete. Saving worlds and stopping; hosting panel must start the server again.");
        for(World world:Bukkit.getWorlds()) {
            try { world.save(); } catch(RuntimeException ex) { getLogger().severe("World save failed for "+world.getName()+": "+ex); }
        }
        Bukkit.shutdown(); // Normal Paper shutdown also saves players and worlds. Never executes a configurable shell command.
    }
    private void cancelNotice() {
        for(Player player:Bukkit.getOnlinePlayers()) {
            if(settings.restartChat()) player.sendMessage(text(settings.cancelled(),player,0));
            if(settings.restartTitle()) player.clearTitle();
        }
    }
    private void hideBar(UUID id) { ActiveBar previous=bars.remove(id);if(previous!=null) previous.player().hideBossBar(previous.bar()); }
    private void clearBars() { for(ActiveBar bar:bars.values()) bar.player().hideBossBar(bar.bar());bars.clear(); }
    @EventHandler public void quit(PlayerQuitEvent e) { hideBar(e.getPlayer().getUniqueId()); }
    @Override public void onDisable() {
        if(heartbeat!=null) heartbeat.cancel();countdown.cancel();clearBars();
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        boolean restart=command.getName().equals("restartcountdown");
        if(!sender.hasPermission(restart?"ultimateannouncements.restart":"ultimateannouncements.manage")) { sender.sendMessage("You do not have permission.");return true; }
        try {
            if(restart) {
                if(args.length>1) throw new IllegalArgumentException("Usage: /restartcountdown [1-3600|cancel|status]");
                if(args.length==1 && args[0].equalsIgnoreCase("cancel")) {
                    if(!countdown.cancel()) { sender.sendMessage("No restart is counting down.");return true; }
                    cancelNotice();nextAutomatic=tick+settings.interval();sender.sendMessage("Restart cancelled.");getLogger().info(sender.getName()+" cancelled the restart countdown.");return true;
                }
                if(args.length==1 && args[0].equalsIgnoreCase("status")) { sender.sendMessage(countdown.active()?"Restart in "+countdown.remaining()+" seconds.":"No restart is counting down.");return true; }
                int seconds=args.length==0?settings.defaultSeconds():Countdown.seconds(args[0]);
                countdown.start(seconds);clearBars();restartNotice(seconds);nextCountdownNanos=System.nanoTime()+1_000_000_000L;
                sender.sendMessage("Countdown started: "+seconds+" seconds. At zero the server saves and STOPS; the host must start it again.");
                getLogger().warning(sender.getName()+" started a "+seconds+"-second save-and-stop countdown.");return true;
            }
            if(command.getName().equals("announce")) {
                if(args.length==0) throw new IllegalArgumentException("Usage: /announce <message>");
                String message=String.join(" ",args);
                if(message.length()>2048) throw new IllegalArgumentException("Message is too long (maximum 2048 characters).");
                broadcast(new Notice("manual",Display.CHAT,List.of(settings.prefix()+message),5));sender.sendMessage("Announcement sent.");return true;
            }
            String action=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
            switch(action) {
                case "pause" -> { requireCount(args,1);paused=true;sender.sendMessage("Automatic announcements paused."); }
                case "resume" -> { requireCount(args,1);paused=false;nextAutomatic=tick+settings.interval();sender.sendMessage(settings.enabled()?"Automatic announcements resumed.":"Set enabled: true in config and reload to enable automatic announcements."); }
                case "reload" -> {
                    requireCount(args,1);
                    if(countdown.active()) throw new IllegalArgumentException("Cancel the restart before reloading.");
                    Settings replacement=readSettings();settings=replacement;rotation.reset();nextAutomatic=tick+settings.interval();clearBars();sender.sendMessage("Configuration reloaded. "+settings.notices().size()+" announcements loaded.");
                }
                case "list" -> { requireCount(args,1);sender.sendMessage("Announcements: "+String.join(", ",settings.notices().stream().map(Notice::id).toList())); }
                case "send", "preview" -> {
                    requireCount(args,2);
                    Notice notice=settings.notices().stream().filter(n->n.id().equalsIgnoreCase(args[1])).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown announcement. Use /announcements list."));
                    if(action.equals("preview")) {
                        if(!(sender instanceof Player player)) throw new IllegalArgumentException("Preview is in-game only.");
                        show(notice,player);sender.sendMessage("Preview shown only to you.");
                    } else { broadcast(notice);sender.sendMessage("Announcement sent."); }
                }
                case "status" -> {
                    if(args.length>1) throw new IllegalArgumentException("Usage: /announcements status");
                    sender.sendMessage("UltimateAnnouncements 1.0.0 | automatic: "+(!settings.enabled()?"disabled":paused?"paused":"enabled")+" | interval: "+settings.interval()+"s | messages: "+settings.notices().size());
                    sender.sendMessage(countdown.active()?"Restart in "+countdown.remaining()+" seconds.":"No restart is counting down.");
                }
                default -> sender.sendMessage("/announcements <list|send <id>|preview <id>|pause|resume|reload|status>");
            }
        } catch(IllegalArgumentException|IllegalStateException ex) { sender.sendMessage(ex.getMessage()); }
        catch(Exception ex) { sender.sendMessage("Could not complete the command: "+ex.getMessage());getLogger().warning(ex.toString()); }
        return true;
    }
    private void requireCount(String[] args,int count) { if(args.length!=count) throw new IllegalArgumentException("Wrong arguments. Use /announcements for status; /announcements list for IDs."); }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        String permission=command.getName().equals("restartcountdown")?"ultimateannouncements.restart":"ultimateannouncements.manage";
        if(!sender.hasPermission(permission)) return List.of();
        List<String> options=List.of();
        if(command.getName().equals("restartcountdown") && args.length==1) options=List.of("10","30","60","cancel","status");
        else if(command.getName().equals("announcements")) {
            if(args.length==1) options=List.of("list","send","preview","pause","resume","reload","status");
            else if(args.length==2 && (args[0].equalsIgnoreCase("send")||args[0].equalsIgnoreCase("preview"))) options=settings.notices().stream().map(Notice::id).toList();
        }
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
