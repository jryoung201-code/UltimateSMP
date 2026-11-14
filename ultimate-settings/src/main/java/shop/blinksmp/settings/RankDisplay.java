package shop.blinksmp.settings;
import java.util.*;
/** Unique teams for every visible rank, ordered by configured weight. */
public final class RankDisplay {
    private RankDisplay(){}
    public static Map<String,String> keys(Map<String,Integer> weights) {
        List<String> ordered=new ArrayList<>(weights.keySet());
        ordered.sort(Comparator.<String>comparingInt(weights::get).reversed().thenComparing(Comparator.naturalOrder()));
        Map<String,String> keys=new HashMap<>();
        for(int i=0;i<ordered.size();i++)keys.put(ordered.get(i),String.format(Locale.ROOT,"us%08d",i));
        return Map.copyOf(keys);
    }
}
