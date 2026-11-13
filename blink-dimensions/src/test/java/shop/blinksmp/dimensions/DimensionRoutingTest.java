package shop.blinksmp.dimensions;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DimensionRoutingTest {
    @Test void netherReturnAndEntry() {
        assertEquals("Nether", DimensionRouting.target("Overworld", "NETHER_PORTAL"));
        assertEquals("Overworld", DimensionRouting.target("Nether", "NETHER_PORTAL"));
        assertNull(DimensionRouting.target("End", "NETHER_PORTAL"));
    }

    @Test void endEntryAndReturn() {
        assertEquals("End", DimensionRouting.target("Overworld", "END_PORTAL"));
        assertEquals("Overworld", DimensionRouting.target("End", "END_PORTAL"));
        assertNull(DimensionRouting.target("Nether", "END_PORTAL"));
        assertNull(DimensionRouting.target("End", "END_GATEWAY"));
    }

    @Test void canonicalAndWorldEnvironment() {
        assertEquals("Overworld", DimensionRouting.environment("NORMAL"));
        assertEquals("Nether", DimensionRouting.environment("NETHER"));
        assertEquals("End", DimensionRouting.environment("THE_END"));
        assertNull(DimensionRouting.environment("CUSTOM"));
        assertEquals("Nether", DimensionRouting.canonical("nEtHeR"));
        assertNull(DimensionRouting.canonical("unknown"));
    }
}
