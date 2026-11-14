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
        try(Connection c=DriverManager.getConnection(System.getenv("TEST_JDBC_URL"),"postgres","postgres");Statement q=c.createStatement()){q.execute("DROP SCHEMA IF EXISTS blinksmp_network_v1 CASCADE");}
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
    @Test void rejectedFirstLoginCanReleaseOnlyItsOwnEmptyClaim()throws Exception{
        UUID p=UUID.randomUUID(),s=UUID.randomUUID();assertNull(db.claim(p,A,s,true));
        db.abandonEmpty(p,UUID.randomUUID());assertEquals("active",db.route(p).state());
        db.abandonEmpty(p,s);assertEquals("offline",db.route(p).state());
        UUID next=UUID.randomUUID();assertNull(db.claim(p,A,next,false));db.save(p,next,new byte[]{7},false);
        db.abandonEmpty(p,next);assertEquals("active",db.route(p).state());
    }
    @Test void playerModulesAreVersionedAndConflictsDoNotOverwrite()throws Exception{
        UUID player=UUID.randomUUID();
        assertTrue(db.modules(player).isEmpty());
        long v1=db.writeModule(player,"ranks",0,"MEMBER".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1,v1);
        assertArrayEquals("MEMBER".getBytes(java.nio.charset.StandardCharsets.UTF_8),db.modules(player).get("ranks").payload());
        assertThrows(SQLException.class,()->db.writeModule(player,"ranks",0,"OWNER".getBytes()));
        long v2=db.writeModule(player,"ranks",v1,"BLINK_PLUS".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(2,v2);
        assertThrows(SQLException.class,()->db.writeModule(player,"ranks",1,"OWNER".getBytes()));
        assertEquals("BLINK_PLUS",new String(db.modules(player).get("ranks").payload(),java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,()->db.writeModule(player,"economy",0,new byte[]{1}));
        assertThrows(IllegalArgumentException.class,()->db.writeModule(player,"settings",0,new byte[2_097_153]));
    }
    @Test void savedCoordinatesSelectCorrectBackendAfterOwnershipChange()throws Exception{
        Region r=new Region("Overworld",5000+new Random().nextInt(5000),3000+new Random().nextInt(5000));
        db.assign(r,B);
        UUID player=UUID.randomUUID(),session=UUID.randomUUID();
        db.claim(player,A,session,true);
        byte[] snapshot=("version: 1\ndimension: Overworld\nx: "+(r.x()*144+12)+".0\ny: 70.0\nz: "+(r.z()*144+12)+".0\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(db.save(player,session,snapshot,true));
        assertEquals(B,db.loginTarget(player,db.route(player)));
        assertThrows(SQLException.class,()->db.claim(player,A,UUID.randomUUID(),false));
        assertArrayEquals(snapshot,db.claim(player,B,UUID.randomUUID(),false));
    }
    @Test void worldBorderRejectsOutOfBoundsRegionAssignment()throws Exception{
        try(Connection c=DriverManager.getConnection(System.getenv("TEST_JDBC_URL"),"postgres","postgres")){
            try(PreparedStatement q=c.prepareStatement(
                "INSERT INTO blinksmp_network_v1.world_borders(dimension,center_x,center_z,size_blocks,enabled) VALUES('Overworld',0,0,576,true) ON CONFLICT(dimension) DO UPDATE SET center_x=0,center_z=0,size_blocks=576,enabled=true")){
                q.executeUpdate();
            }
            try {
                NetworkStore.Border border=db.borders().get("Overworld");
                assertNotNull(border);assertTrue(border.contains(5,5));
                assertTrue(border.containsRegion(new Region("Overworld",0,0)));
                assertFalse(border.containsRegion(new Region("Overworld",3,0)));
                assertThrows(SQLException.class,()->db.assign(new Region("Overworld",3,0),A));
            }finally{
                try(Statement q=c.createStatement()){q.executeUpdate("DELETE FROM blinksmp_network_v1.world_borders WHERE dimension='Overworld'");}
            }
        }
    }
    @Test void payloadBoundsRejectedBeforeWriting()throws Exception{
        UUID p=UUID.randomUUID(),s=UUID.randomUUID();db.claim(p,A,s,true);
        assertThrows(IllegalArgumentException.class,()->db.save(p,s,new byte[2097153],false));
        assertThrows(IllegalArgumentException.class,()->db.save(p,s,null,false));
    }

    private static byte[] position(double x,double z){return ("version: 1\ndimension: Overworld\nx: "+x+"\ny: 70.0\nz: "+z+"\ninventory: PRESERVE_ITEM_NBT\nender: PRESERVE_ENDER_NBT\nhealth: 18.0\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    @Test void failedEdgeCrossingReconnectsToConfirmedSourcePosition()throws Exception{
        Region source=new Region("Overworld",20000+new Random().nextInt(10000),22000);
        db.assign(source,A);db.assign(new Region("Overworld",source.x()+1,source.z()),B);
        UUID p=UUID.randomUUID(),session=UUID.randomUUID(),ticket=UUID.randomUUID();db.claim(p,A,session,true);
        byte[] from=position((source.x()+1)*144.0-0.01,source.z()*144.0+60);
        byte[] to=position((source.x()+1)*144.0+0.01,source.z()*144.0+60);
        assertTrue(db.save(p,session,from,false));assertTrue(db.prepare(p,session,B,ticket,from,to));
        assertTrue(db.cancel(p,session,ticket,true));assertEquals(A,db.loginTarget(p,db.route(p)));
        assertArrayEquals(from,db.claim(p,A,UUID.randomUUID(),false));
    }
    @Test void offlineRescuePreservesItemsAndProfilesAndRejectsLateSourceSave()throws Exception{
        Region r=new Region("Overworld",40000+new Random().nextInt(10000),33000);db.assign(r,B);
        UUID p=UUID.randomUUID(),old=UUID.randomUUID();db.claim(p,A,old,true);
        byte[] before=position(-0.001,-0.001);db.save(p,old,before,true);
        db.writeModule(p,"ranks",0,"BLINK_PLUS_PLUS_PLUS".getBytes());
        double x=r.x()*144.0+20,z=r.z()*144.0+20;
        db.rescueOffline(p,B,x,72,z);
        assertEquals(B,db.loginTarget(p,db.route(p)));
        assertFalse(db.save(p,old,before,true));
        byte[] after=db.claim(p,B,UUID.randomUUID(),false);
        assertArrayEquals(SnapshotPosition.relocate(before,"Overworld",x,72,z),after);
        assertEquals(1,db.modules(p).get("ranks").revision());
        assertEquals("BLINK_PLUS_PLUS_PLUS",new String(db.modules(p).get("ranks").payload()));
    }
    @Test void rescueCannotBypassActiveOrPendingOwnership()throws Exception{
        Region r=new Region("Overworld",55000+new Random().nextInt(10000),44000);db.assign(r,A);
        UUID p=UUID.randomUUID(),session=UUID.randomUUID(),ticket=UUID.randomUUID();db.claim(p,A,session,true);
        byte[] bytes=position(r.x()*144.0+20,r.z()*144.0+20);db.save(p,session,bytes,false);
        assertThrows(SQLException.class,()->db.rescueOffline(p,A,r.x()*144.0+20,72,r.z()*144.0+20));
        assertTrue(db.prepare(p,session,B,ticket,bytes,bytes));
        assertThrows(SQLException.class,()->db.rescueOffline(p,A,r.x()*144.0+20,72,r.z()*144.0+20));
        assertEquals(ticket,db.route(p).ticket());assertEquals("handoff",db.route(p).state());
    }
    @Test void rejectedRescueDoesNotChangeSavedPosition()throws Exception{
        Region r=new Region("Overworld",70000+new Random().nextInt(10000),55000);db.assign(r,A);
        UUID p=UUID.randomUUID(),session=UUID.randomUUID();db.claim(p,A,session,true);
        byte[] bytes=position(r.x()*144.0+20,r.z()*144.0+20);db.save(p,session,bytes,true);
        assertThrows(SQLException.class,()->db.rescueOffline(p,B,r.x()*144.0+20,72,r.z()*144.0+20));
        assertThrows(SQLException.class,()->db.rescueOffline(p,A,(r.x()+1)*144.0+20,72,r.z()*144.0+20));
        assertArrayEquals(bytes,db.claim(p,A,UUID.randomUUID(),false));
    }
    @Test void backendMirrorsOwnedRegionsIntoBothOtherDimensions()throws Exception{
        db.dimensions(A,true);Region ow=new Region("Overworld",7,-2);db.assign(ow,A);
        assertEquals(A,db.assignments().get(new Region("Nether",7,-2)));
        assertEquals(A,db.assignments().get(new Region("End",7,-2)));
        UUID p=UUID.randomUUID(),session=UUID.randomUUID();db.claim(p,A,session,true);
        byte[] snapshot=SnapshotPosition.relocate(position(7*144+20,-2*144+20),"Nether",7*144+20,70,-2*144+20);
        db.save(p,session,snapshot,true);assertEquals(A,db.loginTarget(p,db.route(p)));
        assertArrayEquals(snapshot,db.claim(p,A,UUID.randomUUID(),false));
        db.dimensions(B,true);db.assign(new Region("Nether",7,-2),B);
        assertEquals(B,db.assignments().get(new Region("Nether",7,-2)));
        assertEquals(A,db.assignments().get(new Region("End",7,-2)));
    }
    @Test void maintenanceBlocksHandoffAndRtpButRetainsHeartbeat()throws Exception{
        assertTrue(db.ready(B));db.maintenance(B,true);assertFalse(db.ready(B));assertTrue(db.anyMaintenance());
        UUID p=UUID.randomUUID(),session=UUID.randomUUID();db.claim(p,A,session,true);db.save(p,session,position(12,12),false);
        assertFalse(db.prepare(p,session,B,UUID.randomUUID(),position(12,12),position(160,12)));
        db.maintenance(B,false);assertTrue(db.ready(B));
    }
    @Test void restartQueueReleasesOnlyExactSessionAndPreservesInventory()throws Exception{
        UUID p=UUID.randomUUID(),session=UUID.randomUUID(),ticket=UUID.randomUUID();db.claim(p,A,session,true);
        byte[] snapshot=position(12,12);db.save(p,session,snapshot,false);
        assertFalse(db.queue(p,UUID.randomUUID(),A,ticket,snapshot));assertEquals("active",db.route(p).state());
        assertTrue(db.queue(p,session,A,ticket,snapshot));assertTrue(db.queued(p,A,ticket));assertFalse(db.queued(p,B,ticket));
        assertEquals("offline",db.route(p).state());assertFalse(db.save(p,session,new byte[]{9},true));
        assertArrayEquals(snapshot,db.claim(p,A,UUID.randomUUID(),false));
    }
    @Test void deadServerRecoveryWaitsThenPermanentlyFencesOldInstance()throws Exception{
        UUID p=UUID.randomUUID(),session=UUID.randomUUID(),instance=UUID.randomUUID();String server="BlinkSMP-End-98";
        db.register(server);db.enable(server,true);assertTrue(db.heartbeat(server,instance));db.claim(p,server,session,true);
        db.save(p,session,position(12,12),false);assertFalse(db.releaseDeadSession(p));assertEquals("active",db.route(p).state());
        try(Connection c=DriverManager.getConnection(System.getenv("TEST_JDBC_URL"),"postgres","postgres");Statement q=c.createStatement()){q.execute("UPDATE blinksmp_network_v1.servers SET heartbeat=now()-interval '46 seconds' WHERE id='"+server+"'");}
        assertTrue(db.releaseDeadSession(p));assertEquals("offline",db.route(p).state());
        assertFalse(db.heartbeat(server,instance));assertFalse(db.save(p,session,new byte[]{9},true));
        assertTrue(db.heartbeat(server,UUID.randomUUID()));
    }
    @Test void rtpMovesOnlyOfflineSnapshotAndKeepsItemData()throws Exception{
        db.assign(new Region("Overworld",1,0),B);db.publishRtp(new NetworkStore.Rtp(B,"Overworld",164,72,20));
        UUID p=UUID.randomUUID(),session=UUID.randomUUID();db.claim(p,A,session,true);byte[] before=position(12,12);db.save(p,session,before,false);
        var points=db.rtpPoints(A);assertEquals(1,points.size());
        assertThrows(SQLException.class,()->db.rerouteOffline(p,points.getFirst()));
        db.save(p,session,before,true);assertTrue(db.rerouteOffline(p,points.getFirst()));assertEquals(B,db.loginTarget(p,db.route(p)));
        String after=new String(db.claim(p,B,UUID.randomUUID(),false),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(after.contains("inventory: PRESERVE_ITEM_NBT"));assertTrue(after.contains("rtp-arrival: true"));
        db.maintenance(B,true);assertTrue(db.rtpPoints(A).isEmpty());
    }

}

