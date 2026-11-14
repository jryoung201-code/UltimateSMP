package shop.blinksmp.network.paper;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import shop.blinksmp.network.Region;
import java.util.Map;

/** Local, per-player visual marker; never sends particles to unrelated players. */
final class TerritoryBorderParticles {
    private final int radius, horizontalStep, verticalStep, height;
    private final Particle.DustOptions otherServer, unassigned;
    private final boolean enabled;

    TerritoryBorderParticles(FileConfiguration config) {
        enabled = config.getBoolean("particle-border.enabled", true);
        radius = clamp(config.getInt("particle-border.distance", 24), 8, 48);
        horizontalStep = clamp(config.getInt("particle-border.horizontal-spacing", 3), 2, 8);
        verticalStep = clamp(config.getInt("particle-border.vertical-spacing", 2), 1, 4);
        height = clamp(config.getInt("particle-border.height", 8), 4, 16);
        otherServer = dust(config.getString("particle-border.server-color", "#33CCFF"), 0x33CCFF);
        unassigned = dust(config.getString("particle-border.unassigned-color", "#FF5555"), 0xFF5555);
    }
    boolean enabled() { return enabled; }
    private static int clamp(int v, int low, int high) { return Math.max(low, Math.min(high, v)); }
    private static Particle.DustOptions dust(String hex, int fallback) {
        int rgb = fallback;
        if (hex != null && hex.matches("#[0-9a-fA-F]{6}")) rgb = Integer.parseInt(hex.substring(1), 16);
        return new Particle.DustOptions(Color.fromRGB(rgb), 1.0f);
    }
    void show(Player player, Map<Region, String> owners, String server, String dimension) {
        if (!enabled) return;
        double px = player.getLocation().getX(), pz = player.getLocation().getZ();
        int count = 0;
        for (TerritoryBorderGeometry.Face face :
             TerritoryBorderGeometry.near(owners, server, dimension, px, pz, radius)) {
            double along = face.xFixed() ? pz : px;
            double low = Math.max(face.from(), along - radius);
            double high = Math.min(face.to(), along + radius);
            Particle.DustOptions particle = face.unassigned() ? unassigned : otherServer;
            for (double point = Math.ceil(low / horizontalStep) * horizontalStep;
                 point <= high && count < 240; point += horizontalStep) {
                double x = face.xFixed() ? face.fixed() : point;
                double z = face.xFixed() ? point : face.fixed();
                if ((px - x) * (px - x) + (pz - z) * (pz - z) > radius * radius) continue;
                for (int dy = -2; dy <= height - 2 && count < 240; dy += verticalStep) {
                    double y = player.getLocation().getY() + dy;
                    if (y <= player.getWorld().getMinHeight() || y >= player.getWorld().getMaxHeight()) continue;
                    player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, particle);
                    count++;
                }
            }
        }
    }
}


