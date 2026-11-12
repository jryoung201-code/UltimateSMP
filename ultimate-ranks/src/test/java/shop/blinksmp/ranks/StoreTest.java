package shop.blinksmp.ranks;

import java.nio.file.*;
import java.util.*;
import static shop.blinksmp.ranks.RankStore.*;

public final class StoreTest {
    private static void check(boolean c,String m) { if(!c) throw new AssertionError(m); }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("ultimate-ranks-test-"); Path file=dir.resolve("players.yml");
        UUID id=UUID.randomUUID();
        try {
            RankStore first=new RankStore(file);
            check(first.get(id).rank()==Rank.MEMBER,"Unknown players must default to MEMBER");
            Sanction ban=new Sanction(0,"test reason","Console",7);
            Sanction mute=new Sanction(500,"expired","Mod",3);
            Entry original=new Entry(".BedrockName",Rank.MOD,mute,ban,List.of("warning"));
            first.put(id,original);
            RankStore second=new RankStore(file);
            check(second.get(id).equals(original),"UUID, rank, name and punishments must survive reload");
            check(second.get(id).ban().active(Long.MAX_VALUE),"Permanent ban must not expire");
            check(!second.get(id).mute().active(500),"Mute must expire at exact expiry");
            second.put(id,second.get(id).ranked(Rank.ADMIN).banned(null));
            RankStore third=new RankStore(file);
            check(third.get(id).rank()==Rank.ADMIN,"Rank update must persist");
            check(third.get(id).ban()==null,"Unban must persist");
            check(third.get(id).warnings().equals(List.of("warning")),"Unrelated history must survive");
            // Added ranks survive restart without changing another player's sanctions or legacy rank.
            for(Rank premium:new Rank[]{Rank.BLINK_PLUS,Rank.BLINK_PLUS_PLUS,Rank.BLINK_PLUS_PLUS_PLUS}) {
                UUID subscriber=UUID.randomUUID();Entry entry=new Entry("Subscriber",premium,null,null,List.of());
                third.put(subscriber,entry);RankStore reloaded=new RankStore(file);
                check(reloaded.get(subscriber).equals(entry),"Premium must survive reload: "+premium);
                check(reloaded.get(id).equals(third.get(id)),"Legacy player data must survive new premium entries");
                reloaded.put(subscriber,reloaded.get(subscriber).ranked(Rank.MEMBER));
                check(new RankStore(file).get(subscriber).rank()==Rank.MEMBER,"Premium removal must persist");
                third=new RankStore(file);
            }
            // Move the data directory away, then replace it with a regular file to force a save failure.
            Path archived=dir.resolveSibling(dir.getFileName()+"-moved");
            Files.move(dir,archived); Files.writeString(dir,"block writes");
            try {
                boolean failed=false;
                try { third.put(id,third.get(id).ranked(Rank.OWNER)); } catch(java.io.IOException ex) { failed=true; }
                check(failed,"Save failure must be reported");
                check(third.get(id).rank()==Rank.ADMIN,"Failed save must not change live authorization");
            } finally { Files.delete(dir); Files.move(archived,dir); }
            Files.writeString(file,"players: [invalid yaml structure");
            boolean failed=false; try { new RankStore(file); } catch(Exception ex) { failed=true; }
            check(failed,"Corrupt YAML must be rejected without overwriting it");
            System.out.println("PASS: rank/punishment persistence, expiry, failed save and corrupt data checks");
        } finally {
            try(var paths=Files.walk(dir)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
}
