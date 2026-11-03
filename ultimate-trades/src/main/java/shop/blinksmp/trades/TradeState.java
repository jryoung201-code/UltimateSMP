package shop.blinksmp.trades;

/** All state transitions happen on Paper's main thread. */
public final class TradeState {
    private long revision;
    private boolean first,second,closed;
    private long due;
    public long revision() { return revision; }
    public boolean closed() { return closed; }
    public boolean ready(int side) { return side==0?first:second; }
    public boolean both() { return first && second && !closed; }
    public void changed() { if(closed) return;revision++;first=false;second=false;due=0; }
    public boolean confirm(int side,long expected,long now,long delay) {
        if(closed || expected!=revision || (side!=0 && side!=1)) return false;
        if(side==0) first=!first;else second=!second;
        due=both()?now+delay:0;
        return true;
    }
    public boolean canFinish(long now) { return both() && now-due>=0; }
    public boolean close() { if(closed) return false;closed=true;return true; }
}
