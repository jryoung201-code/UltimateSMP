package shop.blinksmp.network;

import com.zaxxer.hikari.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** JDBC only. Call off the game thread. Persistent, fenced locks never silently expire. */
public final class NetworkStore implements AutoCloseable {
    private final HikariDataSource pool;
    private final String S;
    private final DatabaseDialect dialect;
    public record Route(String state,String last,String owner,String target,UUID ticket) {
        public String loginServer(){return state.equals("handoff")?target:last;}
        public boolean permits(String from,String to) {return state.equals("handoff") && Objects.equals(owner,from)&&Objects.equals(target,to);}
        /** The target has legitimately claimed (or already saved) this handoff. Never let the old backend overwrite it. */
        public boolean destinationHasState(String server) {
            return server != null && (("active".equals(state) && server.equals(owner))
                || ("offline".equals(state) && server.equals(last)));
        }
    }

    public static final Set<String> PROFILE_KINDS=Set.of("ranks","sanctions","homes","settings","advancements","statistics");
    public record Module(long revision,byte[] payload) {
        public Module { if(revision<1 || payload==null)throw new IllegalArgumentException("Invalid module");payload=payload.clone(); }
        @Override public byte[] payload(){return payload.clone();}
    }
    private static String moduleKind(String kind) {
        if(!PROFILE_KINDS.contains(kind))throw new IllegalArgumentException("Unknown profile module");
        return kind;
    }
    /** A module is absent at revision 0. Reads and writes are JDBC-only and must run off the game thread. */
    public Map<String,Module> modules(UUID player) throws SQLException {
        Map<String,Module> found=new HashMap<>();
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,
             "SELECT category,revision,payload FROM "+S+"player_modules WHERE player_id=?")) {
            dialect.uuid(q,1,player);
            try(ResultSet r=q.executeQuery()){
                while(r.next()) {
                    String kind=moduleKind(r.getString(1));
                    found.put(kind,new Module(r.getLong(2),r.getBytes(3)));
                }
            }
        }
        return Map.copyOf(found);
    }
    /** Optimistic compare-and-set: lost revisions never overwrite newer player state. */
    public long writeModule(UUID player,String category,long expectedRevision,byte[] data) throws SQLException {
        moduleKind(category);
        if(expectedRevision<0 || data==null || data.length>2_097_152)throw new IllegalArgumentException("Invalid profile data");
        if(expectedRevision==0) {
            try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,
              "INSERT INTO "+S+"player_modules(player_id,category,revision,payload) VALUES(?,?,1,?) ON CONFLICT DO NOTHING")) {
                dialect.uuid(s,1,player);s.setString(2,category);s.setBytes(3,data);
                if(s.executeUpdate()!=1)throw new SQLException("Profile revision conflict: "+category);
                return 1;
            }
        }
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,
          "UPDATE "+S+"player_modules SET revision=revision+1,payload=?,updated_at=now() WHERE player_id=? AND category=? AND revision=?")) {
            s.setBytes(1,data);dialect.uuid(s,2,player);s.setString(3,category);s.setLong(4,expectedRevision);
            if(s.executeUpdate()!=1)throw new SQLException("Profile revision conflict: "+category);
            return expectedRevision+1;
        }
    }
    public NetworkStore(String url,String user,String password) {
        dialect=DatabaseDialect.from(url);S=dialect.prefix();
        HikariConfig c=new HikariConfig();c.setJdbcUrl(dialect.url(url));c.setUsername(user);c.setPassword(password);
        c.setDriverClassName(dialect.driver());c.setMaximumPoolSize(2);c.setMinimumIdle(0);
        c.setConnectionTimeout(10_000);c.setValidationTimeout(3000);c.setInitializationFailTimeout(-1);
        c.addDataSourceProperty("connectTimeout",dialect.mysql?"5000":"5");
        c.addDataSourceProperty("socketTimeout",dialect.mysql?"10000":"10");
        if(dialect.mysql){c.addDataSourceProperty("useAffectedRows","true");c.setTransactionIsolation("TRANSACTION_READ_COMMITTED");}
        c.setPoolName("BlinkNetwork");pool=new HikariDataSource(c);
    }
    private PreparedStatement prepare(Connection c,String sql)throws SQLException{return c.prepareStatement(dialect.sql(sql));}
    public void initialize() throws Exception {
        try(var in=NetworkStore.class.getResourceAsStream(dialect.mysql?"/schema-mysql.sql":"/schema.sql");Connection c=pool.getConnection();Statement s=c.createStatement()) {
            c.setAutoCommit(false);
            for(String sql:new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())s.execute(sql);
            c.commit();
        }
    }
    public void verify() throws SQLException {try(Connection c=pool.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(dialect.sql("SELECT version FROM "+S+"meta WHERE id=1"))){if(!r.next()||r.getInt(1)!=3)throw new SQLException("Unsupported network schema");}}
    public void register(String server) throws SQLException {
        String dimension=Region.dimensionOf(server);
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"INSERT INTO "+S+"servers(id,dimension) VALUES(?,?) ON CONFLICT DO NOTHING")){s.setString(1,server);s.setString(2,dimension);s.executeUpdate();}
    }
    private boolean exists(Connection c,String server)throws SQLException{
        try(PreparedStatement q=prepare(c,"SELECT 1 FROM "+S+"servers WHERE id=?")){
            q.setString(1,server);try(ResultSet r=q.executeQuery()){return r.next();}
        }
    }
    public void enable(String server,boolean enabled) throws SQLException {
        Region.dimensionOf(server);
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"UPDATE "+S+"servers SET enabled=? WHERE id=?")){s.setBoolean(1,enabled);s.setString(2,server);if(s.executeUpdate()!=1&&!exists(c,server))throw new SQLException("Register the server first");}
    }
    public boolean heartbeat(String server,UUID instance) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"UPDATE "+S+"servers SET heartbeat=now(),instance=? WHERE id=? AND (instance IS NULL OR instance=? OR heartbeat<now()-interval '45 seconds') AND NOT EXISTS (SELECT 1 FROM "+S+"retired_instances retired WHERE retired.server_id=id AND retired.instance=?)")){dialect.uuid(s,1,instance);s.setString(2,server);dialect.uuid(s,3,instance);dialect.uuid(s,4,instance);return s.executeUpdate()==1;}
    }
    public boolean ready(String server) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"SELECT 1 FROM "+S+"servers WHERE id=? AND enabled AND NOT maintenance AND heartbeat>now()-interval '15 seconds'")){s.setString(1,server);try(ResultSet r=s.executeQuery()){return r.next();}}
    }
    public Map<Region,String> assignments() throws SQLException {
        Map<Region,String> result=new HashMap<>();
        try(Connection c=pool.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT r.dimension,region_x,region_z,server_id,s.dimension FROM "+S+"regions r JOIN "+S+"servers s ON s.id=r.server_id")) {
            while(r.next()) {result.put(new Region(r.getString(1),r.getInt(2),r.getInt(3)),r.getString(4));}
        }
        Set<String> multi=new HashSet<>();
        try(Connection c=pool.getConnection();Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT id FROM "+S+"servers WHERE all_dimensions")){while(r.next())multi.add(r.getString(1));}
        for(var row:new HashMap<>(result).entrySet())if(row.getKey().dimension().equals("Overworld")&&multi.contains(row.getValue()))
            for(String dim:List.of("Nether","End"))result.putIfAbsent(new Region(dim,row.getKey().x(),row.getKey().z()),row.getValue());
        return Map.copyOf(result);
    }
    /** Dimensions share one administrator-configured world border across their Paper instances. */
    public record Border(String dimension,int centerX,int centerZ,int sizeBlocks,boolean enabled,long revision) {
        public boolean contains(double x,double z) {
            if(!enabled)return true;
            double radius=sizeBlocks/2.0;
            return Math.abs(x-centerX)<radius&&Math.abs(z-centerZ)<radius;
        }
        public boolean containsRegion(Region region) {
            if(!enabled)return true;
            if(!dimension.equals(region.dimension()))return false;
            double half=sizeBlocks/2.0;
            return region.x()*144.0>=centerX-half&&(region.x()+1)*144.0<=centerX+half
                &&region.z()*144.0>=centerZ-half&&(region.z()+1)*144.0<=centerZ+half;
        }
    }
    public Map<String,Border> borders() throws SQLException {
        Map<String,Border> result=new HashMap<>();
        try(Connection c=pool.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(
            "SELECT dimension,center_x,center_z,size_blocks,enabled,revision FROM "+S+"world_borders")) {
            while(r.next()){
                Border b=new Border(r.getString(1),r.getInt(2),r.getInt(3),r.getInt(4),r.getBoolean(5),r.getLong(6));
                if(b.sizeBlocks()<144||b.sizeBlocks()>59_999_968)throw new SQLException("Invalid network world border");
                result.put(b.dimension(),b);
            }
        }
        return Map.copyOf(result);
    }
    /** Only initial assignment. Reassignment requires actual chunk migration; never upsert ownership. */
    public void assign(Region region,String server) throws SQLException {
        if(!supports(server,region.dimension()))throw new IllegalArgumentException("Dimension mismatch");
        Border border=borders().get(region.dimension());
        if(border!=null&&!border.containsRegion(region))throw new SQLException("Region is outside the configured world border");
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"INSERT INTO "+S+"regions(dimension,region_x,region_z,server_id) VALUES(?,?,?,?)")){s.setString(1,region.dimension());s.setInt(2,region.x());s.setInt(3,region.z());s.setString(4,server);s.executeUpdate();}
    }
    public Route route(UUID id) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"SELECT state,last_server,owner,target,ticket FROM "+S+"players WHERE player_id=?")){dialect.uuid(s,1,id);try(ResultSet r=s.executeQuery()){return r.next()?new Route(r.getString(1),r.getString(2),r.getString(3),r.getString(4),DatabaseDialect.uuid(r,5)):null;}}
    }
    /** Determines the correct server for a saved player's coordinates when ownership was edited offline. */
    public String loginTarget(UUID id,Route route) throws SQLException {
        if(!"offline".equals(route.state()))return route.loginServer();
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,
            "SELECT payload FROM "+S+"players WHERE player_id=? AND state='offline'")) {
            dialect.uuid(q,1,id);
            try(ResultSet result=q.executeQuery()){
                if(!result.next())throw new SQLException("Offline session no longer available");
                byte[] bytes=result.getBytes(1);
                if(bytes==null)return route.loginServer();
                Region region=regionFromSnapshot(bytes);
                String owner=ownerOf(c,region);
                if(owner==null)throw new SQLException("Player's saved coordinates are in an unassigned region");
                return owner;
            }
        }
    }
    /** Read only the three top-level YAML scalars needed for routing, never deserialize untrusted objects. */
    private static Region regionFromSnapshot(byte[] payload) throws SQLException {
        if(payload.length==0||payload.length>2_097_152)throw new SQLException("Invalid player snapshot");
        String src=new String(payload,StandardCharsets.UTF_8);
        String dimension=null,x=null,z=null;
        for(String line:src.split("\\R")){
            if(line.startsWith("dimension: "))dimension=line.substring("dimension: ".length()).trim();
            else if(line.startsWith("x: "))x=line.substring(3).trim();
            else if(line.startsWith("z: "))z=line.substring(3).trim();
        }
        try {
            if(dimension==null||x==null||z==null)throw new IllegalArgumentException();
            return Region.at(dimension,Double.parseDouble(x),Double.parseDouble(z));
        }catch(RuntimeException ex){throw new SQLException("Invalid saved position",ex);}
    }
    private String ownerOf(Connection c,Region region) throws SQLException {
        try(PreparedStatement q=prepare(c,
            "SELECT server_id FROM (SELECT server_id,0 AS priority FROM "+S+"regions WHERE dimension=? AND region_x=? AND region_z=? UNION ALL SELECT r.server_id,1 FROM "+S+"regions r JOIN "+S+"servers s ON s.id=r.server_id WHERE r.dimension='Overworld' AND s.all_dimensions AND r.region_x=? AND r.region_z=?) owners ORDER BY priority LIMIT 1")){
            q.setString(1,region.dimension());q.setInt(2,region.x());q.setInt(3,region.z());q.setInt(4,region.x());q.setInt(5,region.z());
            try(ResultSet result=q.executeQuery()){return result.next()?result.getString(1):null;}
        }
    }
    public byte[] claim(UUID id,String server,UUID session,boolean bootstrap) throws SQLException {
        try(Connection c=pool.getConnection()) {
            c.setAutoCommit(false);
            if(bootstrap)try(PreparedStatement s=prepare(c,"INSERT INTO "+S+"players(player_id,last_server,state) VALUES(?,?,'offline') ON CONFLICT DO NOTHING")){dialect.uuid(s,1,id);s.setString(2,server);s.executeUpdate();}
            byte[] data;
            try(PreparedStatement s=prepare(c,"SELECT state,last_server,target,payload FROM "+S+"players WHERE player_id=? FOR UPDATE")) {
                dialect.uuid(s,1,id);try(ResultSet r=s.executeQuery()) {
                    if(!r.next())throw new SQLException("New players must first join the bootstrap server");
                    data=r.getBytes(4);
                    boolean offline=r.getString(1).equals("offline");
                    boolean allowed=(offline&&server.equals(r.getString(2)))||(r.getString(1).equals("handoff")&&server.equals(r.getString(3)));
                    if(offline&&data!=null) {
                        // Native snapshots route by their stored coordinates. Legacy unreadable
                        // snapshots may still be claimed only on their original backend.
                        try {
                            String owner=ownerOf(c,regionFromSnapshot(data));
                            allowed=server.equals(owner);
                        }catch(SQLException malformed) {
                            if(!server.equals(r.getString(2)))throw malformed;
                        }
                    }
                    if(!allowed)throw new SQLException("Player state is held by another session/server or saved region is unassigned");
                }
            }
            try(PreparedStatement s=prepare(c,"UPDATE "+S+"players SET state='active',owner=?,last_server=?,session=?,owner_instance=(SELECT instance FROM "+S+"servers WHERE id=?),target=NULL,ticket=NULL,rollback_payload=NULL,updated_at=now() WHERE player_id=?")){s.setString(1,server);s.setString(2,server);dialect.uuid(s,3,session);s.setString(4,server);dialect.uuid(s,5,id);s.executeUpdate();}
            c.commit();return data;
        }
    }
    public void abandonEmpty(UUID id,UUID session) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"UPDATE "+S+"players SET state='offline',owner=NULL,session=NULL WHERE player_id=? AND session=? AND state='active' AND payload IS NULL")){dialect.uuid(s,1,id);dialect.uuid(s,2,session);s.executeUpdate();}
    }
    public boolean save(UUID id,UUID session,byte[] data,boolean release) throws SQLException {
        checkSize(data);
        String tail=release?",state='offline',owner=NULL,session=NULL":"";
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"UPDATE "+S+"players SET payload=?,updated_at=now()"+tail+" WHERE player_id=? AND session=? AND state='active'")){s.setBytes(1,data);dialect.uuid(s,2,id);dialect.uuid(s,3,session);return s.executeUpdate()==1;}
    }
    public boolean prepare(UUID id,UUID session,String target,UUID ticket,byte[] source,byte[] destination) throws SQLException {
        checkSize(source);checkSize(destination);Region.dimensionOf(target);
        if(!ready(target))return false;
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"UPDATE "+S+"players SET state='handoff',target=?,ticket=?,payload=?,rollback_payload=?,updated_at=now() WHERE player_id=? AND session=? AND state='active'")){s.setString(1,target);dialect.uuid(s,2,ticket);s.setBytes(3,destination);s.setBytes(4,source);dialect.uuid(s,5,id);dialect.uuid(s,6,session);return s.executeUpdate()==1;}
    }
    public boolean cancel(UUID id,UUID session,UUID ticket,boolean release) throws SQLException {
        String state=release?"state='offline',owner=NULL,session=NULL":"state='active'";
        try(Connection c=pool.getConnection();PreparedStatement s=prepare(c,"UPDATE "+S+"players SET "+state+",payload=rollback_payload,rollback_payload=NULL,target=NULL,ticket=NULL,updated_at=now() WHERE player_id=? AND session=? AND ticket=? AND state='handoff'")){dialect.uuid(s,1,id);dialect.uuid(s,2,session);dialect.uuid(s,3,ticket);return s.executeUpdate()==1;}
    }
    /** Offline-only position repair. Does not reset inventory, modules, sanctions or any active session. */
    public void rescueOffline(UUID player,String server,double x,double y,double z) throws SQLException {
        rescueOffline(player,server,Region.dimensionOf(server),x,y,z);
    }
    public void rescueOffline(UUID player,String server,String dimension,double x,double y,double z) throws SQLException {rescueOffline(player,server,dimension,x,y,z,false);}
    private void rescueOffline(UUID player,String server,String dimension,double x,double y,double z,boolean rtp) throws SQLException {
        Region region=Region.at(dimension,x,z);
        try(Connection c=pool.getConnection()) {
            c.setAutoCommit(false);
            try {
                if(!server.equals(ownerOf(c,region)))throw new SQLException("Rescue destination belongs to another server or is unassigned");
                try(PreparedStatement q=prepare(c,"SELECT center_x,center_z,size_blocks,enabled,revision FROM "+S+"world_borders WHERE dimension=?")){
                    q.setString(1,dimension);try(ResultSet r=q.executeQuery()){
                        if(r.next()&&!new Border(dimension,r.getInt(1),r.getInt(2),r.getInt(3),r.getBoolean(4),r.getLong(5)).contains(x,z))throw new SQLException("Rescue outside world border");
                    }
                }
                byte[] payload;
                try(PreparedStatement q=prepare(c,"SELECT state,payload FROM "+S+"players WHERE player_id=? FOR UPDATE")){
                    dialect.uuid(q,1,player);try(ResultSet r=q.executeQuery()){
                        if(!r.next()||!"offline".equals(r.getString(1))||r.getBytes(2)==null)throw new SQLException("Player must be offline with a saved snapshot; active locks are not bypassed");
                        payload=SnapshotPosition.relocate(r.getBytes(2),dimension,x,y,z);
                        payload=(new String(payload,StandardCharsets.UTF_8).replaceAll("(?m)^(?:rtp-arrival|portal-arrival):.*\\R?", "")+(rtp?"\nrtp-arrival: true\n":"")).getBytes(StandardCharsets.UTF_8);
                    }
                }
                try(PreparedStatement q=prepare(c,"UPDATE "+S+"players SET payload=?,last_server=?,updated_at=now() WHERE player_id=? AND state='offline'")){
                    q.setBytes(1,payload);q.setString(2,server);dialect.uuid(q,3,player);if(q.executeUpdate()!=1)throw new SQLException("Player state changed during rescue");
                }
                try(PreparedStatement q=prepare(c,"INSERT INTO "+S+"audit(action,detail) VALUES('offline-position-rescue',?)")){
                    q.setString(1,player+" -> "+server+" "+x+","+y+","+z);q.executeUpdate();
                }
                c.commit();
            }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}
        }
    }
    /** Administrative recovery only after all copies of this player's server process have stopped. */
    public void recover(UUID id) throws SQLException {
        try(Connection c=pool.getConnection()) {
            c.setAutoCommit(false);
            try(PreparedStatement s=prepare(c,"UPDATE "+S+"players SET payload=CASE WHEN state='handoff' THEN rollback_payload ELSE payload END,state='offline',owner=NULL,session=NULL,target=NULL,ticket=NULL,rollback_payload=NULL,updated_at=now() WHERE player_id=?")){dialect.uuid(s,1,id);s.executeUpdate();}
            try(PreparedStatement s=prepare(c,"INSERT INTO "+S+"audit(action,detail) VALUES('manual-recovery',?)")){s.setString(1,id.toString());s.executeUpdate();}c.commit();
        }
    }

    public void dimensions(String server,boolean all) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"UPDATE "+S+"servers SET all_dimensions=? WHERE id=?")){q.setBoolean(1,all);q.setString(2,server);q.executeUpdate();}
    }
    public boolean supports(String server,String dim) throws SQLException {
        Region.dimensionOf(server);
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"SELECT 1 FROM "+S+"servers WHERE id=? AND (dimension=? OR all_dimensions)")){q.setString(1,server);q.setString(2,dim);try(ResultSet r=q.executeQuery()){return r.next();}}
    }
    public void maintenance(String server,boolean value) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"UPDATE "+S+"servers SET maintenance=? WHERE id=?")){q.setBoolean(1,value);q.setString(2,server);if(q.executeUpdate()!=1&&!exists(c,server))throw new SQLException("Unknown backend");}
    }
    public boolean queue(UUID player,UUID session,String origin,UUID ticket,byte[] payload) throws SQLException {
        checkSize(payload);
        try(Connection c=pool.getConnection()) {
            c.setAutoCommit(false);
            try(PreparedStatement q=prepare(c,"UPDATE "+S+"players SET payload=?,state='offline',owner=NULL,session=NULL,updated_at=now() WHERE player_id=? AND session=? AND state='active'")){
                q.setBytes(1,payload);dialect.uuid(q,2,player);dialect.uuid(q,3,session);if(q.executeUpdate()!=1){c.rollback();return false;}
            }
            try(PreparedStatement q=prepare(c,"INSERT INTO "+S+"queue_requests(player_id,origin,ticket) VALUES(?,?,?) ON CONFLICT(player_id) DO UPDATE SET origin=excluded.origin,ticket=excluded.ticket,created_at=now()")){
                dialect.uuid(q,1,player);q.setString(2,origin);dialect.uuid(q,3,ticket);q.executeUpdate();
            }
            c.commit();return true;
        }
    }
    /** Private session authentication for a safe proxy-requested server switch. */
    public UUID switchKey(UUID player,String source)throws SQLException{
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"SELECT session FROM "+S+"players WHERE player_id=? AND state='active' AND owner=?")){
            dialect.uuid(q,1,player);q.setString(2,source);try(ResultSet row=q.executeQuery()){if(!row.next())throw new SQLException("No active source session");return DatabaseDialect.uuid(row,1);}
        }
    }
    public boolean queued(UUID player,String origin,UUID ticket) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"SELECT 1 FROM "+S+"queue_requests WHERE player_id=? AND origin=? AND ticket=? AND created_at>now()-interval '2 minutes'")){
            dialect.uuid(q,1,player);q.setString(2,origin);dialect.uuid(q,3,ticket);try(ResultSet r=q.executeQuery()){return r.next();}
        }
    }
    public record Rtp(String server,String dimension,double x,double y,double z) {}
    public void publishRtp(Rtp point) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"INSERT INTO "+S+"rtp_points(server_id,dimension,x,y,z) VALUES(?,?,?,?,?) ON CONFLICT(server_id,dimension) DO UPDATE SET x=excluded.x,y=excluded.y,z=excluded.z,heartbeat=now()")){
            q.setString(1,point.server());q.setString(2,point.dimension());q.setDouble(3,point.x());q.setDouble(4,point.y());q.setDouble(5,point.z());q.executeUpdate();
        }
    }
    public void queueHealth(String id,boolean ready) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"INSERT INTO "+S+"queue_status(id,ready) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET ready=excluded.ready,heartbeat=now()")){q.setString(1,id);q.setBoolean(2,ready);q.executeUpdate();}
    }
    public boolean queueReady(String id) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"SELECT 1 FROM "+S+"queue_status WHERE id=? AND ready AND heartbeat>now()-interval '15 seconds'")){q.setString(1,id);try(ResultSet r=q.executeQuery()){return r.next();}}
    }
    public boolean rtpPending(UUID player) throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"SELECT payload FROM "+S+"players WHERE player_id=? AND state='offline'")){
            dialect.uuid(q,1,player);try(ResultSet r=q.executeQuery()){return r.next()&&r.getBytes(1)!=null&&new String(r.getBytes(1),StandardCharsets.UTF_8).matches("(?s).*(?:\nrtp-arrival: true|\nportal-arrival: true).*" );}
        }
    }
    public boolean anyMaintenance() throws SQLException {
        try(Connection c=pool.getConnection();Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT 1 FROM "+S+"servers WHERE maintenance LIMIT 1")){return r.next();}
    }
    public boolean rerouteOffline(UUID player,Rtp point) throws SQLException {
        if(!point.dimension().equals("Overworld"))throw new IllegalArgumentException("Fallback RTP must use Overworld");
        if(!ready(point.server()))return false;
        rescueOffline(player,point.server(),point.dimension(),point.x(),point.y(),point.z(),true);return true;
    }
    public List<Rtp> rtpPoints(String exclude) throws SQLException {
        List<Rtp> result=new ArrayList<>();Map<Region,String> owners=assignments();Map<String,Border> limits=borders();
        try(Connection c=pool.getConnection();PreparedStatement q=prepare(c,"SELECT p.server_id,p.dimension,x,y,z FROM "+S+"rtp_points p JOIN "+S+"servers s ON s.id=p.server_id WHERE s.enabled AND NOT s.maintenance AND s.heartbeat>now()-interval '15 seconds' AND p.heartbeat>now()-interval '45 seconds' AND p.dimension='Overworld' AND s.id<>?")){
            q.setString(1,exclude);try(ResultSet r=q.executeQuery()){while(r.next()){
                Rtp p=new Rtp(r.getString(1),r.getString(2),r.getDouble(3),r.getDouble(4),r.getDouble(5));Border b=limits.get(p.dimension());
                if(p.server().equals(owners.get(Region.at(p.dimension(),p.x(),p.z())))&&(b==null||b.contains(p.x(),p.z())))result.add(p);
            }}
        }
        Collections.shuffle(result);return result;
    }
    /** Crash recovery fences the old instance before releasing its last committed player state. */
    public boolean releaseDeadSession(UUID player) throws SQLException {
        try(Connection c=pool.getConnection()) {
            c.setAutoCommit(false);
            try {
                String owner,state;UUID held;
                try(PreparedStatement q=prepare(c,"SELECT state,owner,owner_instance FROM "+S+"players WHERE player_id=? FOR UPDATE")){
                    dialect.uuid(q,1,player);try(ResultSet r=q.executeQuery()){if(!r.next()){c.rollback();return false;}state=r.getString(1);owner=r.getString(2);held=DatabaseDialect.uuid(r,3);}
                }
                if(state.equals("offline")){c.commit();return true;}
                UUID current;boolean expired;
                try(PreparedStatement q=prepare(c,"SELECT instance,(heartbeat IS NULL OR heartbeat<now()-interval '45 seconds') FROM "+S+"servers WHERE id=? FOR UPDATE")){
                    q.setString(1,owner);try(ResultSet r=q.executeQuery()){if(!r.next()){c.rollback();return false;}current=DatabaseDialect.uuid(r,1);expired=r.getBoolean(2);}
                }
                if((held==null||Objects.equals(held,current))&&!expired){c.rollback();return false;}
                UUID old=held==null?current:held;
                if(old!=null)try(PreparedStatement q=prepare(c,"INSERT INTO "+S+"retired_instances(server_id,instance) VALUES(?,?) ON CONFLICT DO NOTHING")){q.setString(1,owner);dialect.uuid(q,2,old);q.executeUpdate();}
                if(Objects.equals(old,current))try(PreparedStatement q=prepare(c,"UPDATE "+S+"servers SET instance=NULL,heartbeat=NULL WHERE id=?")){q.setString(1,owner);q.executeUpdate();}
                try(PreparedStatement q=prepare(c,"UPDATE "+S+"players SET payload=CASE WHEN state='handoff' THEN rollback_payload ELSE payload END,state='offline',owner=NULL,session=NULL,target=NULL,ticket=NULL,rollback_payload=NULL,updated_at=now() WHERE player_id=?")){dialect.uuid(q,1,player);q.executeUpdate();}
                try(PreparedStatement q=prepare(c,"INSERT INTO "+S+"audit(action,detail) VALUES('dead-instance-fenced',?)")){q.setString(1,player+" owner="+owner);q.executeUpdate();}
                c.commit();return true;
            }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}
        }
    }
    private static void checkSize(byte[] data){if(data==null||data.length>2_097_152)throw new IllegalArgumentException("Invalid snapshot size");}
    @Override public void close(){pool.close();}
}


