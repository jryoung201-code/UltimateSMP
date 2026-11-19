package shop.ultimatesmp.shop;

import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Shared entitlement records are desired state, not commands that can execute twice. */
public final class ShopStore {
    private final String url,user,password;
    public ShopStore(String url,String user,String password){this.url=url;this.user=user;this.password=password;}
    Connection connect() throws SQLException {var p=new Properties();p.setProperty("user",user);p.setProperty("password",password);p.setProperty("connectTimeout","10000");p.setProperty("socketTimeout","15000");try {return new org.mariadb.jdbc.Driver().connect(url,p);} catch (SQLException error) {throw DatabaseFailure.during("open MariaDB connection",error);}}
    public void initialize() throws Exception {
        try(var c=connect();var s=c.createStatement();var in=ShopStore.class.getResourceAsStream("/schema.sql")){
            for(String sql:new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())s.execute(sql);
        }
    }
    public static String hash(String token) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
    public boolean linkDiscord(DiscordDatabase database, UUID player, String code) throws Exception {
        try (var connection = database.connect()) {
            connection.setAutoCommit(false);
            try (var query = connection.prepareStatement(
                    "SELECT discord_id FROM ultimate_discord_codes WHERE token_hash=? AND expires>?")) {
                query.setString(1, hash(code));
                query.setLong(2, System.currentTimeMillis());
                String discordId;
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) {
                        connection.rollback();
                        return false;
                    }
                    discordId = rows.getString(1);
                }
                update(connection, "INSERT INTO ultimate_discord_links(discord_id,player_id) VALUES(?,?)",
                        discordId, player.toString());
                update(connection, "DELETE FROM ultimate_discord_codes WHERE discord_id=?", discordId);
                connection.commit();
                return true;
            } catch (SQLException error) {
                connection.rollback();
                if (error.getErrorCode() == 19) return false;
                throw error;
            }
        }
    }

    public void link(UUID id,String name,String token,long expires)throws Exception {
        try(var c=connect()){
            linkWrite(c,"remove expired website links","DELETE FROM ultimate_shop_links WHERE expires < ?",System.currentTimeMillis());
            linkWrite(c,"remove previous website link","DELETE FROM ultimate_shop_links WHERE player_id=?",id.toString());
            linkWrite(c,"insert website link","INSERT INTO ultimate_shop_links(token_hash,player_id,player_name,expires) VALUES(?,?,?,?)",hash(token),id.toString(),name,expires);
        }
    }
    private static void linkWrite(Connection connection, String operation, String sql, Object... parameters) throws SQLException {
        try {
            update(connection, sql, parameters);
        } catch (SQLException error) {
            throw DatabaseFailure.during(operation, error);
        }
    }

    public Map<String,Long> read(UUID id,String name)throws Exception {
        try(var c=connect()){
            if(name!=null)update(c,"INSERT INTO ultimate_shop_players(player_id,player_name,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE player_name=VALUES(player_name),updated_at=VALUES(updated_at)",id.toString(),name,System.currentTimeMillis());
            Map<String,Long> out=new HashMap<>();
            try(var q=c.prepareStatement("SELECT g.package_id,MAX(g.expires) FROM ultimate_shop_grants g LEFT JOIN ultimate_shop_blocks b ON b.player_id=g.player_id AND b.package_id=g.package_id WHERE g.player_id=? AND b.player_id IS NULL AND g.expires>? GROUP BY g.package_id")){
                q.setString(1,id.toString());q.setLong(2,System.currentTimeMillis());try(var r=q.executeQuery()){while(r.next())out.put(r.getString(1),r.getLong(2));}
            }return out;
        }
    }
    public void heartbeat(String serverId)throws Exception {try(var c=connect()){update(c,"INSERT INTO ultimate_shop_backends(server_id,heartbeat) VALUES(?,?) ON DUPLICATE KEY UPDATE heartbeat=VALUES(heartbeat)",serverId,System.currentTimeMillis());}}
    public record Delivery(String id,String pack,int amount,String claim){}
    public void keyHeartbeat(String backend,Set<String> packs)throws Exception {try(var c=connect()){update(c,"DELETE FROM ultimate_shop_key_backends WHERE server_id=?",backend);for(String pack:packs)update(c,"INSERT INTO ultimate_shop_key_backends(server_id,package_id,heartbeat) VALUES(?,?,?)",backend,pack,System.currentTimeMillis());}}
    public List<Delivery> pending(UUID id)throws Exception {try(var c=connect();var q=c.prepareStatement("SELECT id,package_id,amount FROM ultimate_shop_key_deliveries WHERE player_id=? AND state='pending' ORDER BY updated_at LIMIT 10")){q.setString(1,id.toString());try(var r=q.executeQuery()){List<Delivery> out=new ArrayList<>();while(r.next())out.add(new Delivery(r.getString(1),r.getString(2),r.getInt(3),null));return out;}}}
    public boolean claim(Delivery d,UUID player,String backend,String claim)throws Exception {try(var c=connect();var q=c.prepareStatement("UPDATE ultimate_shop_key_deliveries SET state='claimed',claim_id=?,backend=?,updated_at=? WHERE id=? AND player_id=? AND state='pending'")){q.setString(1,claim);q.setString(2,backend);q.setLong(3,System.currentTimeMillis());q.setString(4,d.id());q.setString(5,player.toString());return q.executeUpdate()==1;}}
    public void finish(String id,String claim,boolean delivered)throws Exception {try(var c=connect()){update(c,"UPDATE ultimate_shop_key_deliveries SET state=?,updated_at=? WHERE id=? AND claim_id=? AND state='claimed'",delivered?"delivered":"pending",System.currentTimeMillis(),id,claim);}}
    public void keyGrant(UUID player,String pack,int count)throws Exception {String id=UUID.randomUUID().toString();try(var c=connect()){update(c,"INSERT INTO ultimate_shop_key_deliveries(id,source,player_id,package_id,amount,state,updated_at) VALUES(?,?,?,?,?,'pending',?)",id,"manual:"+id,player.toString(),pack,count,System.currentTimeMillis());}}
    public List<String> deliveries(UUID player)throws Exception {try(var c=connect();var q=c.prepareStatement("SELECT id,package_id,amount,state,backend FROM ultimate_shop_key_deliveries WHERE player_id=? ORDER BY updated_at DESC LIMIT 20")){q.setString(1,player.toString());try(var r=q.executeQuery()){List<String> out=new ArrayList<>();while(r.next())out.add(r.getString(1)+" "+r.getString(2)+" x"+r.getInt(3)+" "+r.getString(4)+" "+r.getString(5));return out;}}}
    public boolean retry(String delivery)throws Exception {try(var c=connect();var q=c.prepareStatement("UPDATE ultimate_shop_key_deliveries SET state='pending',claim_id=NULL,backend=NULL,updated_at=? WHERE id=? AND state IN ('claimed','review')")){q.setLong(1,System.currentTimeMillis());q.setString(2,delivery);return q.executeUpdate()==1;}}
    public UUID resolve(String target)throws Exception {
        try{return UUID.fromString(target);}catch(IllegalArgumentException ignored){}
        try(var c=connect();var q=c.prepareStatement("SELECT player_id FROM ultimate_shop_players WHERE LOWER(player_name)=LOWER(?)")){
            q.setString(1,target);try(var r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Player must have joined with UltimateShop installed, or use their exact UUID.");UUID id=UUID.fromString(r.getString(1));if(r.next())throw new IllegalArgumentException("Ambiguous name. Use exact UUID.");return id;}
        }
    }
    public void give(UUID id,String pack,long expires)throws Exception {
        try(var c=connect()){c.setAutoCommit(false);try{
            update(c,"DELETE FROM ultimate_shop_blocks WHERE player_id=? AND package_id=?",id.toString(),pack);
            update(c,"INSERT INTO ultimate_shop_grants(source,player_id,package_id,expires) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE expires=GREATEST(expires,VALUES(expires))","manual:"+id+":"+pack,id.toString(),pack,expires);
            c.commit();}catch(Exception e){c.rollback();throw e;}}
    }
    public void revoke(UUID id,String pack)throws Exception {
        try(var c=connect()){c.setAutoCommit(false);try{
            update(c,"INSERT IGNORE INTO ultimate_shop_blocks(player_id,package_id) VALUES(?,?)",id.toString(),pack);
            update(c,"DELETE FROM ultimate_shop_grants WHERE source=?","manual:"+id+":"+pack);
            c.commit();}catch(Exception e){c.rollback();throw e;}}
    }
    static int update(Connection c,String sql,Object...args)throws SQLException {try(var q=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);return q.executeUpdate();}}
}
