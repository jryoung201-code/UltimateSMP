package shop.blinksmp.dimensions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Dimension portals only. BlinkNetwork retains sole ownership of inventory,
 * player snapshots, permissions, region ownership and Velocity handoff tickets.
 * No independent /server or BungeeCord switching occurs here.
 */
public final class BlinkDimensions extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private Plugin network;
    private Method transfer;
    private Method remoteCheck;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("Disabled by config; BlinkNetwork retains portal handling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        network = getServer().getPluginManager().getPlugin("BlinkNetwork");
        if (!(network instanceof JavaPlugin) || !network.isEnabled()) {
            getLogger().severe("BlinkNetwork 0.2.4+ must be enabled on this Paper backend.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            // Reject older builds: otherwise two plugins would try to transfer a
            // single player and could create a handoff loop.
            Method compatibility = network.getClass().getMethod("delegatesPortalsToBlinkDimensions");
            if (!Boolean.TRUE.equals(compatibility.invoke(network)))
                throw new IllegalStateException("Incompatible BlinkNetwork bridge");
            transfer = network.getClass().getMethod("networkHomeTeleport", Player.class,
                    String.class, double.class, double.class, double.class, float.class, float.class);
            remoteCheck = network.getClass().getMethod("isHomeRemote", String.class, double.class, double.class);
        } catch (ReflectiveOperationException | IllegalStateException failure) {
            getLogger().severe("Install matching BlinkNetwork 0.2.4+ Paper JAR: portal bridge unavailable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Objects.requireNonNull(getCommand("blinkdimensions"), "Missing command").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Dimension portal routing enabled. No territory or inventory data is stored by this plugin.");
    }

    @Override public void onDisable() {
        cooldownUntil.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;

        // Cancel vanilla local-world portal creation/teleportation first. Only a
        // verified BlinkNetwork handoff may transport the player's inventory.
        event.setCancelled(true);
        event.setCanCreatePortal(false);
        Player player = event.getPlayer();
        if (!getConfig().getBoolean("enabled", true)) return;

        String source = DimensionRouting.environment(event.getFrom().getWorld().getEnvironment().name());
        String target = DimensionRouting.target(source, event.getCause().name());
        if (target == null) return;

        if (!onConfiguredWorld(player)) {
            tell(player, "This portal is not in the configured network world.");
            return;
        }
        long now = System.currentTimeMillis();
        if (cooldownUntil.getOrDefault(player.getUniqueId(), 0L) > now) return;

        Destination destination = destination(target);
        if (destination == null) {
            tell(player, "The " + target + " arrival hub is disabled. Staff must verify a safe landing pad.");
            return;
        }
        if (handoff(player, destination)) {
            cooldownUntil.put(player.getUniqueId(), now + 1000L * cooldownSeconds());
            tell(player, "Transferring to " + target + "...");
        } else {
            tell(player, "The " + target + " destination is unavailable or unassigned. You remain here.");
        }
    }

    private boolean onConfiguredWorld(Player player) {
        if (!(network instanceof JavaPlugin javaPlugin)) return false;
        String name = javaPlugin.getConfig().getString("world", "");
        return player.getWorld().getName().equals(name);
    }

    private int cooldownSeconds() {
        return Math.max(2, Math.min(300, getConfig().getInt("portal-cooldown-seconds", 15)));
    }

    private Destination destination(String dim) {
        ConfigurationSection section = getConfig().getConfigurationSection("arrivals." + dim);
        if (section == null || !section.getBoolean("enabled", false)) return null;
        for (String field : new String[]{"x", "y", "z", "yaw", "pitch"})
            if (!(section.get(field) instanceof Number)) return null;
        double x = section.getDouble("x"), y = section.getDouble("y"), z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw"), pitch = (float) section.getDouble("pitch");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) >= 29_999_984 || Math.abs(z) >= 29_999_984
                || y < -64 || y > 319 || pitch < -90 || pitch > 90) return null;
        return new Destination(dim, x, y, z, yaw, pitch);
    }

    private boolean handoff(Player player, Destination dest) {
        if (!network.isEnabled()) return false;
        try {
            return Boolean.TRUE.equals(transfer.invoke(network, player, dest.dimension,
                    dest.x, dest.y, dest.z, dest.yaw, dest.pitch));
        } catch (IllegalAccessException | InvocationTargetException e) {
            getLogger().warning("BlinkNetwork rejected a dimension handoff (" + e.getClass().getSimpleName() + ").");
            return false;
        }
    }

    private boolean assigned(Destination dest) {
        if (!network.isEnabled()) return false;
        try {
            return Boolean.TRUE.equals(remoteCheck.invoke(network, dest.dimension, dest.x, dest.z));
        } catch (IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }

    private void tell(Player player, String message) {
        player.sendMessage(ChatColor.AQUA + "[BlinkDimensions] " + ChatColor.YELLOW + message);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("BlinkDimensions 0.1.0; BlinkNetwork bridge=" + (network != null && network.isEnabled() ? "linked" : "unavailable"));
            for (String dim : new String[]{"Overworld", "Nether", "End"}) {
                Destination destination = destination(dim);
                sender.sendMessage(dim + ": " + (destination == null ? "hub disabled/unconfigured"
                        : "hub enabled; region " + (assigned(destination) ? "assigned and remote" : "unavailable or not assigned")));
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            if (!getConfig().getBoolean("enabled", true)) {
                sender.sendMessage("Disabling BlinkDimensions; restart to restore legacy portal handling.");
                getServer().getPluginManager().disablePlugin(this);
            } else sender.sendMessage("BlinkDimensions config reloaded.");
            return true;
        }
        if (args.length != 2 || (!args[0].equalsIgnoreCase("check") && !args[0].equalsIgnoreCase("travel"))) {
            sender.sendMessage("Usage: /blinkdimensions [status|reload|check <dimension>|travel <dimension>]");
            return true;
        }
        String dim = DimensionRouting.canonical(args[1]);
        if (dim == null) {
            sender.sendMessage("Use Overworld, Nether or End.");
            return true;
        }
        Destination dest = destination(dim);
        if (dest == null) {
            sender.sendMessage(dim + " hub is disabled or has invalid coordinates. Verify a safe pad first.");
            return true;
        }
        if (!assigned(dest)) {
            sender.sendMessage(dim + " is not assigned to a reachable different server in BlinkNetwork.");
            return true;
        }
        if (args[0].equalsIgnoreCase("check")) {
            sender.sendMessage(dim + " routing is available; confirm the target pad is safe in the destination world.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Travel must be run by an in-game operator.");
            return true;
        }
        if (!onConfiguredWorld(player)) {
            sender.sendMessage("You are not in the configured network world.");
            return true;
        }
        if (handoff(player, dest)) tell(player, "Transferring to " + dim + "...");
        else tell(player, "Transfer could not begin; no items were moved.");
        return true;
    }

    private record Destination(String dimension, double x, double y, double z, float yaw, float pitch) {}
}
