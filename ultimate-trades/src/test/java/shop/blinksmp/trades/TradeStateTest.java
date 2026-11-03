package shop.blinksmp.trades;

public final class TradeStateTest {
    private static int checks;
    private static void check(boolean value,String name) { checks++;if(!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        TradeState state=new TradeState();long delay=3_000_000_000L,now=100;
        check(!state.both() && !state.canFinish(Long.MAX_VALUE),"Empty trade cannot finish");
        long revision=state.revision();
        check(state.confirm(0,revision,now,delay),"First confirmation");
        check(!state.canFinish(now+delay),"One participant cannot commit");
        state.confirm(1,revision,now,delay);
        check(state.both() && !state.canFinish(now+delay-1) && state.canFinish(now+delay),"Safety delay boundary");
        state.changed();
        check(!state.both() && !state.ready(0) && !state.ready(1),"Change revokes BOTH confirmations");
        check(!state.confirm(1,revision,now,delay),"Stale click cannot accept a changed offer");
        revision=state.revision();state.confirm(0,revision,now,delay);state.confirm(1,revision,now,delay);
        state.confirm(0,revision,now+1,delay);
        check(!state.both() && !state.canFinish(now+delay*2),"Unconfirm cancels pending commit");
        state.confirm(0,revision,now+delay,delay);
        check(!state.canFinish(now+delay*2-1) && state.canFinish(now+delay*2),"Reconfirm restarts delay");
        check(!state.confirm(-1,revision,now,delay),"Invalid participant");
        check(state.close() && !state.close(),"Close/commit is one shot");
        check(!state.canFinish(Long.MAX_VALUE) && !state.confirm(0,revision,now,delay),"Closed sessions cannot commit again");
        state.changed();check(state.closed(),"Changes cannot reopen cancelled trades");
        System.out.println("PASS: "+checks+" confirmation, revision, delay and one-shot close checks");
    }
}
