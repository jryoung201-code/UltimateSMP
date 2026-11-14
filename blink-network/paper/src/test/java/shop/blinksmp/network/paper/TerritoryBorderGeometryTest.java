package shop.blinksmp.network.paper;

import org.junit.jupiter.api.Test;
import shop.blinksmp.network.Region;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TerritoryBorderGeometryTest {
    private static final String A = "BlinkSMP-Overworld-01", B = "BlinkSMP-Overworld-02";
    @Test void drawsOnlyEdgesWhereOwnersDiffer() {
        Map<Region, String> owners = Map.of(
            new Region("Overworld", 0, 0), A,
            new Region("Overworld", 1, 0), A,
            new Region("Overworld", 0, 1), B
        );
        var faces = TerritoryBorderGeometry.near(owners,A,"Overworld",80,142,24);
        assertTrue(faces.stream().anyMatch(f -> !f.xFixed() && f.fixed() == 144 && !f.unassigned()));
        assertFalse(TerritoryBorderGeometry.near(owners,A,"Overworld",142,70,24).stream()
            .anyMatch(f -> f.xFixed() && f.fixed() == 144));
    }
    @Test void negativesAndUnassignedEdges() {
        Map<Region,String> owners = Map.of(new Region("Overworld",-3,-2), A);
        var faces = TerritoryBorderGeometry.near(owners,A,"Overworld",-293.5,-144.5,24);
        assertTrue(faces.stream().anyMatch(f -> !f.xFixed() && f.fixed() == -144 && f.unassigned()));
        assertEquals(0, TerritoryBorderGeometry.near(owners,A,"Overworld",-310,-180,8).size());
    }
    @Test void doesntRenderWhenViewerOwnsNoNearbyTerritory() {
        Map<Region,String> owners = Map.of(new Region("Overworld",0,0), B);
        assertTrue(TerritoryBorderGeometry.near(owners,A,"Overworld",2,2,24).isEmpty());
        assertTrue(TerritoryBorderGeometry.near(owners,A,"Overworld",2,2,100).isEmpty());
        assertTrue(TerritoryBorderGeometry.near(owners,A,"Nether",2,2,24).isEmpty());
    }
}

