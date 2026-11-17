package shop.ultimatesmp.shop;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
public class ShopStoreTest {
    @Test void linkHashesAreDeterministicAndDoNotStoreTheCode()throws Exception {String code="a".repeat(48);assertEquals(64,ShopStore.hash(code).length());assertEquals(ShopStore.hash(code),ShopStore.hash(code));assertNotEquals(ShopStore.hash(code),ShopStore.hash("b".repeat(48)));}
    @Test void sharedStateGrantRevokeReconnectExpiryAndBedrockIdentity()throws Exception {
        String url=System.getenv("TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"Integration database is provided by CI");
        ShopStore a=new ShopStore(url,"root","disposable"),b=new ShopStore(url,"root","disposable");a.initialize();b.initialize();UUID id=UUID.randomUUID();
        String name=".test"+id.toString().substring(0,8);a.read(id,name);assertEquals(id,b.resolve(name));a.give(id,"ultimate-plus-plus",System.currentTimeMillis()+60000);assertTrue(b.read(id,null).containsKey("ultimate-plus-plus"));
        b.revoke(id,"ultimate-plus-plus");assertTrue(a.read(id,null).isEmpty());a.give(id,"ultimate-plus-plus",System.currentTimeMillis()-1);assertTrue(b.read(id,null).isEmpty());
        UUID bedrock=UUID.fromString("00000000-0000-0000-0009-01fb5b0f0d11");a.read(bedrock,".JACKPOTth25");assertEquals(bedrock,b.resolve(".JACKPOTth25"));
        a.link(id,name,"c".repeat(48),System.currentTimeMillis()+60000);a.heartbeat("test-backend");
        try(var c=DriverManager.getConnection(url,"root","disposable");var q=c.prepareStatement("SELECT token_hash,player_id FROM ultimate_shop_links WHERE player_id=?")){q.setString(1,id.toString());try(var r=q.executeQuery()){assertTrue(r.next());assertEquals(ShopStore.hash("c".repeat(48)),r.getString(1));assertEquals(id.toString(),r.getString(2));}}
    }
}
