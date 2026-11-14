package shop.blinksmp.network.velocity;

import org.junit.jupiter.api.Test;
import shop.blinksmp.network.NetworkStore;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QueuePermitTest {
    @Test void reservationIsBoundedAndMatchesOnlyTheSelectedWorld(){
        QueuePermit p=new QueuePermit("BlinkSMP-Overworld-02",100,"origin:false");
        assertTrue(p.allows("BlinkSMP-Overworld-02",99));assertFalse(p.allows("BlinkSMP-Overworld-01",99));assertFalse(p.allows("BlinkSMP-Overworld-02",100));
    }
    @Test void queueAdmissionCannotBypassLiveActiveSession(){
        var active=new NetworkStore.Route("active","world","world",null,null);
        assertFalse(QueuePermit.routeAllows(active,"world","world"));
        assertTrue(QueuePermit.routeAllows(new NetworkStore.Route("offline","world",null,null,null),"world","world"));
        assertFalse(QueuePermit.routeAllows(new NetworkStore.Route("offline","world",null,null,null),"world","other"));
    }
    @Test void validatedHandoffAndBootstrapCanContinueWithoutAnyQueueBackend(){
        var handoff=new NetworkStore.Route("handoff","source","source","target",UUID.randomUUID());
        assertTrue(QueuePermit.routeAllows(handoff,"target","target"));assertFalse(QueuePermit.routeAllows(handoff,"target","source"));
        assertTrue(QueuePermit.routeAllows(null,"bootstrap","bootstrap"));
    }
}
