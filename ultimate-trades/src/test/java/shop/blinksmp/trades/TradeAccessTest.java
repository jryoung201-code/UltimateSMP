package shop.blinksmp.trades;

import org.bukkit.GameMode;

public final class TradeAccessTest {
    private static int checks;
    private static void check(TradeAccess.Issue expected,boolean permission,boolean dead,GameMode mode,boolean creative,boolean off,boolean busy) {
        checks++;if(TradeAccess.check(permission,dead,mode,creative,off,busy)!=expected) throw new AssertionError(expected+" "+mode);
    }
    public static void main(String[] args) {
        for(GameMode mode:new GameMode[]{GameMode.SURVIVAL,GameMode.ADVENTURE,GameMode.CREATIVE}) {
            check(TradeAccess.Issue.NONE,true,false,mode,true,false,false);
            check(TradeAccess.Issue.REQUESTS_OFF,true,false,mode,true,true,false);
            check(TradeAccess.Issue.BUSY,true,false,mode,true,false,true);
            check(TradeAccess.Issue.NO_PERMISSION,false,false,mode,true,false,false);
            check(TradeAccess.Issue.DEAD,true,true,mode,true,false,false);
        }
        check(TradeAccess.Issue.CREATIVE_BLOCKED,true,false,GameMode.CREATIVE,false,false,false);
        check(TradeAccess.Issue.NONE,true,false,GameMode.SURVIVAL,false,false,false);
        check(TradeAccess.Issue.NONE,true,false,GameMode.ADVENTURE,false,false,false);
        check(TradeAccess.Issue.SPECTATOR,true,false,GameMode.SPECTATOR,true,false,false);
        check(TradeAccess.Issue.SPECTATOR,true,false,GameMode.SPECTATOR,false,false,false);
        System.out.println("PASS: "+checks+" creative opt-out, spectator, permission, disabled-request and busy checks");
    }
}
