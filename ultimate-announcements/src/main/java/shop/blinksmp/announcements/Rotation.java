package shop.blinksmp.announcements;

import java.util.random.RandomGenerator;

/** Rotation cursor: ordered wraparound or random picks without consecutive repeats. */
public final class Rotation {
    private int last=-1;
    public void reset() { last=-1; }
    public int next(int size,boolean random,RandomGenerator generator) {
        if(size<1) return -1;
        if(size==1) return last=0;
        if(last>=size) last=-1;
        if(!random) return last=(last+1)%size;
        int pick=generator.nextInt(last<0?size:size-1);
        if(last>=0 && pick>=last) pick++;
        return last=pick;
    }
}
