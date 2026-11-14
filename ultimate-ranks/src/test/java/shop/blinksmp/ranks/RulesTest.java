package shop.blinksmp.ranks;

public final class RulesTest {
    private static int checks;
    private static void check(boolean c,String message) { checks++; if(!c) throw new AssertionError(message); }
    private static void invalid(String text) {
        boolean threw=false; try { Rules.duration(text); } catch(IllegalArgumentException|ArithmeticException ex) { threw=true; }
        check(threw,"Invalid duration accepted: "+text);
    }
    public static void main(String[] args) {
        for(Rank actor:Rank.values()) for(Rank target:Rank.values()) for(Rank assigned:Rank.values()) {
            boolean allowed=Rules.canAssign(actor,target,assigned);
            if(target.level>=actor.level || assigned.level>=actor.level) check(!allowed,"Rank escalation permitted");
            if(actor.level<Rank.ADMIN.level) check(!allowed,"Non-admin rank change permitted");
        }
        check(!Rules.canAssign(Rank.OWNER,Rank.MEMBER,Rank.OWNER),"OWNER must be console-assigned");
        check(Rules.canAssign(Rank.ADMIN,Rank.MEMBER,Rank.MOD),"Admin should assign moderator");
        check(Rules.canAssign(Rank.OWNER,Rank.DEV,Rank.ADMIN),"Owner should demote dev");
        check(!Rules.canReverse(Rank.MOD,Rank.MEMBER,Rank.ADMIN.level),"Mod reversed admin action");
        check(!Rules.canReverse(Rank.MOD,Rank.MOD,Rank.HELPER.level),"Mod acted on peer");
        check(Rules.canReverse(Rank.ADMIN,Rank.MOD,Rank.MOD.level),"Admin should reverse mod action");
        check(!Rules.canReverse(Rank.OWNER,Rank.MEMBER,7),"Player reversed console action");
        for(Rank premium:new Rank[]{Rank.ULTIMATE_PLUS,Rank.ULTIMATE_PLUS_PLUS,Rank.ULTIMATE_PLUS_PLUS_PLUS}) {
            check(Rank.parse(premium.label())==premium,"Display rank name must parse: "+premium);
            check(Rank.parse(premium.label().toLowerCase(java.util.Locale.ROOT))==premium,"Case-insensitive plus rank");
            check(Rank.parse(premium.name())==premium,"Stored rank name must parse");
            check(premium.level==Rank.MEMBER.level,"Premium rank must remain at member level");
            check(!premium.above(Rank.MEMBER)&&!Rank.MEMBER.above(premium),"Premium must not gain moderation priority over member");
            for(String cmd:Rules.MINIMUM.keySet())check(!Rules.allowed(premium,cmd),"Premium staff escalation: "+cmd);
            check(Rules.canAssign(Rank.ADMIN,Rank.MEMBER,premium),"Admin should be able to assign premium");
        }
        check(Rank.ULTIMATE_PLUS.homes==3&&Rank.ULTIMATE_PLUS_PLUS.homes==5&&Rank.ULTIMATE_PLUS_PLUS_PLUS.homes==9,"Requested home limits");
        for(Rank rank:Rank.values())if(rank.level>0||rank==Rank.MEMBER)check(rank.homes==0,"Existing ranks must keep their configured home permissions");
        for(String invalid:new String[]{"Ultimate++++","Blink","ULTIMATE_PLUS_PLUS_PLUS_PLUS"}) {
            boolean rejected=false;try{Rank.parse(invalid);}catch(IllegalArgumentException ex){rejected=true;}
            check(rejected,"Unknown subscription tier accepted");
        }
        check(Rank.parse("OWER")==Rank.OWNER,"OWER alias");
        check(Rank.parse("trial mod")==Rank.TRIAL_MOD,"Space alias");
        check(Rank.parse("trial-mod")==Rank.TRIAL_MOD,"Hyphen alias");
        check(Rules.duration("10m")==600000,"Minutes");
        check(Rules.duration("2H")==7200000,"Case-insensitive hours");
        check(Rules.duration("7d")==604800000,"Days");
        for(String input:new String[]{"0m","-1m","forever","1.5h","9999999999999999d","366d","2h30m","10"}) invalid(input);
        check(Rules.maxMute(Rank.HELPER)==Rules.duration("10m"),"Helper mute limit");
        check(Rules.maxMute(Rank.TRIAL_MOD)==Rules.duration("1h"),"Trial mute limit");
        check(Rules.maxTempban(Rank.MOD)==Rules.duration("7d"),"Mod ban limit");
        for(String cmd:Rules.MINIMUM.keySet()) check(!Rules.allowed(Rank.MEMBER,cmd),"Member staff command: "+cmd);
        check(!Rules.allowed(Rank.HELPER,"kick"),"Helper kick");
        check(!Rules.allowed(Rank.TRIAL_MOD,"tempban"),"Trial tempban");
        check(!Rules.allowed(Rank.MOD,"ban"),"Mod permanent ban");
        check(Rules.allowed(Rank.ADMIN,"ban"),"Admin permanent ban");
        check(!Rules.allowed(Rank.OWNER,"unknown"),"Unknown action permitted");
        System.out.println("PASS: "+checks+" rank authorization and duration checks");
    }
}

