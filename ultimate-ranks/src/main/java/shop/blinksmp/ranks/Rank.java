package shop.blinksmp.ranks;

import java.util.Locale;

public enum Rank {
    MEMBER(0, "&7"), HELPER(1, "&a"), TRIAL_MOD(2, "&e"), MOD(3, "&2"),
    ADMIN(4, "&c"), DEV(5, "&b"), OWNER(6, "&4");
    public final int level;
    public final String color;
    Rank(int level, String color) { this.level=level; this.color=color; }
    public String label() { return name().replace('_',' '); }
    public boolean above(Rank other) { return level > other.level; }
    public static Rank parse(String value) {
        String key=value.trim().toUpperCase(Locale.ROOT).replace(' ','_').replace('-','_');
        return valueOf(key.equals("OWER") ? "OWNER" : key);
    }
}
