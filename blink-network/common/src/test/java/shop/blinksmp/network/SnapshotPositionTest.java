package shop.blinksmp.network;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotPositionTest {
    @Test void repairsOnlyTopLevelCoordinatesAndPreservesInventoryExactly(){
        String source="version: 1\ndimension: Overworld\nx: 144.00001\ny: 70.0\nz: 0.0\ninventory: !!binary |\n  Q2FuYXJ5TkJU\nender: KEEP_ENDER\nhealth: 19.0\neffects:\n  one:\n    x: 999\n";
        String result=new String(SnapshotPosition.relocate(source.getBytes(StandardCharsets.UTF_8),"Overworld",12,72,15),StandardCharsets.UTF_8);
        assertTrue(result.contains("x: 12.0\ny: 72.0\nz: 15.0\n"));
        assertEquals(source.substring(source.indexOf("inventory:")),result.substring(result.indexOf("inventory:")));
        assertTrue(result.contains("    x: 999\n"));
    }
    @Test void crlfAndLeadingBlanksSurvive(){
        String source="\r\nversion: 1\r\ndimension: Overworld\r\nx: 144\r\ny: 70\r\nz: 0\r\ninventory: SAME\r\n";
        String result=new String(SnapshotPosition.relocate(source.getBytes(StandardCharsets.UTF_8),"Overworld",12,72,15),StandardCharsets.UTF_8);
        assertTrue(result.startsWith("\r\nversion: 1\r\n"));assertTrue(result.endsWith("inventory: SAME\r\n"));
    }
    @Test void duplicateMissingAndNonfiniteValuesAreRejected(){
        byte[] valid="dimension: Overworld\nx: 1\ny: 1\nz: 1\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,()->SnapshotPosition.relocate("dimension: Overworld\nx: 1\nx: 2\ny: 1\nz: 1\n".getBytes(),"Overworld",1,1,1));
        assertThrows(IllegalArgumentException.class,()->SnapshotPosition.relocate(new byte[]{1},"Overworld",1,1,1));
        assertThrows(IllegalArgumentException.class,()->SnapshotPosition.relocate(valid,"Overworld",Double.NaN,1,1));
        assertThrows(IllegalArgumentException.class,()->SnapshotPosition.relocate(valid,"Overworld",1,Double.POSITIVE_INFINITY,1));
    }
}

