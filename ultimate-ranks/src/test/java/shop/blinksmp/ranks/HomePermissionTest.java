package shop.blinksmp.ranks;

import java.lang.reflect.Proxy;
import java.util.Map;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

/** Exercise the exact attachment grant used at login and /rank set, using Bukkit's real attachment. */
public final class HomePermissionTest {
    public static void main(String[] args) {
        Plugin plugin=(Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class<?>[]{Plugin.class},(p,m,a)->switch(m.getName()) {
            case "isEnabled"->true;case "getName"->"UltimateRanksTest";case "toString"->"UltimateRanksTest";default->null;
        });
        Permissible holder=(Permissible)Proxy.newProxyInstance(Permissible.class.getClassLoader(),new Class<?>[]{Permissible.class},(p,m,a)->null);
        Rank[] ranks={Rank.ULTIMATE_PLUS,Rank.ULTIMATE_PLUS_PLUS,Rank.ULTIMATE_PLUS_PLUS_PLUS};
        String[] expected={"maxhomes.3","maxhomes.5","maxhomes.9"};
        for(int i=0;i<ranks.length;i++) {
            PermissionAttachment attachment=new PermissionAttachment(plugin,holder);
            UltimateRanks.grantHomePermission(attachment,ranks[i]);
            if(!attachment.getPermissions().equals(Map.of(expected[i],true)))throw new AssertionError("Wrong effective grant for "+ranks[i]);
        }
        for(Rank rank:Rank.values())if(rank.homes==0 && rank!=Rank.OWNER) {
            PermissionAttachment attachment=new PermissionAttachment(plugin,holder);
            UltimateRanks.grantHomePermission(attachment,rank);
            if(!attachment.getPermissions().isEmpty())throw new AssertionError("Existing rank home permissions changed: "+rank);
        }
        PermissionAttachment owner=new PermissionAttachment(plugin,holder);
        UltimateRanks.grantHomePermission(owner,Rank.OWNER);
        if(!owner.getPermissions().equals(Map.of("maxhomes.unlimited",true)))throw new AssertionError("OWNER must have unlimited homes");
        System.out.println("PASS: exact Bukkit home permission grants for Ultimate ranks; legacy ranks unaffected");
    }
}

