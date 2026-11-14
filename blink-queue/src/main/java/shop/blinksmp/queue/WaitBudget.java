package shop.blinksmp.queue;

/** A monotonic, fixed deadline survives repeated limbo entries during one attempt. */
final class WaitBudget {
    private final long started,limit;
    WaitBudget(long now,long limitNanos){if(limitNanos<=0)throw new IllegalArgumentException();started=now;limit=limitNanos;}
    boolean expired(long now){return now-started>=limit;}
    long secondsLeft(long now){return Math.max(0,(limit-(now-started)+999_999_999L)/1_000_000_000L);}
}
