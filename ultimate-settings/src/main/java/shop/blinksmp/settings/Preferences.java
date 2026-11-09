package shop.blinksmp.settings;

public record Preferences(boolean booster, boolean rankTags, boolean players,
                          boolean messages, boolean joinMessages, boolean coordinates, boolean excludeMonsters) {
    public Preferences(boolean booster,boolean rankTags,boolean players,boolean messages,boolean joinMessages,boolean coordinates) {
        this(booster,rankTags,players,messages,joinMessages,coordinates,false);
    }
    public static final Preferences DEFAULT = new Preferences(false,true,true,true,true,false,false);
    public Preferences toggle(String key) {
        return switch(key) {
            case "booster" -> new Preferences(!booster,rankTags,players,messages,joinMessages,coordinates,excludeMonsters);
            case "rank-tags" -> new Preferences(booster,!rankTags,players,messages,joinMessages,coordinates,excludeMonsters);
            case "players" -> new Preferences(booster,rankTags,!players,messages,joinMessages,coordinates,excludeMonsters);
            case "messages" -> new Preferences(booster,rankTags,players,!messages,joinMessages,coordinates,excludeMonsters);
            case "join-messages" -> new Preferences(booster,rankTags,players,messages,!joinMessages,coordinates,excludeMonsters);
            case "coordinates" -> new Preferences(booster,rankTags,players,messages,joinMessages,!coordinates,excludeMonsters);
            case "exclude-monsters" -> new Preferences(booster,rankTags,players,messages,joinMessages,coordinates,!excludeMonsters);
            default -> throw new IllegalArgumentException("Unknown setting: "+key);
        };
    }
    public boolean value(String key) {
        return switch(key) {
            case "booster"->booster; case "rank-tags"->rankTags; case "players"->players;
            case "messages"->messages; case "join-messages"->joinMessages; case "coordinates"->coordinates; case "exclude-monsters"->excludeMonsters;
            default->throw new IllegalArgumentException("Unknown setting: "+key);
        };
    }
    public static int chunkCap(int configured,int world,int previous) {
        return Math.max(2,Math.min(Math.min(configured,world),previous));
    }
}
