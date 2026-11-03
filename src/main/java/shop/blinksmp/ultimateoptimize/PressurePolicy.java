package shop.blinksmp.ultimateoptimize;

/** Consecutive samples and separate recovery thresholds prevent distance flapping. */
public final class PressurePolicy {
    private final double highHeap, lowHeap, highTick, lowTick;
    private final int enterSamples, recoverSamples;
    private int bad, good;
    private boolean pressured;

    public PressurePolicy(double highHeap, double lowHeap, double highTick, double lowTick,
                          int enterSamples, int recoverSamples) {
        if (!(lowHeap > 0 && lowHeap < highHeap && highHeap <= 100
                && lowTick > 0 && lowTick < highTick && enterSamples > 0 && recoverSamples > 0)) {
            throw new IllegalArgumentException("Invalid pressure/recovery thresholds");
        }
        this.highHeap = highHeap; this.lowHeap = lowHeap;
        this.highTick = highTick; this.lowTick = lowTick;
        this.enterSamples = enterSamples; this.recoverSamples = recoverSamples;
    }

    public boolean sample(double heapPercent, double mspt) {
        if (!Double.isFinite(heapPercent) || !Double.isFinite(mspt) || heapPercent < 0 || mspt < 0) {
            bad = good = 0;
            return pressured;
        }
        if (!pressured) {
            good = 0;
            bad = heapPercent >= highHeap || mspt >= highTick ? bad + 1 : 0;
            if (bad >= enterSamples) { pressured = true; bad = 0; }
        } else {
            bad = 0;
            good = heapPercent <= lowHeap && mspt <= lowTick ? good + 1 : 0;
            if (good >= recoverSamples) { pressured = false; good = 0; }
        }
        return pressured;
    }

    public boolean pressured() { return pressured; }

    public void reset() { bad = good = 0; pressured = false; }

    public static int step(int current, int target) {
        return current + Integer.compare(target, current);
    }
}
