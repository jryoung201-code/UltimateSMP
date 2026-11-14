package shop.blinksmp.ranks;

import java.util.*;
import java.util.regex.*;

public final class Rules {
    private Rules() {}
    public static final Map<String,Rank> MINIMUM = Map.ofEntries(
        Map.entry("warn",Rank.HELPER), Map.entry("warnings",Rank.HELPER), Map.entry("mute",Rank.HELPER),
        Map.entry("unmute",Rank.HELPER), Map.entry("staffchat",Rank.HELPER),
        Map.entry("kick",Rank.TRIAL_MOD), Map.entry("freeze",Rank.TRIAL_MOD),
        Map.entry("vanish",Rank.TRIAL_MOD), Map.entry("invsee",Rank.TRIAL_MOD),
        Map.entry("tp",Rank.TRIAL_MOD), Map.entry("bring",Rank.MOD),
        Map.entry("tempban",Rank.MOD), Map.entry("unban",Rank.MOD),
        Map.entry("gsp",Rank.MOD), Map.entry("ban",Rank.ADMIN),
        Map.entry("heal",Rank.ADMIN), Map.entry("feed",Rank.ADMIN),
        Map.entry("rank",Rank.ADMIN));
    public static boolean allowed(Rank actor, String command) {
        Rank minimum=MINIMUM.get(command);
        return minimum!=null && actor.level>=minimum.level;
    }
    public static boolean canAssign(Rank actor, Rank target, Rank assigned) {
        return allowed(actor,"rank") && actor.above(target) && actor.above(assigned);
    }
    // Moderators cannot silently reverse an action issued by a higher rank.
    public static boolean canReverse(Rank actor, Rank target, int issuerLevel) {
        return actor.above(target) && actor.level>=issuerLevel;
    }
    public static long duration(String value) {
        Matcher m=Pattern.compile("([1-9][0-9]{0,8})([smhdw])",Pattern.CASE_INSENSITIVE).matcher(value);
        if(!m.matches()) throw new IllegalArgumentException("Use a duration like 10m, 2h or 7d.");
        long factor=switch(m.group(2).toLowerCase(Locale.ROOT)) {
            case "s"->1000; case "m"->60000; case "h"->3600000;
            case "d"->86400000; default->604800000;
        };
        long result=Math.multiplyExact(Long.parseLong(m.group(1)),factor);
        if(result>365L*86400000) throw new IllegalArgumentException("Maximum duration is 365d.");
        return result;
    }
    public static long maxMute(Rank rank) {
        return switch(rank) { case HELPER->600000; case TRIAL_MOD->3600000; default->365L*86400000; };
    }
    public static long maxTempban(Rank rank) { return rank==Rank.MOD ? 7L*86400000 : 365L*86400000; }
}

