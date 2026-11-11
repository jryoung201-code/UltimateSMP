package shop.blinksmp.network;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.sql.*;

/** Real PostgreSQL integration; CI supplies an isolated database, never production Neon. */
class DatabaseTest {
    NetworkStore db;static final String A="BlinkSMP-Overworld-01",B="BlinkSMP-Overworld-02";
    @BeforeEach void setup()throws Exception{
        Assumptions.assumeTrue(System.getenv("TEST_JDBC_URL")!=null,"Set TEST_JDBC_URL to run PostgreSQL integration tests");
        db=new NetworkStore(System.getenv("TEST_JDBC_URL"),"postgres","postgres");db.initialize();db.verify();db.register(A);db.register(B);db.enable(A,true);db.enable(B,true);db.heartbeat(A,UUID.randomUUID());db.heartbeat(B,UUID.randomUUID());
    }
    @AfterEach void close(){if(db!=null)db.close();}
    @Test void endToEndHandoffAndFencing()throws Exception{
        UUID player=UUID.randomUUID(),s1=UUID.randomUUID(),s2=UUID.randomUUID(),ticket=UUID.randomUUID();
        assertNull(db.claim(player,A,s1,true));assertTrue(db.save(player,s1,new byte[]{1},false));
        assertThrows(SQLException.class,()->db.claim(player,A,UUID.randomUUID(),true));
        assertTrue(db.prepare(player,s1,B,ticket,new byte[]{1},new byte[]{2}));assertEquals(B,db.route(player).loginServer());
        assertFalse(db.save(player,s1,new byte[]{9},true));
        assertArrayEquals(new byte[]{2},db.claim(player,B,s2,false));
        assertFalse(db.cancel(player,s1,ticket,false));assertFalse(db.save(player,s1,new byte[]{9},true));
        assertTrue(db.save(player,s2,new byte[]{3},true));assertArrayEquals(new byte[]{3},db.claim(player,B,UUID.randomUUID(),false));
    }
    @Test void failureRestoresSourceAndRejectsWrongTicket()throws Exception{
        UUID p=UUID.randomUUID(),s=UUID.randomUUID(),t=UUID.randomUUID();db.claim(p,A,s,true);db.save(p,s,new byte[]{1},false);
        assertTrue(db.prepare(p,s,B,t,new byte[]{1},new byte[]{2}));assertFalse(db.cancel(p,s,UUID.randomUUID(),false));assertTrue(db.cancel(p,s,t,true));
        assertEquals(A,db.route(p).loginServer());assertArrayEquals(new byte[]{1},db.claim(p,A,UUID.randomUUID(),false));
    }
    @Test void unavailableDestinationDoesNotPrepare()throws Exception{
        UUID p=UUID.randomUUID(),s=UUID.randomUUID();db.claim(p,A,s,true);db.save(p,s,new byte[]{1},false);db.enable(B,false);
        assertFalse(db.prepare(p,s,B,UUID.randomUUID(),new byte[]{1},new byte[]{2}));assertEquals("active",db.route(p).state());
    }
    @Test void wrongDestinationAndConcurrentClaims()throws Exception{
        UUID p=UUID.randomUUID(),s=UUID.randomUUID();db.claim(p,A,s,true);db.save(p,s,new byte[]{1},true);
        assertThrows(SQLException.class,()->db.claim(p,B,UUID.randomUUID(),false));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{var tasks=List.<java.util.concurrent.Callable<Boolean>>of(()->claimOnce(p),()->claimOnce(p));int wins=0;for(var f:pool.invokeAll(tasks))if(f.get())wins++;assertEquals(1,wins);}finally{pool.shutdownNow();}
    }
    private boolean claimOnce(UUID p){try{db.claim(p,A,UUID.randomUUID(),false);return true;}catch(SQLException e){return false;}}
    @Test void assignmentsCannotSilentlyOverwrite()throws Exception{
        Region r=new Region("Overworld",new Random().nextInt(1000000),new Random().nextInt(1000000));db.assign(r,A);assertEquals(A,db.assignments().get(r));assertThrows(SQLException.class,()->db.assign(r,B));assertThrows(IllegalArgumentException.class,()->db.assign(r,"BlinkSMP-Nether-01"));
    }
    @Test void duplicateServerInstanceRefused()throws Exception{UUID a=UUID.randomUUID();String id="BlinkSMP-End-99";db.register(id);assertTrue(db.heartbeat(id,a));assertTrue(db.heartbeat(id,a));assertFalse(db.heartbeat(id,UUID.randomUUID()));}
    @Test void manualRecoveryRetainsLastCommittedSource()throws Exception{
        UUID p=UUID.randomUUID(),s=UUID.randomUUID();db.claim(p,A,s,true);db.prepare(p,s,B,UUID.randomUUID(),new byte[]{4},new byte[]{5});db.recover(p);assertArrayEquals(new byte[]{4},db.claim(p,A,UUID.randomUUID(),false));
    }
}
