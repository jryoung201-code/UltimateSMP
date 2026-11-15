package shop.blinksmp.network;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class AuxiliaryPositionTest {
 @Test void invalidWorldsAndNonfiniteCoordinatesAreRejected(){assertThrows(IllegalArgumentException.class,()->new AuxiliaryPosition("../world",0,70,0,0,0));assertThrows(IllegalArgumentException.class,()->new AuxiliaryPosition("spawn",0,Double.NaN,0,0,0));assertThrows(IllegalArgumentException.class,()->new AuxiliaryPosition("spawn",0,70,0,0,120));}
 @Test void freshJoinRtpClearsAuxiliaryReturnWithoutChangingInventory(){String original="version: 1\ndimension: Overworld\nx: 10\ny: 70\nz: 20\nauxiliary-world: ultimate_spawn\nauxiliary-x: 1.5\nauxiliary-y: 80\nauxiliary-z: 1.5\nauxiliary-yaw: 90\nauxiliary-pitch: 0\ninventory: EXACT_NBT\nender: EXACT_ENDER\n";String result=new String(SnapshotPosition.relocate(original.getBytes(java.nio.charset.StandardCharsets.UTF_8),"Overworld",100,75,200),java.nio.charset.StandardCharsets.UTF_8);assertFalse(result.contains("auxiliary-"));assertTrue(result.endsWith("inventory: EXACT_NBT\nender: EXACT_ENDER\n"));assertTrue(result.contains("x: 100.0\n"));}
}
