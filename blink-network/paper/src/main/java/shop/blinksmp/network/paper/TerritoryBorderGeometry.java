package shop.blinksmp.network.paper;

import shop.blinksmp.network.Region;
import java.util.*;

/** Visible edges of owned 9-by-9-chunk regions; no chunk or block access. */
final class TerritoryBorderGeometry {
    private static final int BLOCKS = 144;
    record Face(boolean xFixed, double fixed, double from, double to, boolean unassigned) { }

    static List<Face> near(Map<Region, String> owners, String server, String dimension,
                           double px, double pz, int radius) {
        if (!Double.isFinite(px) || !Double.isFinite(pz) || radius < 1 || radius > 48)
            return List.of();
        int left = (int) Math.floor((px - radius) / BLOCKS);
        int right = (int) Math.floor((px + radius) / BLOCKS);
        int top = (int) Math.floor((pz - radius) / BLOCKS);
        int bottom = (int) Math.floor((pz + radius) / BLOCKS);
        List<Face> faces = new ArrayList<>();
        for (int rx = left; rx <= right; rx++) {
            for (int rz = top; rz <= bottom; rz++) {
                Region region = new Region(dimension, rx, rz);
                if (!server.equals(owners.get(region))) continue;
                add(faces, owners, server, dimension, rx - 1, rz,
                    true, (double) rx * BLOCKS, (double) rz * BLOCKS,
                    (double) (rz + 1) * BLOCKS, px, radius);
                add(faces, owners, server, dimension, rx + 1, rz,
                    true, (double) (rx + 1) * BLOCKS, (double) rz * BLOCKS,
                    (double) (rz + 1) * BLOCKS, px, radius);
                add(faces, owners, server, dimension, rx, rz - 1,
                    false, (double) rz * BLOCKS, (double) rx * BLOCKS,
                    (double) (rx + 1) * BLOCKS, pz, radius);
                add(faces, owners, server, dimension, rx, rz + 1,
                    false, (double) (rz + 1) * BLOCKS, (double) rx * BLOCKS,
                    (double) (rx + 1) * BLOCKS, pz, radius);
            }
        }
        return faces;
    }
    private static void add(List<Face> faces, Map<Region, String> owners, String server,
                            String dimension, int nx, int nz, boolean xFixed,
                            double fixed, double from, double to, double playerAxis, int radius) {
        String neighbor = owners.get(new Region(dimension, nx, nz));
        if (!server.equals(neighbor) && Math.abs(fixed - playerAxis) <= radius)
            faces.add(new Face(xFixed, fixed, from, to, neighbor == null));
    }
}
