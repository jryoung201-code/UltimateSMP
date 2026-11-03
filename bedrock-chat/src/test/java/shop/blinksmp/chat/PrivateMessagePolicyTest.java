package shop.blinksmp.chat;

public final class PrivateMessagePolicyTest {
    public static void main(String[] args) {
        int checks=0;
        for(String label:new String[]{"msg","tell","w","minecraft:msg","minecraft:tell","minecraft:w","MSG"}) {
            String input=label+" .Bedrock_User hello there";
            if(!PrivateMessagePolicy.handles(input) || PrivateMessagePolicy.validate(input)==null) throw new AssertionError(input);
            checks++;
        }
        for(String input:new String[]{"msg","msg Alice","msg Alice ","msg @a hi","msg @p hi","msg @e hi","msg Alice /op me","msg Alice \nhello","msg Alice \tmsg Bob hi","msg Alice \u00a7ahey","msg Alice \uD800","msg a/b hi","msg Alice "+"x".repeat(256),"op Alice","say hi","execute run msg Alice hi","other:msg Alice hi","/msg Alice hi"," msg Alice hi"}) {
            if(PrivateMessagePolicy.validate(input)!=null) throw new AssertionError(input);
            checks++;
        }
        for(String target:new String[]{"RealJR_25736",".BedrockUser","Java_User",".Xbox-Name"}) {
            if(PrivateMessagePolicy.validate("msg "+target+" hello /op nobody; this stays message text")==null) throw new AssertionError(target);
            checks++;
        }
        if(!PrivateMessagePolicy.validate("w  Alice   hello  there ").equals("w Alice hello  there")) throw new AssertionError("Spacing");
        String boundary="msg Alice "+"x".repeat(246);
        if(PrivateMessagePolicy.validate(boundary)==null || PrivateMessagePolicy.validate(boundary+"x")!=null) throw new AssertionError("Length");
        System.out.println("PASS: "+(checks+2)+" private-message alias, target, command-isolation and text validation checks");
    }
}
