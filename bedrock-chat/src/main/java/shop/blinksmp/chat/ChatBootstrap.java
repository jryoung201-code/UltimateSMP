package shop.blinksmp.chat;

import java.io.IOException;
import java.util.Properties;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Starts even without PacketEvents. Packet classes live only in the reflectively loaded relay. */
public final class ChatBootstrap extends JavaPlugin implements Listener {
    private Relay relay;
    private String status="Starting";

    @Override public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this,this);
        boolean packetRestart=false;
        if(getConfig().getBoolean("auto-install-packetevents",true)) {
            packetRestart=install("PacketEvents","bundled/PacketEvents.jar","bundled/packetevents.properties",
                    new AdminInstaller.Spec("packetevents","io.github.retrooper.packetevents.PacketEventsPlugin","2.14.0"));
        }
        if(getConfig().getBoolean("auto-install-admin",true)) {
            getLogger().info("Bundled admin panel owner: bd33980d-5b8d-4f1c-becc-0520f92c4a0d (RealJR_25736).");
            install("UltimateGUI","bundled/UltimateGUI.jar","bundled/admin.properties",
                    new AdminInstaller.Spec("UltimateGUI","shop.blinksmp.gui.UltimateGUI","1.0.2"));
        }
        Plugin packets=Bukkit.getPluginManager().getPlugin("packetevents");
        if(packetRestart || packets==null || !packets.isEnabled()) {
            status="WAITING FOR PACKETEVENTS — restart once after installation/update; check console for installer errors";
            getLogger().warning(status); return;
        }
        try {
            Class<?> implementation=Class.forName("shop.blinksmp.chat.ChatRelay",true,getClassLoader());
            relay=(Relay)implementation.getConstructor(JavaPlugin.class).newInstance(this);
            relay.start(); status="ACTIVE";
        } catch(ReflectiveOperationException|RuntimeException|LinkageError ex) {
            status="RELAY UNAVAILABLE — check console";
            if(relay!=null) { try { relay.stop(); } catch(RuntimeException|LinkageError ignored) {} relay=null; }
            getLogger().severe("Could not start Bedrock chat relay: "+ex);
        }
    }
    private boolean install(String label,String resource,String manifestName,AdminInstaller.Spec spec) {
        getLogger().info("Checking bundled "+label+" "+spec.version()+" for automatic installation.");
        try(var jar=getResource(resource); var manifest=getResource(manifestName)) {
            if(jar==null || manifest==null) throw new IOException("Bundled resources missing; use the packaged JAR");
            Properties properties=new Properties(); properties.load(manifest);
            if(!spec.version().equals(properties.getProperty("version"))) throw new IOException("Bundled version mismatch");
            AdminInstaller.Result result=AdminInstaller.installPlugin(getDataFolder().toPath().getParent(),
                    Bukkit.getUpdateFolderFile().toPath(),jar.readAllBytes(),properties.getProperty("sha256",""),spec);
            getLogger().info(result.message()); return result.restartRequired();
        } catch(IOException|RuntimeException ex) {
            getLogger().severe(label+" auto-install failed: "+ex.getMessage()); return false;
        }
    }
    @EventHandler public void dependencyDisabled(PluginDisableEvent event) {
        if(relay!=null && (event.getPlugin().getName().equals("packetevents")||event.getPlugin().getName().equals("floodgate"))) {
            try { relay.stop(); } finally { relay=null; status="DEPENDENCY DISABLED — restart to restore chat relay"; }
        }
    }
    @Override public void onDisable() { if(relay!=null) { relay.stop(); relay=null; } }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(!sender.hasPermission("ultimatebedrockchat.admin")) { sender.sendMessage("You do not have permission."); return true; }
        sender.sendMessage("UltimateBedrockChat 1.0.4 | "+status);
        if(relay!=null) relay.status(sender);
        return true;
    }
}

