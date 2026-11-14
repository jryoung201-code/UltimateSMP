package shop.blinksmp.network;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class PolicyTest {
    @Test void negativeGrid(){assertEquals(-1,Region.at("Overworld",-0.01,0).x());assertEquals(-1,Region.at("Overworld",-144,0).x());assertEquals(-2,Region.at("Overworld",-144.01,0).x());assertEquals(0,Region.at("Overworld",143.999,0).x());assertEquals(1,Region.at("Overworld",144,0).x());}
    @Test void ids(){assertEquals("Nether",Region.dimensionOf("BlinkSMP-Nether-01"));for(String bad:List.of("BlinkSMP-Nether-1","BlinkSMP-Nether-00","BlinkSMP-Overworld-100","BlinkSMP-overworld-01","other"))assertThrows(IllegalArgumentException.class,()->Region.dimensionOf(bad));}
    @Test void nonfiniteRejected(){assertThrows(IllegalArgumentException.class,()->Region.at("Overworld",Double.NaN,0));assertThrows(IllegalArgumentException.class,()->Region.at("End",Double.POSITIVE_INFINITY,0));}
    @Test void tickets()throws Exception{Protocol p=new Protocol(UUID.randomUUID(),UUID.randomUUID(),"BlinkSMP-Overworld-02");assertEquals(p,Protocol.read(p.bytes()));assertThrows(java.io.IOException.class,()->Protocol.read(Arrays.copyOf(p.bytes(),p.bytes().length+1)));assertThrows(java.io.IOException.class,()->Protocol.read(new byte[257]));}
    @Test void recognizesCompletedHandoffWithoutOverwritingDestination() {
        assertTrue(new NetworkStore.Route("active","B","B",null,null).destinationHasState("B"));
        assertTrue(new NetworkStore.Route("offline","B",null,null,null).destinationHasState("B"));
        assertFalse(new NetworkStore.Route("handoff","A","A","B",UUID.randomUUID()).destinationHasState("B"));
        assertFalse(new NetworkStore.Route("active","A","A",null,null).destinationHasState("B"));
    }
    @Test void routing(){var r=new NetworkStore.Route("handoff","A","A","B",UUID.randomUUID());assertEquals("B",r.loginServer());assertTrue(r.permits("A","B"));assertFalse(r.permits("X","B"));assertFalse(r.permits("A","C"));assertFalse(new NetworkStore.Route("active","A","A",null,null).permits("A","B"));}
}

