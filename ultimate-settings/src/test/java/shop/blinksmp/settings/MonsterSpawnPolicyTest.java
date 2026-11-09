package shop.blinksmp.settings;

import java.util.List;
import shop.blinksmp.settings.MonsterSpawnPolicy.Candidate;
public final class MonsterSpawnPolicyTest {
    private static int checks;
    private static void check(boolean value,String label) { checks++;if(!value) throw new AssertionError(label); }
    private static boolean cancelled(boolean natural,boolean monster,Candidate... players) {
        return MonsterSpawnPolicy.cancel(natural,monster,40,64,0,List.of(players));
    }
    public static void main(String[] args) {
        Candidate excluded=new Candidate(0,64,0,128,true,true),normal=new Candidate(80,64,0,128,false,true);
        check(cancelled(true,true,excluded),"Sole opted-out player cannot permit natural monsters");
        check(!cancelled(true,false,excluded),"Animals unaffected");
        check(!cancelled(false,true,excluded),"Spawner/egg/raid/custom reasons unaffected");
        check(!cancelled(true,true,normal),"Normal participation");
        check(!cancelled(true,true,excluded,normal),"Nearby eligible normal player permits shared-world spawn");
        check(!cancelled(true,true,normal,excluded),"Player iteration order irrelevant");
        check(cancelled(true,true,excluded,new Candidate(400,64,0,128,false,true)),"Distant normal player cannot override exclusion");
        check(cancelled(true,true,excluded,new Candidate(80,300,0,128,false,true)),"Vertical natural spawn range");
        check(cancelled(true,true,excluded,new Candidate(80,64,0,128,false,false)),"Spectator/dead/non-affecting player ignored");
        check(cancelled(true,true,excluded,new Candidate(80,64,0,0,false,true)),"Cancelled spawning-radius event ignored");
        check(cancelled(true,true,excluded,new Candidate(80,64,0,16,false,true)),"Player-specific small spawn range honored");
        check(!cancelled(true,true),"Unattributed custom natural attempt not intercepted");
        check(!cancelled(true,true,new Candidate(0,64,0,0,true,true)),"Opted-out but already spawning-disabled player is not a reason for veto");
        check(MonsterSpawnPolicy.cancel(true,true,-40,64,0,List.of(excluded)),"Negative chunk coordinates");
        System.out.println("PASS: "+checks+" monster-only, natural-only, overlap and player-range checks");
    }
}
