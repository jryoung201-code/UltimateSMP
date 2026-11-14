package shop.blinksmp.settings;
import java.util.*;
public final class RankDisplayTest {
    public static void main(String[] args){
        Map<String,Integer> weights=Map.of("MEMBER",0,"BLINK_PLUS",10,"BLINK_PLUS_PLUS",20,"BLINK_PLUS_PLUS_PLUS",30,"ADMIN",400,"OWNER",600);
        Map<String,String> keys=RankDisplay.keys(weights);
        if(new HashSet<>(keys.values()).size()!=weights.size())throw new AssertionError("Donor/member teams collided");
        List<String> byKey=keys.keySet().stream().sorted(Comparator.comparing(keys::get)).toList();
        if(!byKey.equals(List.of("OWNER","ADMIN","BLINK_PLUS_PLUS_PLUS","BLINK_PLUS_PLUS","BLINK_PLUS","MEMBER")))throw new AssertionError("Wrong default TAB order");
        Map<String,String> extremes=RankDisplay.keys(Map.of("OWNER",Integer.MIN_VALUE,"MEMBER",Integer.MAX_VALUE,"DEV",0,"ADMIN",0));
        if(!extremes.keySet().stream().sorted(Comparator.comparing(extremes::get)).toList().equals(List.of("MEMBER","ADMIN","DEV","OWNER")))throw new AssertionError("Custom weights/ties/overflow");
        if(keys.values().stream().anyMatch(v->v.length()>16))throw new AssertionError("Invalid scoreboard team length");
        if(!RankDisplay.keys(weights).equals(keys))throw new AssertionError("Unstable keys");
        System.out.println("PASS: weighted TAB team order, donor tag isolation, ties and integer extremes");
    }
}
