package shop.blinksmp.network.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldEditPolicyTest {
    @Test void spawnEditorDoesNotNeedSurvivalRegionOwnership() {
        assertTrue(WorldEditPolicy.allowed(false,true,true,true,false));
        assertFalse(WorldEditPolicy.allowed(false,true,true,false,false));
        assertFalse(WorldEditPolicy.allowed(false,true,false,true,false));
    }
    @Test void editPermissionNeverBypassesFencingOrSurvivalBoundaries() {
        assertFalse(WorldEditPolicy.allowed(true,true,true,true,true));
        assertFalse(WorldEditPolicy.allowed(false,false,true,true,false));
        assertTrue(WorldEditPolicy.allowed(false,false,false,false,true));
    }
}
