package shop.blinksmp.ranks;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import static shop.blinksmp.ranks.RankStore.*;
public final class MultiRankTest {
    private static int checks;
    private static void check(boolean c,String name){checks++;if(!c)throw new AssertionError(name);}
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("multi-rank-");UUID id=UUID.randomUUID();
        try{
            Path file=dir.resolve("players.yml");
            Files.writeString(file,"players:\n  "+id+":\n    name: Legacy\n    rank: ADMIN\n");
            RankStore store=new RankStore(file);
            check(store.get(id).ranks().equals(Set.of(Rank.ADMIN)),"Legacy single rank migrated");
            Entry both=store.get(id).added(Rank.BLINK_PLUS_PLUS_PLUS).muted(new Sanction(0,"reason","Console",6));
            store.put(id,both);
            check(new RankStore(file).get(id).equals(both),"Multiple ranks survive restart");
            check(both.rank()==Rank.ADMIN,"Donor rank preserves staff authority");
            check(Rank.display(both.ranks(),Map.of())==Rank.ADMIN,"Default visible rank uses weights");
            Map<Rank,Integer> inverted=Map.of(Rank.ADMIN,-10,Rank.BLINK_PLUS_PLUS_PLUS,999);
            check(Rank.display(both.ranks(),inverted)==Rank.BLINK_PLUS_PLUS_PLUS,"Configured display weight wins");
            check(both.rank()==Rank.ADMIN && !Rules.allowed(Rank.BLINK_PLUS_PLUS_PLUS,"rank"),"Weights never escalate permissions");
            check(!Rules.canAssign(Rank.ADMIN,both.added(Rank.OWNER).rank(),Rank.MEMBER),"Hidden OWNER membership still protects target");
            check(both.added(Rank.ADMIN).ranks().size()==2,"Duplicate add is idempotent");
            check(both.removed(Rank.ADMIN).ranks().equals(Set.of(Rank.BLINK_PLUS_PLUS_PLUS)),"Targeted removal preserves other ranks");
            check(both.removed(Rank.ADMIN).removed(Rank.BLINK_PLUS_PLUS_PLUS).ranks().equals(Set.of(Rank.MEMBER)),"Last removal falls back to MEMBER");
            check(both.ranked(Rank.MOD).ranks().equals(Set.of(Rank.MOD)),"Set replaces all memberships");
            check(both.named("Rename").muted(null).banned(null).ranks().equals(both.ranks()),"Name and sanctions preserve all ranks");
            RankStore replica=new RankStore(dir.resolve("replica.yml"));
            replica.importNetworkModule(id,"ranks",store.exportNetworkModule(id,"ranks"));
            replica.importNetworkModule(id,"sanctions",store.exportNetworkModule(id,"sanctions"));
            check(replica.get(id).equals(both),"Both network modules preserve memberships and sanctions");
            Entry before=replica.get(id);
            try{replica.importNetworkModule(id,"ranks","version: 1\nranks: [ADMIN, NOT_A_RANK]\n");throw new AssertionError("Unknown rank accepted");}catch(IOException expected){checks++;}
            check(replica.get(id).equals(before),"Malformed network data preserves authorization");
            replica.importNetworkModule(id,"ranks","version: 1\nrank: HELPER\n");
            check(replica.get(id).ranks().equals(Set.of(Rank.HELPER)),"Legacy network payload accepted");
            Rank tie=Rank.display(Set.of(Rank.MOD,Rank.ADMIN),Map.of(Rank.MOD,5,Rank.ADMIN,5));
            check(tie==Rank.display(Set.of(Rank.ADMIN,Rank.MOD),Map.of(Rank.MOD,5,Rank.ADMIN,5)),"Weight ties deterministic");
            System.out.println("PASS: "+checks+" multi-rank migration, weighted display, authority and network checks");
        }finally{try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
