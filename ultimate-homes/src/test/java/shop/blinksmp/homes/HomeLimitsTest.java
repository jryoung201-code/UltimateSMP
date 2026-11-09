package shop.blinksmp.homes;

import java.util.Map;
public final class HomeLimitsTest {
    private static int checks;
    private static void check(boolean condition,String name) { checks++;if(!condition) throw new AssertionError(name); }
    public static void main(String[] args) {
        check(HomeLimits.resolve(Map.of("maxhomes.1",true),1000)==1,"Default node");
        check(HomeLimits.resolve(Map.of("maxhomes.1",true,"maxhomes.3",true,"maxhomes.10",true),1000)==10,"Highest granted limit");
        check(HomeLimits.resolve(Map.of("maxhomes.1",true,"maxhomes.10",false),1000)==1,"Explicit false not counted");
        check(HomeLimits.resolve(Map.of("MAXHOMES.7",true),1000)==7,"Case-insensitive");
        check(HomeLimits.resolve(Map.of("maxhomes.*",true,"maxhomes.-5",true,"maxhomes.9999999999999999999999",true),1000)==0,"Wildcard, negative, overflow not unlimited");
        check(HomeLimits.resolve(Map.of("maxhomes.9999",true),1000)==1000,"Server cap");
        check(HomeLimits.resolve(Map.of("maxhomes.1",false,"maxhomes.0",true),1000)==0,"Deny default allows zero limit");
        check(HomeLimits.resolve(Map.of("other.maxhomes.9",true,"maxhomes.9.extra",true),1000)==0,"Only exact numeric nodes");
        check(HomeLimits.name("My_Base-2").equals("my_base-2"),"Names normalized");
        for(String name:new String[]{"../test","a.b","hello world","", "x".repeat(25),"a/b","a:b"}) {
            try { HomeLimits.name(name);throw new AssertionError("Invalid name accepted"); } catch(IllegalArgumentException expected) { checks++; }
        }
        System.out.println("PASS: "+checks+" numeric limits, explicit denials, cap and home-name checks");
    }
}
