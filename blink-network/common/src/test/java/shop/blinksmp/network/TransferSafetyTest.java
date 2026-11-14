package shop.blinksmp.network;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferSafetyTest {
    static final String A="BlinkSMP-Overworld-01",B="BlinkSMP-Overworld-02";
    @Test void crossingBothDirectionsLandsInsideTheCorrectOwner(){
        Map<Region,String> m=Map.of(new Region("Overworld",0,0),A,new Region("Overworld",1,0),B);
        var east=TransferSafety.landing(m,B,"Overworld",144.001,70,null);
        assertEquals(146,east.x());assertEquals(B,m.get(Region.at("Overworld",east.x(),east.z())));
        var west=TransferSafety.landing(m,A,"Overworld",143.999,70,null);
        assertEquals(142,west.x());assertEquals(A,m.get(Region.at("Overworld",west.x(),west.z())));
    }
    @Test void negativeSeamsAndDiagonalCornerStayInSelectedRegion(){
        Map<Region,String> m=Map.of(new Region("Overworld",-1,-1),A,new Region("Overworld",0,-1),B);
        var p=TransferSafety.landing(m,A,"Overworld",-0.001,-0.001,null);
        assertEquals(-2,p.x());assertEquals(-2,p.z());assertEquals(new Region("Overworld",-1,-1),Region.at("Overworld",p.x(),p.z()));
    }
    @Test void internalEdgesWithTheSameOwnerAreNotMoved(){
        Map<Region,String> m=Map.of(new Region("Overworld",0,0),A,new Region("Overworld",1,0),A);
        assertEquals(143.99,TransferSafety.landing(m,A,"Overworld",143.99,70,null).x());
        assertEquals(144.01,TransferSafety.landing(m,A,"Overworld",144.01,70,null).x());
    }
    @Test void worldBorderHasAHitboxMargin(){
        Map<Region,String> m=Map.of(new Region("Overworld",0,0),A);
        var b=new NetworkStore.Border("Overworld",72,72,144,true,1);
        var p=TransferSafety.landing(m,A,"Overworld",143.999,0.001,b);
        assertEquals(142,p.x());assertEquals(2,p.z());assertTrue(b.contains(p.x(),p.z()));
    }
    @Test void unassignedAndWrongDimensionNeverSilentlyRouteElsewhere(){
        Map<Region,String> m=Map.of(new Region("Overworld",0,0),A);
        assertThrows(IllegalArgumentException.class,()->TransferSafety.landing(m,B,"Overworld",70,70,null));
        assertThrows(IllegalArgumentException.class,()->TransferSafety.landing(m,A,"Overworld",144,70,null));
        assertThrows(IllegalArgumentException.class,()->TransferSafety.landing(m,A,"Nether",70,70,null));
    }
    @Test void cancelledCorrectionsAndRotationCannotTriggerAutomaticHandoffs(){
        assertFalse(TransferSafety.automaticAllowed(1000,0,143,70,143,70));
        assertFalse(TransferSafety.automaticAllowed(2999,3000,143,70,144,70));
        assertTrue(TransferSafety.automaticAllowed(3000,3000,143,70,144,70));
    }
    @Test void proxyCircuitSurvivesReconnectAndRejectsTicketReplay(){
        var guard=new TransferThrottle();UUID player=UUID.randomUUID(),ticket=UUID.randomUUID();
        assertTrue(guard.admit(player,ticket,1000));assertFalse(guard.admit(player,ticket,1001));
        assertFalse(guard.admit(player,UUID.randomUUID(),1002)); // A new client connection with the same UUID.
        assertTrue(guard.admit(UUID.randomUUID(),UUID.randomUUID(),1002));
        assertFalse(guard.admit(player,ticket,5000));assertTrue(guard.admit(player,UUID.randomUUID(),5000));
    }
    @org.junit.jupiter.api.Test void physicalBackendCanOwnNetherAndEnd(){
        String server="BlinkSMP-Overworld-01";
        for(String dim:java.util.List.of("Nether","End")){
            var owners=java.util.Map.of(new Region(dim,0,0),server);
            var p=TransferSafety.landing(owners,server,dim,0.01,20,null);
            org.junit.jupiter.api.Assertions.assertEquals(2.0,p.x());
        }
    }
}
