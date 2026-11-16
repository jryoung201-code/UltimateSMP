package shop.blinksmp.network;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InitialJoinLandingTest {
    static final String A="BlinkSMP-Overworld-01",B="BlinkSMP-Overworld-02";
    @Test void reportedForeignLocalSpawnSelectsServerTwosOwnedRegion(){
        var owners=Map.of(new Region("Overworld",-1,0),A,new Region("Overworld",0,0),B);
        var points=InitialJoinLanding.candidates(owners,B,-5.5,9.5,null);
        assertEquals(List.of(new TransferSafety.Point(72,72)),points);
        assertEquals(A,owners.get(new Region("Overworld",-1,0)));
    }
    @Test void neverChoosesAnotherDimensionOrServer(){
        var owners=Map.of(new Region("Overworld",0,0),A,new Region("Nether",0,0),B,new Region("End",0,0),B);
        assertTrue(InitialJoinLanding.candidates(owners,B,0,0,null).isEmpty());
    }
    @Test void negativeOwnedRegionIsHandledWithFloorCoordinates(){
        var owners=Map.of(new Region("Overworld",-1,-1),B);
        var points=InitialJoinLanding.candidates(owners,B,8,8,null);
        assertEquals(List.of(new TransferSafety.Point(-72,-72)),points);
    }
    @Test void bordersCannotClampACandidateIntoForeignOwnership(){
        var owners=Map.of(new Region("Overworld",0,0),A,new Region("Overworld",1,0),B);
        var border=new NetworkStore.Border("Overworld",72,72,40,true,1);
        assertTrue(InitialJoinLanding.candidates(owners,B,72,72,border).isEmpty());
    }
    @Test void searchIsBoundedAndChunksAreUnique(){
        Map<Region,String> owners=new HashMap<>();for(int x=0;x<50;x++)owners.put(new Region("Overworld",x,0),B);
        var points=InitialJoinLanding.candidates(owners,B,72,72,null);
        assertEquals(16,points.size());assertEquals(16,points.stream().map(p->((int)p.x()>>4)+":"+((int)p.z()>>4)).distinct().count());
        assertTrue(points.stream().allMatch(p->B.equals(owners.get(Region.at("Overworld",p.x(),p.z())))));
    }
    @Test void invalidCoordinatesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->InitialJoinLanding.candidates(Map.of(),B,Double.NaN,72,null));
    }
}
