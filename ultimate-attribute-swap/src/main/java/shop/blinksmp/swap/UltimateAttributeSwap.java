package shop.blinksmp.swap;

import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Uses the switch verified in Paper's ver/1.21.11 source; no packet or damage rewrites. */
public final class UltimateAttributeSwap extends JavaPlugin implements Listener,TabCompleter {
    private final EquipmentSwitch setting=new EquipmentSwitch();
    private Method globalGet;
    private boolean restoring;
    private Object currentSettings() throws ReflectiveOperationException {
        Object global=Objects.requireNonNull(globalGet.invoke(null),"Paper global configuration is not initialized");
        return Objects.requireNonNull(global.getClass().getField("unsupportedSettings").get(global),"Paper unsupported settings are not initialized");
    }
    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            if(!"1.21.11".equals(Bukkit.getMinecraftVersion())) throw new IllegalStateException("This build supports Paper 1.21.11 only.");
            Class<?> global=Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            globalGet=global.getMethod("get");restoring=getConfig().getBoolean("enabled",true);
            Object settings=currentSettings();EquipmentSwitch.read(settings);
            if(restoring) setting.apply(settings);
            Bukkit.getPluginManager().registerEvents(this,this);Objects.requireNonNull(getCommand("attributeswap")).setTabCompleter(this);
            getLogger().info("Attribute-swap restoration "+(restoring?"ON":"OFF")+" | Paper update-equipment-on-player-actions="+EquipmentSwitch.read(settings));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError ex) {
            getLogger().severe("Cannot apply Paper 1.21.11 attribute-swap setting: "+ex);Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void loaded(ServerLoadEvent event) {
        if(!restoring) return;
        try { setting.apply(currentSettings()); }
        catch(ReflectiveOperationException|RuntimeException ex) { getLogger().severe("Could not reapply after server load: "+ex); }
    }
    @Override public void onDisable() {
        try { setting.restore(); }
        catch(ReflectiveOperationException|RuntimeException ex) { getLogger().warning("Could not restore the previous runtime setting: "+ex); }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.hasPermission("ultimateattributeswap.admin")) { sender.sendMessage("You do not have permission.");return true; }
        if(args.length>1) { sender.sendMessage("Usage: /attributeswap <status|on|off|reload>");return true; }
        String action=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
        try {
            switch(action) {
                case "on" -> { setting.apply(currentSettings());restoring=true;getConfig().set("enabled",true);saveConfig();sender.sendMessage("Vanilla attribute-swap restoration enabled for everyone."); }
                case "off" -> { setting.restore();restoring=false;getConfig().set("enabled",false);saveConfig();sender.sendMessage("Restoration disabled; previous Paper runtime value restored."); }
                case "reload" -> {
                    reloadConfig();boolean desired=getConfig().getBoolean("enabled",true);
                    if(desired) setting.apply(currentSettings());else setting.restore();
                    restoring=desired;sender.sendMessage("Configuration reloaded and applied.");
                }
                case "status" -> {}
                default -> { sender.sendMessage("Usage: /attributeswap <status|on|off|reload>");return true; }
            }
            Object settings=currentSettings();boolean forced=EquipmentSwitch.read(settings);
            sender.sendMessage("UltimateAttributeSwap 1.0.0 | restoration: "+(restoring?"ON":"OFF")+" | Paper forced equipment updates: "+forced);
            if(restoring && (!setting.owns(settings) || forced)) sender.sendMessage("Paper's live setting changed. Run /attributeswap on to reapply restoration.");
            sender.sendMessage("No damage multiplier is used. Swap timing remains vanilla.");
        } catch(ReflectiveOperationException|RuntimeException ex) { sender.sendMessage("Could not access the runtime setting. Check console.");getLogger().severe(ex.toString()); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!sender.hasPermission("ultimateattributeswap.admin") || args.length!=1) return List.of();
        String prefix=args[0].toLowerCase(Locale.ROOT);
        return List.of("status","on","off","reload").stream().filter(s->s.startsWith(prefix)).toList();
    }
}
