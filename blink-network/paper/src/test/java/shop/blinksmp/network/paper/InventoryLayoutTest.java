package shop.blinksmp.network.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryLayoutTest {
    @Test void acceptsCurrentAndLegacyPaperLayouts() {
        assertDoesNotThrow(()->InventoryLayout.validate(43,27));
        assertDoesNotThrow(()->InventoryLayout.validate(41,27));
        assertThrows(IllegalArgumentException.class,()->InventoryLayout.validate(40,27));
        assertThrows(IllegalArgumentException.class,()->InventoryLayout.validate(43,26));
    }
    @Test void preservesEverySlotInCurrentInventory() {
        String[] items=new String[43];
        items[0]="hotbar";items[36]="boots";items[40]="offhand";
        items[41]="body";items[42]="saddle";
        String[] restored=InventoryLayout.adapt(items,43);
        assertArrayEquals(items,restored);
        assertNotSame(items,restored);
    }
    @Test void restoresOlderSnapshotsWithoutItemLoss() {
        String[] old=new String[41];
        old[0]="sword";old[36]="boots";old[40]="offhand";
        String[] restored=InventoryLayout.adapt(old,43);
        assertEquals(43,restored.length);
        assertEquals("sword",restored[0]);
        assertEquals("boots",restored[36]);
        assertEquals("offhand",restored[40]);
        assertNull(restored[41]);assertNull(restored[42]);
    }
    @Test void refusesTruncationOfNewEquipmentSlots() {
        String[] modern=new String[43];
        modern[41]="body armor";modern[42]="saddle";
        assertThrows(IllegalArgumentException.class,()->InventoryLayout.adapt(modern,41));
    }
}


