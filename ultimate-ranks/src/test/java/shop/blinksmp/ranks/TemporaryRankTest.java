package shop.blinksmp.ranks;
import java.util.*;
public final class TemporaryRankTest {
 public static void main(String[] args){Set<Rank> permanent=Set.of(Rank.MEMBER,Rank.ULTIMATE_PLUS);Map<Rank,Long> temporary=Map.of(Rank.OWNER,100L,Rank.ULTIMATE_PLUS,100L);if(!TemporaryRankPolicy.combine(permanent,temporary,99).contains(Rank.OWNER))throw new AssertionError("Active staff overlay missing");var expired=TemporaryRankPolicy.combine(permanent,temporary,100);if(expired.contains(Rank.OWNER)||!expired.contains(Rank.ULTIMATE_PLUS))throw new AssertionError("Expiry modified permanent ranks");if(!permanent.equals(Set.of(Rank.MEMBER,Rank.ULTIMATE_PLUS)))throw new AssertionError("Permanent set mutated");System.out.println("Temporary rank overlay preservation passed");}
}
