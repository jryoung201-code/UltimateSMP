package shop.blinksmp.ranks;

import java.util.Locale;

public enum Rank {
    MEMBER(0, "&7"), BLINK_PLUS(0, "&b", 3), BLINK_PLUS_PLUS(0, "&d", 5), BLINK_PLUS_PLUS_PLUS(0, "&6", 9),
    HELPER(1, "&a"), TRIAL_MOD(2, "&e"), MOD(3, "&2"),
    ADMIN(4, "&c"), DEV(5, "&b"), OWNER(6, "&4");
    public final int level;
    public final String color;
    public final int homes;
    Rank(int level, String color) { this(level,color,0); }
    Rank(int level, String color, int homes) { this.level=level; this.color=color; this.homes=homes; }
    public String label() {
        return switch(this) {
            case BLINK_PLUS -> "Blink+";
            case BLINK_PLUS_PLUS -> "Blink++";
            case BLINK_PLUS_PLUS_PLUS -> "Blink+++";
            default -> name().replace('_',' ');
        };
    }
    public boolean above(Rank other) { return level > other.level; }
    public static Rank parse(String value) {
        String key=value.trim().toUpperCase(Locale.ROOT).replace(' ','_').replace('-','_');
        key=switch(key) {
            case "BLINK+" -> "BLINK_PLUS";
            case "BLINK++" -> "BLINK_PLUS_PLUS";
            case "BLINK+++" -> "BLINK_PLUS_PLUS_PLUS";
            case "OWER" -> "OWNER";
            default -> key;
        };
        return valueOf(key);
    }
}
