package shop.blinksmp.ranks;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Player;

/** API bridge tests without a live Paper server or fake OP authorization. */
public final class VanishBridgeTest {
    public static final class Gui {
        final UUID owner;boolean hidden,fail;int updates;
        Gui(UUID owner) { this.owner=owner; }
        public boolean controlsVanish(UUID id) { return owner.equals(id); }
        public boolean isVanished(UUID id) { return controlsVanish(id) && hidden; }
        public boolean setOwnerVanish(Player player,boolean state) { if(fail || !controlsVanish(player.getUniqueId())) return false;hidden=state;updates++;return true; }
    }
    private static Player player(UUID id) { return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)-> m.getName().equals("getUniqueId")?id:null); }
    private static int checks;
    private static void check(boolean value,String label) { checks++;if(!value) throw new AssertionError(label); }
    public static void main(String[] args) throws Exception {
        UUID owner=UUID.randomUUID();Player p=player(owner),other=player(UUID.randomUUID());Gui gui=new Gui(owner);
        check(!VanishBridge.toggle(null,p,false),"Missing GUI falls back to rank path");
        check(!VanishBridge.toggle(new Object(),p,false),"Older GUI without bridge falls back");
        check(!VanishBridge.toggle(gui,other,false) && gui.updates==0,"Other UUIDs cannot operate owner vanish");
        check(VanishBridge.toggle(gui,p,false) && gui.hidden,"Owner normal rank toggle enables GUI vanish");
        check(VanishBridge.toggle(gui,p,false) && !gui.hidden,"Same route disables GUI vanish");
        check(VanishBridge.toggle(gui,p,true) && !gui.hidden,"Old independent rank hide toggles OFF instead of creating a second state");
        gui.hidden=true;check(VanishBridge.toggle(gui,p,true) && !gui.hidden,"Both hide states produce OFF");
        gui.fail=true;
        try { VanishBridge.toggle(gui,p,false);throw new AssertionError("Failed save was treated as success"); }
        catch(IllegalStateException expected) { checks++; }
        check(!gui.hidden,"Failed GUI update never toggles rank fallback");
        gui.fail=false;check(VanishBridge.set(gui,p,true) && gui.hidden,"Migrate an existing owner rank hide on GUI enable");
        System.out.println("PASS: "+checks+" vanish bridge UUID, shared-state, migration and failure checks");
    }
}

