package shop.blinksmp.ranks;
import java.util.*;
final class TemporaryRankPolicy {
 static Set<Rank> combine(Set<Rank> permanent,Map<Rank,Long> temporary,long now){Set<Rank> all=new HashSet<>(permanent);temporary.forEach((rank,expiry)->{if(expiry>now)all.add(rank);});return Set.copyOf(all);}
}
