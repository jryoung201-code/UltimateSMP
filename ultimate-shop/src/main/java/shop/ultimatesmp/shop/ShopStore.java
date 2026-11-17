package shop.ultimatesmp.shop;

import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Shared entitlement records are desired state, not commands that can execute twice. */
public final class ShopStore {
    private final String url,user,password;
    public ShopStore(String url,String user,String password){this.url=url;this.user=user;this.password=password;}
    private Connection connect() throws SQLException {var p=new Properties();p.setProperty("user",user);p.setProperty("password",password);p.setProperty("connectTimeout","10000");p.setProperty("socketTimeout","15000");return DriverManager.getConnection(url,p);}
    public void initialize() throws Exception {
        try(var c=connect();var s=c.createStatement();var in=ShopStore.class.getResourceAsStream("/schema.sql")){
            for(String sql:new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())s.execute(sql);
        }
    }
    public static String hash(String token) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
    public void link(UUID id,String name,String token,long expires)throws Exception {
        try(var c=connect()){
            update(c,"DELETE FROM ultimate_shop_links WHERE expires < ?",System.currentTimeMillis());
            update(c,"DELETE FROM ultimate_shop_links WHERE player_id=?",id.toString());
            update(c,"INSERT INTO ultimate_shop_links(token_hash,player_id,player_name,expires) VALUES(?,?,?,?)",hash(token),id.toString(),name,expires);
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
    private static void update(Connection c,String sql,Object...args)throws SQLException {try(var q=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);q.executeUpdate();}}
}
