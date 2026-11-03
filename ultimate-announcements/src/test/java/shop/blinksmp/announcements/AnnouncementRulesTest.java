package shop.blinksmp.announcements;

import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public final class AnnouncementRulesTest {
    private static int checks;
    private static void check(boolean good,String label) { checks++;if(!good) throw new AssertionError(label); }
    public static void main(String[] args) {
        Countdown timer=new Countdown();
        check(!timer.active() && timer.tick()==-1,"Idle countdown never stops");
        for(String invalid:new String[]{"0","-1","3601","2147483648","abc","1.5",""}) {
            try { Countdown.seconds(invalid);throw new AssertionError(invalid); } catch(IllegalArgumentException expected) { checks++; }
        }
        check(Countdown.seconds("10")==10 && Countdown.seconds("3600")==3600,"Bounds");
        timer.start(10);
        List<Integer> seen=new ArrayList<>();seen.add(timer.remaining());
        while(timer.active()) seen.add(timer.tick());
        check(seen.equals(List.of(10,9,8,7,6,5,4,3,2,1,0)),"Complete countdown without gaps");
        for(int i=0;i<10;i++) check(timer.tick()==-1,"Zero can trigger shutdown only once");
        timer.start(10);timer.tick();
        check(timer.cancel(),"Active cancellation");
        for(int i=0;i<20;i++) check(timer.tick()==-1,"Cancelled countdown can never trigger shutdown");
        check(!timer.cancel(),"Idle cancel");timer.start(1);
        try { timer.start(20);throw new AssertionError("Overlapping countdown accepted"); } catch(IllegalStateException expected) { checks++; }
        check(timer.remaining()==1 && timer.tick()==0 && !timer.active(),"Overlap preserved initial timer");
        Rotation rotation=new Rotation();Random random=new Random(25736);
        check(rotation.next(0,true,random)==-1,"Empty list");
        for(int i=0;i<12;i++) check(rotation.next(3,false,random)==i%3,"Ordered wraparound");
        rotation.reset();check(rotation.next(3,false,random)==0,"Reload resets rotation");
        int last=-1;
        for(int i=0;i<1000;i++) {
            int pick=rotation.next(3,true,random);
            check(pick>=0 && pick<3 && pick!=last,"Random stays in bounds without consecutive repeats");last=pick;
        }
        for(int i=0;i<5;i++) check(rotation.next(1,true,random)==0,"One message works");
        check(rotation.next(2,false,random)<2,"Resized list stays in bounds");
        System.out.println("PASS: "+checks+" countdown, cancellation, duplicate-stop and rotation checks");
    }
}
