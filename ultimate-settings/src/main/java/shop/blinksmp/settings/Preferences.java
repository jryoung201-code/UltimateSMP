package shop.blinksmp.settings;

public record Preferences(boolean booster, boolean rankTags, boolean players,
                          boolean messages, boolean joinMessages, boolean coordinates) {
    public static final Preferences DEFAULT = new Preferences(false,true,true,true,true,false);
    public Preferences toggle(String key) {
        return switch(key) {
            case "booster" -> new Preferences(!booster,rankTags,players,messages,joinMessages,coordinates);
            case "rank-tags" -> new Preferences(booster,!rankTags,players,messages,joinMessages,coordinates);
            case "players" -> new Preferences(booster,rankTags,!players,messages,joinMessages,coordinates);
            case "messages" -> new Preferences(booster,rankTags,players,!messages,joinMessages,coordinates);
            case "join-messages" -> new Preferences(booster,rankTags,players,messages,!joinMessages,coordinates);
            case "coordinates" -> new Preferences(booster,rankTags,players,messages,joinMessages,!coordinates);
            default -> throw new IllegalArgumentException("Unknown setting: "+key);
        };
    }
    public boolean value(String key) {
        return switch(key) {
            case "booster"->booster; case "rank-tags"->rankTags; case "players"->players;
            case "messages"->messages; case "join-messages"->joinMessages; case "coordinates"->coordinates;
            default->throw new IllegalArgumentException("Unknown setting: "+key);
        };
    }
    public static int chunkCap(int configured,int world,int previous) {
        return Math.max(2,Math.min(Math.min(configured,world),previous));
    }
}
