package shop.blinksmp.chat;

import java.util.Locale;
import java.util.Set;

/** Only private-message commands with one literal player target can enter the relay. */
public final class PrivateMessagePolicy {
    private static final Set<String> LABELS=Set.of("msg","tell","w","minecraft:msg","minecraft:tell","minecraft:w");
    private PrivateMessagePolicy() {}
    public static boolean handles(String input) {
        if(input==null) return false;
        int end=input.indexOf(' ');
        return LABELS.contains((end<0?input:input.substring(0,end)).toLowerCase(Locale.ROOT));
    }
    public static String validate(String input) {
        if(!handles(input) || input.length()>256) return null;
        int split=input.indexOf(' ');
        if(split<0) return null;
        String args=input.substring(split+1).strip();
        int separator=args.indexOf(' ');
        if(separator<0) return null;
        String target=args.substring(0,separator);
        // Floodgate's usual dot prefix and underscores are valid. Selectors/command syntax are not.
        if(!target.matches("[A-Za-z0-9_.-]{1,64}")) return null;
        String message=ChatPolicy.validate(args.substring(separator+1));
        if(message==null) return null;
        return input.substring(0,split).toLowerCase(Locale.ROOT)+" "+target+" "+message;
    }
}
