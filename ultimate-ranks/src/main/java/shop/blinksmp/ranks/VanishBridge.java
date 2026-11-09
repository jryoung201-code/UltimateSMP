package shop.blinksmp.ranks;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Optional UUID-authorized GUI bridge. No linkage to GUI classes or OP/name bypass. */
public final class VanishBridge {
    private VanishBridge() {}
    public static boolean controls(Object gui,UUID id) throws ReflectiveOperationException {
        if(gui==null) return false;
        Method owns;
        try { owns=gui.getClass().getMethod("controlsVanish",UUID.class); }
        catch(NoSuchMethodException oldVersion) { return false; }
        return Boolean.TRUE.equals(owns.invoke(gui,id));
    }
    public static boolean set(Object gui,Player player,boolean enabled) throws ReflectiveOperationException {
        if(!controls(gui,player.getUniqueId())) return false;
        if(!Boolean.TRUE.equals(gui.getClass().getMethod("setOwnerVanish",Player.class,boolean.class).invoke(gui,player,enabled)))
            throw new IllegalStateException("Owner vanish change failed; check GUI status/console.");
        return true;
    }
    public static boolean toggle(Object gui,Player player,boolean localHidden) throws ReflectiveOperationException {
        if(!controls(gui,player.getUniqueId())) return false;
        boolean hidden=Boolean.TRUE.equals(gui.getClass().getMethod("isVanished",UUID.class).invoke(gui,player.getUniqueId()));
        return set(gui,player,!(hidden||localHidden));
    }
}
