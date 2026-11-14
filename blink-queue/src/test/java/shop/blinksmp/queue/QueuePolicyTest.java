package shop.blinksmp.queue;

import org.junit.jupiter.api.Test;
import java.util.*;
import net.kyori.adventure.text.Component;
import static org.junit.jupiter.api.Assertions.*;

class QueuePolicyTest {
    @Test void deadlineIsFixedAcrossRetriesAndDoesNotUseWallClock(){
        WaitBudget budget=new WaitBudget(1_000_000_000L,180_000_000_000L);
        assertEquals(180,budget.secondsLeft(1_000_000_000L));
        assertEquals(1,budget.secondsLeft(180_999_999_999L));
        assertFalse(budget.expired(180_999_999_999L));assertTrue(budget.expired(181_000_000_000L));
        assertEquals(0,budget.secondsLeft(190_000_000_000L));
    }
    @Test void monotonicClockMayHaveNegativeOrigin(){
        WaitBudget budget=new WaitBudget(-200_000_000_000L,180_000_000_000L);
        assertFalse(budget.expired(-21_000_000_000L));assertTrue(budget.expired(-20_000_000_000L));
    }
    @Test void defaultsAreBoundedAndNoStartEndpointIsInvented(){
        QueueConfig c=QueueConfig.read(new Properties(),null);
        assertEquals(180,c.waitSeconds());assertEquals(100,c.capacity());assertNull(c.startUri());
        assertEquals("BlinkSMP-Queue-01",c.queue());
    }
    @Test void badUrlsAndEmbeddedCredentialsAreRejected(){
        for(String url:List.of("http://example.test/start","https://user:password@example.test/start","https://example.test/start#fragment","file:///tmp/server","https:/no-host")){
            assertThrows(IllegalArgumentException.class,()->QueueConfig.read(new Properties(),url));
        }
    }
    @Test void environmentWinsAndHttpsStartEndpointIsPreserved(){
        Properties p=new Properties();p.setProperty("queue-start-url","https://old.example.test/start");
        assertEquals("https://new.example.test/start",QueueConfig.read(p,"https://new.example.test/start").startUri().toString());
        assertNull(QueueConfig.read(p,"").startUri());
    }
    @Test void capacityPollAndTimeoutCannotBeUnbounded(){
        for(var pair:Map.of("max-wait-seconds","0","max-waiting-players","1000000","poll-seconds","0","ping-timeout-seconds","999","start-retry-seconds","1").entrySet()){
            Properties p=new Properties();p.setProperty(pair.getKey(),pair.getValue());assertThrows(IllegalArgumentException.class,()->QueueConfig.read(p,null));
        }
    }
    @Test void queueCannotBeTheDefaultGameplayServer(){
        Properties p=new Properties();p.setProperty("queue-server","world");p.setProperty("default-server","world");
        assertThrows(IllegalArgumentException.class,()->QueueConfig.read(p,null));
    }
    @Test void moderationAndUnsafeLoginRejectionsNeverBecomeQueueRecovery(){
        assertTrue(FailurePolicy.network(Optional.empty()));
        assertTrue(FailurePolicy.network(Optional.of(Component.translatable("velocity.error.internal-server-connection-error"))));
        assertFalse(FailurePolicy.network(Optional.of(Component.text("Banned"))));
        assertFalse(FailurePolicy.network(Optional.of(Component.text("Data could not be restored safely"))));
        assertFalse(FailurePolicy.network(Optional.of(Component.translatable("multiplayer.disconnect.banned"))));
    }
}
