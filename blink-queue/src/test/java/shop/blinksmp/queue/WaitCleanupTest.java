package shop.blinksmp.queue;

import org.junit.jupiter.api.Test;
import com.velocitypowered.api.proxy.Player;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Regression: a timer retaining an old attempt must not kick a player already handed off. */
class WaitCleanupTest {
    @SuppressWarnings("unchecked") private void run(boolean completed)throws Exception{
        UUID id=UUID.randomUUID();AtomicInteger kicks=new AtomicInteger();
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(object,method,args)->{
            return switch(method.getName()){case "getUniqueId"->id;case "disconnect"->{kicks.incrementAndGet();yield null;}default->null;};
        });
        BlinkQueue plugin=new BlinkQueue(null,null,Path.of("."));
        Field cap=BlinkQueue.class.getDeclaredField("capacity");cap.setAccessible(true);Semaphore slots=new Semaphore(0);cap.set(plugin,slots);
        Field field=BlinkQueue.class.getDeclaredField("attempts");field.setAccessible(true);Map<UUID,Object> attempts=(Map<UUID,Object>)field.get(plugin);
        Class<?> type=Class.forName("shop.blinksmp.queue.BlinkQueue$Attempt");
        Constructor<?> constructor=type.getDeclaredConstructor(Player.class,WaitBudget.class,boolean.class);constructor.setAccessible(true);
        Object attempt=constructor.newInstance(player,new WaitBudget(0,1),false);attempts.put(id,attempt);
        Method remove=BlinkQueue.class.getDeclaredMethod("remove",type);remove.setAccessible(true);
        Method fail=BlinkQueue.class.getDeclaredMethod("fail",type,String.class);fail.setAccessible(true);
        if(completed)assertEquals(true,remove.invoke(plugin,attempt));
        fail.invoke(plugin,attempt,"expired");fail.invoke(plugin,attempt,"expired again");
        assertEquals(completed?0:1,kicks.get());assertEquals(1,slots.availablePermits());assertTrue(attempts.isEmpty());
    }
    @Test void staleTimeoutCannotKickCompletedHandoffOrDoubleReleaseCapacity()throws Exception{run(true);}
    @Test void actualTimeoutDisconnectsAndReleasesCapacityExactlyOnce()throws Exception{run(false);}
}
