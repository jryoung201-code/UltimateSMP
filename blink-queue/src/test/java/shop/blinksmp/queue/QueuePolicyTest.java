package shop.blinksmp.queue;

import org.junit.jupiter.api.Test;
import java.util.*;
import net.kyori.adventure.text.Component;
import static org.junit.jupiter.api.Assertions.*;

class QueuePolicyTest {
    @Test void deadlineIsFixedAcrossRetries(){
        WaitBudget b=new WaitBudget(1_000_000_000L,180_000_000_000L);
        assertEquals(180,b.secondsLeft(1_000_000_000L));assertEquals(1,b.secondsLeft(180_999_999_999L));
        assertFalse(b.expired(180_999_999_999L));assertTrue(b.expired(181_000_000_000L));assertEquals(0,b.secondsLeft(190_000_000_000L));
    }
    @Test void clockMayHaveNegativeOrigin(){WaitBudget b=new WaitBudget(-200_000_000_000L,180_000_000_000L);assertFalse(b.expired(-21_000_000_000L));assertTrue(b.expired(-20_000_000_000L));}
    @Test void defaultsNeedNoBackendAddressOrApi(){QueueConfig c=QueueConfig.read(new Properties());assertTrue(c.enabled());assertEquals(600,c.waitSeconds());assertEquals(100,c.capacity());}
    @Test void obsoleteQueueBackendAndHostSettingsAreIgnored(){
        Properties p=new Properties();p.setProperty("queue-server","missing-server");p.setProperty("queue-start-url","invalid://removed");p.setProperty("default-server","missing-world");
        assertEquals(QueueConfig.read(new Properties()),QueueConfig.read(p));
    }
    @Test void waitCapacityAndPollCannotBeUnbounded(){
        for(var pair:Map.of("max-wait-seconds","0","max-waiting-players","1000000","poll-seconds","0").entrySet()){
            Properties p=new Properties();p.setProperty(pair.getKey(),pair.getValue());assertThrows(IllegalArgumentException.class,()->QueueConfig.read(p));
        }
    }
    @Test void moderationAndUnsafeLoginRejectionsAreNeverQueueRecovery(){
        assertTrue(FailurePolicy.network(Optional.empty()));assertTrue(FailurePolicy.network(Optional.of(Component.translatable("velocity.error.internal-server-connection-error"))));
        assertFalse(FailurePolicy.network(Optional.of(Component.text("Banned"))));assertFalse(FailurePolicy.network(Optional.of(Component.text("Unsafe restore"))));
    }
    @Test void onlyExactHiddenLeaveCommandIsHandled(){
        assertTrue(FailurePolicy.leave("/leave"));assertTrue(FailurePolicy.leave(" /LEAVE "));
        assertFalse(FailurePolicy.leave("leave"));assertFalse(FailurePolicy.leave("/leave someone"));assertFalse(FailurePolicy.leave("/server"));assertFalse(FailurePolicy.leave(null));
    }
}
