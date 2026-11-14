package shop.blinksmp.network.paper;

import java.util.Arrays;

/** Explicit, lossless compatibility for Paper's historic and current player inventory layouts. */
final class InventoryLayout {
    private InventoryLayout() { }
    static void validate(int playerSlots,int enderSlots) {
        // Earlier builds of BlinkNetwork saved 41. Minecraft 1.21.11 has
        // two additional equipment slots (body and saddle), for a total of 43.
        if ((playerSlots != 41 && playerSlots != 43) || enderSlots != 27)
            throw new IllegalArgumentException("Unsupported snapshot inventory layout: playerSlots="
                +playerSlots+", enderSlots="+enderSlots);
    }
    static <T> T[] adapt(T[] saved,int targetSlots) {
        if (targetSlots != 41 && targetSlots != 43)
            throw new IllegalArgumentException("Unsupported target inventory layout: "+targetSlots);
        if (saved.length > targetSlots)
            throw new IllegalArgumentException("Target inventory too small: saved="+saved.length
                +", target="+targetSlots+"; refusing to discard items");
        // Preserve all original slots in order. The new body and saddle slots
        // are left empty when restoring a legacy 41-slot snapshot on 1.21.11.
        return Arrays.copyOf(saved,targetSlots);
    }
}

