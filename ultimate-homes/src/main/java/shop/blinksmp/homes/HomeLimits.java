package shop.blinksmp.homes;

import java.util.Locale;
import java.util.Map;

public final class HomeLimits {
    private HomeLimits() {}
    public static int resolve(Map<String,Boolean> permissions,int serverCap) {
        int result=0;
        for(var entry:permissions.entrySet()) {
            String node=entry.getKey().toLowerCase(Locale.ROOT);
            if(!Boolean.TRUE.equals(entry.getValue()) || !node.startsWith("maxhomes.")) continue;
            String suffix=node.substring(9);
            if(!suffix.matches("[0-9]+")) continue;
            try { result=Math.max(result,Math.min(serverCap,Integer.parseInt(suffix))); }
            catch(NumberFormatException ignored) {} // Overflow is not an unlimited permission.
        }
        return result;
    }
    public static String name(String input) {
        if(input==null || !input.matches("[A-Za-z0-9_-]{1,24}")) throw new IllegalArgumentException("Use 1-24 letters, numbers, underscores or hyphens for a home name.");
        return input.toLowerCase(Locale.ROOT);
    }
}
