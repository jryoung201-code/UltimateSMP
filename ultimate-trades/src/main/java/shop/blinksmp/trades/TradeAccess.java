package shop.blinksmp.trades;

import org.bukkit.GameMode;

/** Shared eligibility policy for requests and active sessions. */
public final class TradeAccess {
    private TradeAccess() {}
    public enum Issue { NONE,NO_PERMISSION,DEAD,SPECTATOR,CREATIVE_BLOCKED,REQUESTS_OFF,BUSY }
    public static Issue check(boolean permission,boolean dead,GameMode mode,boolean allowCreative,boolean requestsOff,boolean busy) {
        if(!permission) return Issue.NO_PERMISSION;
        if(dead) return Issue.DEAD;
        if(mode==GameMode.SPECTATOR) return Issue.SPECTATOR;
        if(mode==GameMode.CREATIVE && !allowCreative) return Issue.CREATIVE_BLOCKED;
        if(requestsOff) return Issue.REQUESTS_OFF;
        if(busy) return Issue.BUSY;
        return Issue.NONE;
    }
}
