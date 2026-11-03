package shop.blinksmp.chat;

/** Validation happens before forwarding to Player.chat; chat can never become a command. */
public final class ChatPolicy {
    private ChatPolicy() {}
    public static String validate(String input) {
        if(input==null || input.length()>256) return null;
        for(int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if(c<32 || c==127 || c=='\u00a7') return null;
            if(Character.isHighSurrogate(c)) {
                if(i+1>=input.length() || !Character.isLowSurrogate(input.charAt(++i))) return null;
            } else if(Character.isLowSurrogate(c)) return null;
        }
        String text=input.strip();
        return text.isEmpty() || text.startsWith("/") ? null : text;
    }
    public static final class Gate {
        private final long interval;
        private long last;
        private boolean used;
        public Gate(long interval) { if(interval<1) throw new IllegalArgumentException("interval must be positive"); this.interval=interval; }
        public synchronized boolean allow(long now) {
            if(used && now-last<interval) return false;
            last=now; used=true; return true;
        }
    }
}
