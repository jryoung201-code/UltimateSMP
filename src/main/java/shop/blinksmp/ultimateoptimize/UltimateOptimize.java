package shop.blinksmp.ultimateoptimize;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class UltimateOptimize extends JavaPlugin implements Listener, TabCompleter {
    private final Map<UUID, Distances> tracked = new HashMap<>();
    private BukkitTask task;
    private PressurePolicy policy;
    private int normalView, normalSimulation, pressureView, pressureSimulation;
    private long stepNanos, warmupUntil;
    private Set<String> excluded;
    private boolean paused;

    private static final class Distances {
        final int originalView, originalSimulation;
        int lastView, lastSimulation;
        long nextStep;
        Distances(World world) {
            originalView = lastView = world.getViewDistance();
            originalSimulation = lastSimulation = world.getSimulationDistance();
        }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        try { configure(); }
        catch (IllegalArgumentException ex) {
            getLogger().severe("Configuration error: " + ex.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("ultimateperf")).setTabCompleter(this);
        start();
        getLogger().info("Enabled: normal view/simulation " + normalView + "/" + normalSimulation
                + "; pressure " + pressureView + "/" + pressureSimulation + ". Use /uperf status.");
    }

    private void configure() {
        // Parse and validate into locals before replacing the active configuration.
        int nv = distance("normal.view-distance"), ns = distance("normal.simulation-distance");
        int pv = distance("pressure.view-distance"), ps = distance("pressure.simulation-distance");
        if (ns > nv || ps > pv || pv > nv || ps > ns)
            throw new IllegalArgumentException("Simulation must not exceed view; pressure must not exceed normal.");
        int stepSeconds = getConfig().getInt("change-interval-seconds", 30);
        if (stepSeconds < 10 || stepSeconds > 600)
            throw new IllegalArgumentException("change-interval-seconds must be 10..600");
        PressurePolicy p = new PressurePolicy(
                getConfig().getDouble("pressure.heap-percent", 80),
                getConfig().getDouble("recovery.heap-percent", 65),
                getConfig().getDouble("pressure.mspt", 45),
                getConfig().getDouble("recovery.mspt", 35),
                getConfig().getInt("pressure.consecutive-samples", 3),
                getConfig().getInt("recovery.consecutive-samples", 12));
        normalView = nv; normalSimulation = ns; pressureView = pv; pressureSimulation = ps;
        stepNanos = stepSeconds * 1_000_000_000L;
        excluded = new HashSet<>(getConfig().getStringList("excluded-worlds"));
        policy = p;
        warmupUntil = System.nanoTime() + 60_000_000_000L;
    }

    private int distance(String key) {
        int n = getConfig().getInt(key);
        if (n < 2 || n > 32) throw new IllegalArgumentException(key + " must be 2..32");
        return n;
    }

    private void start() {
        task = Bukkit.getScheduler().runTaskTimer(this, this::sample, 20L, 200L);
    }

    private void sample() {
        if (paused) return;
        long now = System.nanoTime();
        boolean before = policy.pressured();
        if (now >= warmupUntil) policy.sample(heapPercent(), Bukkit.getAverageTickTime());
        if (before != policy.pressured()) getLogger().info(policy.pressured()
                ? "Sustained pressure: gradually reducing chunk distances."
                : "Sustained recovery: gradually restoring normal chunk distances.");
        for (World world : Bukkit.getWorlds()) {
            if (excluded.contains(world.getName())) continue;
            Distances d = tracked.computeIfAbsent(world.getUID(), id -> new Distances(world));
            if (now < d.nextStep) continue;
            int targetView = Math.min(d.originalView, policy.pressured() ? pressureView : normalView);
            int targetSim = Math.min(Math.min(d.originalSimulation, targetView),
                    policy.pressured() ? pressureSimulation : normalSimulation);
            // Never raise a world above its original settings. Only one chunk step per interval.
            int view = PressurePolicy.step(world.getViewDistance(), targetView);
            int sim = PressurePolicy.step(world.getSimulationDistance(), targetSim);
            if (sim < world.getSimulationDistance()) world.setSimulationDistance(sim);
            if (view != world.getViewDistance()) world.setViewDistance(view);
            if (sim > world.getSimulationDistance()) world.setSimulationDistance(sim);
            d.lastView = world.getViewDistance(); d.lastSimulation = world.getSimulationDistance();
            d.nextStep = now + stepNanos;
        }
    }

    private static double heapPercent() {
        Runtime rt = Runtime.getRuntime();
        return 100.0 * (rt.totalMemory() - rt.freeMemory()) / rt.maxMemory();
    }

    private void restore() {
        for (World world : Bukkit.getWorlds()) {
            Distances d = tracked.get(world.getUID());
            if (d == null) continue;
            // Respect settings changed by another administrator/plugin since our last change.
            if (world.getViewDistance() == d.lastView) world.setViewDistance(d.originalView);
            if (world.getSimulationDistance() == d.lastSimulation)
                world.setSimulationDistance(d.originalSimulation);
        }
        tracked.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(WorldUnloadEvent event) { tracked.remove(event.getWorld().getUID()); }

    @Override public void onDisable() {
        if (task != null) task.cancel();
        restore();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ultimateoptimize.admin")) {
            sender.sendMessage("You do not have permission to use this command."); return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                Runtime rt = Runtime.getRuntime();
                sender.sendMessage("UltimateOptimize 1.0.0 | " + (paused ? "PAUSED" : policy.pressured() ? "PRESSURE" : "NORMAL"));
                sender.sendMessage(String.format(Locale.ROOT, "Heap used: %d / %d MiB (%.1f%%) | tick: %.1f ms",
                        (rt.totalMemory()-rt.freeMemory())/1048576, rt.maxMemory()/1048576,
                        heapPercent(), Bukkit.getAverageTickTime()));
                for (World w : Bukkit.getWorlds()) sender.sendMessage(w.getName() + ": view "
                        + w.getViewDistance() + ", simulation " + w.getSimulationDistance()
                        + (excluded.contains(w.getName()) ? " (excluded)" : ""));
                sender.sendMessage("Heap is not total process RAM. Bedrock network latency is not measured.");
            }
            case "pause" -> {
                paused = true; restore();
                sender.sendMessage("Optimization paused; original distances restored.");
            }
            case "resume" -> {
                paused = false; policy.reset(); warmupUntil = System.nanoTime() + 60_000_000_000L;
                sender.sendMessage("Optimization resumed. Distances adjust gradually.");
            }
            case "reload" -> {
                reloadConfig();
                try {
                    configure();
                    // Restore newly excluded worlds without resetting all active worlds.
                    for (World w : Bukkit.getWorlds()) {
                        if (!excluded.contains(w.getName())) continue;
                        Distances d = tracked.remove(w.getUID());
                        if (d != null) {
                            if (w.getViewDistance() == d.lastView) w.setViewDistance(d.originalView);
                            if (w.getSimulationDistance() == d.lastSimulation) w.setSimulationDistance(d.originalSimulation);
                        }
                    }
                    sender.sendMessage("Configuration reloaded.");
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage("Configuration rejected; previous settings remain active: " + ex.getMessage());
                }
            }
            default -> sender.sendMessage("/" + label + " <status|pause|resume|reload>");
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("ultimateoptimize.admin") || args.length != 1) return List.of();
        return List.of("status", "pause", "resume", "reload").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
