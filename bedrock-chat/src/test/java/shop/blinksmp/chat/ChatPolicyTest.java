package shop.blinksmp.chat;

public final class ChatPolicyTest {
    private static int checks;
    private static void check(boolean condition,String message) { checks++; if(!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        check("hello".equals(ChatPolicy.validate(" hello ")),"Normal text trimming");
        check("Hello 🍬".equals(ChatPolicy.validate("Hello 🍬")),"Valid surrogate pairs");
        check(ChatPolicy.validate("x".repeat(256))!=null,"Maximum chat length");
        for(String invalid:new String[]{"/op me","  /say hi","\u2003/op name", "", "   ","\n/op name","\rhi","hi\u0000","hi\u00a7a", "\ud800", "\udc00", "x".repeat(257)})
            check(ChatPolicy.validate(invalid)==null,"Invalid/command text accepted");
        check(ChatPolicy.validate(null)==null,"Null text");
        for(char c=0;c<32;c++) check(ChatPolicy.validate("before"+c+"after")==null,"Control character accepted");
        ChatPolicy.Gate first=new ChatPolicy.Gate(750),second=new ChatPolicy.Gate(750);
        check(first.allow(0),"First message");
        check(!first.allow(1),"Burst rate limit");
        check(!first.allow(749),"Boundary before refill");
        check(first.allow(750),"Exact interval");
        check(second.allow(1),"Independent session quota");
        check(!first.allow(751),"Last accepted time retained");
        check(first.allow(1500),"Later message");
        System.out.println("PASS: "+checks+" validation, command rejection and rate-limit checks");
    }
}
