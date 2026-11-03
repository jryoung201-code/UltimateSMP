package shop.blinksmp.gui;

import java.util.*;

public final class GuiRules {
    public static final String OWNER_NAME="RealJR_25736";
    public static final UUID OWNER_UUID=UUID.fromString("bd33980d-5b8d-4f1c-becc-0520f92c4a0d");
    private GuiRules() {}
    public static boolean allowed(UUID bound,UUID caller) { return bound!=null && bound.equals(caller); }
    public static boolean command(String raw) {
        if(raw==null || raw.length()>256) return false;
        String root=raw.strip().split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        if(root.startsWith("/")) root=root.substring(1);
        return Set.of("ultimategui","ugui","ultimategui:ultimategui","ultimategui:ugui").contains(root);
    }
    public static boolean hiddenSuggestion(String suggestion,String ownerName) {
        return suggestion!=null && suggestion.toLowerCase(Locale.ROOT).contains(ownerName.toLowerCase(Locale.ROOT));
    }
    public static int visibleCount(int count,boolean hiddenOnline) { return Math.max(0,count-(hiddenOnline?1:0)); }
}
