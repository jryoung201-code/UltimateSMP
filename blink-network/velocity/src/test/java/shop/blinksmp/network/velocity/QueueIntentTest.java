package shop.blinksmp.network.velocity;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class QueueIntentTest {
 @Test void restartPreservesLocationUntilExplicitLeave(){var restart=QueueIntent.restart("BlinkSMP-Overworld-01");assertFalse(restart.needsRtp());assertTrue(restart.canLeave());var join=restart.leave();assertEquals(restart.origin(),join.origin());assertTrue(join.needsRtp());assertFalse(join.canLeave());assertThrows(IllegalStateException.class,join::leave);}
 @Test void preparedJoinDoesNotRerollOnEachPoll(){var join=QueueIntent.join("BlinkSMP-Overworld-01").prepared("BlinkSMP-Overworld-02");assertFalse(join.needsRtp());assertEquals("BlinkSMP-Overworld-02",join.rtpTarget());}
}
