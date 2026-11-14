package shop.blinksmp.ranks;

import java.util.*;

public enum Rank {
    MEMBER(0, "&7"), ULTIMATE_PLUS(0, "&b", 3), ULTIMATE_PLUS_PLUS(0, "&d", 5), ULTIMATE_PLUS_PLUS_PLUS(0, "&6", 9),
    HELPER(1, "&a"), TRIAL_MOD(2, "&e"), MOD(3, "&2"),
    ADMIN(4, "&c"), DEV(5, "&b"), OWNER(6, "&4");
    public final int level;
    public final String color;
    public final int homes;
    Rank(int level, String color) { this(level,color,0); }
    Rank(int level, String color, int homes) { this.level=level; this.color=color; this.homes=homes; }
    public String label() {
        return switch(this) {
            case ULTIMATE_PLUS -> "Ultimate+";
            case ULTIMATE_PLUS_PLUS -> "Ultimate++";
            case ULTIMATE_PLUS_PLUS_PLUS -> "Ultimate+++";
            default -> name().replace('_',' ');
        };
    }
    public int defaultWeight() { return switch(this) {
        case MEMBER -> 0; case ULTIMATE_PLUS -> 10; case ULTIMATE_PLUS_PLUS -> 20; case ULTIMATE_PLUS_PLUS_PLUS -> 30;
        default -> level*100;
    }; }
    /** Display weights never change staff authority. Ties use the stable enum order. */
    public static Rank display(Collection<Rank> ranks,Map<Rank,Integer> weights) {
        return ranks.stream().max(Comparator.<Rank>comparingInt(r->weights.getOrDefault(r,r.defaultWeight()))
            .thenComparingInt(Enum::ordinal)).orElse(MEMBER);
    }
    public boolean above(Rank other) { return level > other.level; }
    public static Rank parse(String value) {
        String key=value.trim().toUpperCase(Locale.ROOT).replace(' ','_').replace('-','_');
        if(key.startsWith("BLINK"))key="ULTIMATE"+key.substring(5);
        key=switch(key) {
            case "ULTIMATE+" -> "ULTIMATE_PLUS";
            case "ULTIMATE++" -> "ULTIMATE_PLUS_PLUS";
            case "ULTIMATE+++" -> "ULTIMATE_PLUS_PLUS_PLUS";
            case "OWER" -> "OWNER";
            default -> key;
        };
        return valueOf(key);
    }
}

