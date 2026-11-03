package shop.blinksmp.ultimateoptimize;

/** Dependency-free behavioral regression checks, run with Java 17 or later. */
public final class PolicyTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static PressurePolicy policy() { return new PressurePolicy(80,65,45,35,3,12); }
    public static void main(String[] args) {
        PressurePolicy p = policy();
        check(!p.sample(90,20), "Single memory spike must not reduce distance");
        check(!p.sample(50,20), "Healthy sample resets pressure sequence");
        check(!p.sample(80,20), "First pressure sample");
        check(!p.sample(80,20), "Second pressure sample");
        check(p.sample(80,20), "Third pressure sample enters pressure mode");
        for (int i=0;i<30;i++) check(p.sample(70,30), "Dead band must hold pressure mode");
        for (int i=0;i<11;i++) check(p.sample(60,30), "Recovery must wait for 12 samples");
        check(p.sample(60,40), "Both heap and tick time must be healthy to recover");
        for (int i=0;i<11;i++) check(p.sample(65,35), "Interrupted recovery must restart");
        check(!p.sample(65,35), "Twelfth healthy sample must recover");
        p = policy();
        p.sample(40,50); p.sample(40,50);
        check(p.sample(40,50), "Tick pressure alone must enter pressure mode");
        check(p.sample(Double.NaN,20), "Invalid telemetry must not change mode");
        p.reset();
        check(!p.pressured(), "Resuming must clear stale pressure state");
        for (int current=2;current<=32;current++) for (int target=2;target<=32;target++) {
            int next=PressurePolicy.step(current,target);
            check(Math.abs(next-current)<=1, "Change must be at most one chunk");
            check(next>=2 && next<=32, "Distance must stay in API range");
            check(Math.abs(next-target)<=Math.abs(current-target), "Change must approach target");
        }
        boolean rejected=false;
        try { new PressurePolicy(65,80,45,35,3,12); }
        catch (IllegalArgumentException e) { rejected=true; }
        check(rejected, "Inverted thresholds must be rejected");
        System.out.println("PASS: " + checks + " behavioral checks");
    }
}
