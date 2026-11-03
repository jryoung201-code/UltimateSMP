package shop.blinksmp.announcements;

/** Main-thread state: emits zero exactly once, and cancellation cannot trigger a stop. */
public final class Countdown {
    private int remaining=-1;
    public static int seconds(String value) {
        int parsed;
        try { parsed=Integer.parseInt(value); } catch(NumberFormatException e) { throw new IllegalArgumentException("Seconds must be 1 to 3600."); }
        if(parsed<1 || parsed>3600) throw new IllegalArgumentException("Seconds must be 1 to 3600.");
        return parsed;
    }
    public boolean active() { return remaining>=1; }
    public int remaining() { return remaining; }
    public void start(int seconds) {
        if(seconds<1 || seconds>3600) throw new IllegalArgumentException("Seconds must be 1 to 3600.");
        if(active()) throw new IllegalStateException("A restart is already counting down. Cancel it first.");
        remaining=seconds;
    }
    public int tick() {
        if(!active()) return -1;
        int next=remaining-1;
        remaining=next==0?-1:next;
        return next;
    }
    public boolean cancel() { boolean wasActive=active();remaining=-1;return wasActive; }
}
