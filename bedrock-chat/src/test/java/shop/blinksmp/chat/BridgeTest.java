package shop.blinksmp.chat;

import java.io.IOException;
import java.util.*;
import shop.blinksmp.chat.bridge.*;

public final class BridgeTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Bridge check "+checks);}
    private static void invalid(byte[] bytes){try{ChatFrame.decode(bytes);throw new AssertionError("Malformed frame accepted");}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        UUID player=UUID.randomUUID(),message=UUID.randomUUID();
        ChatFrame frame=new ChatFrame(message,player,"{\"text\":\"Hello Java and Bedrock 👋\"}");
        byte[] bytes=frame.encode();check(frame.equals(ChatFrame.decode(bytes)));
        for(int length=0;length<bytes.length;length++)invalid(Arrays.copyOf(bytes,length));
        invalid(Arrays.copyOf(bytes,bytes.length+1));invalid(new byte[ChatFrame.MAX_BYTES+1]);
        byte[] version=bytes.clone();version[3]=2;invalid(version);
        try{new ChatFrame(message,player,"x".repeat(ChatFrame.MAX_JSON+1)).encode();throw new AssertionError();}catch(IOException expected){checks++;}
        try{new ChatFrame(message,player," ").encode();throw new AssertionError();}catch(IOException expected){checks++;}
        check(RelayGuard.authentic(player,player,"world1","world1"));
        check(!RelayGuard.authentic(player,UUID.randomUUID(),"world1","world1"));
        check(!RelayGuard.authentic(player,player,"old-server","new-server"));
        check(!RelayGuard.authentic(player,player,"world1",null));
        RelayGuard guard=new RelayGuard();
        check(guard.admit(message,player,1));check(!guard.admit(message,player,500_000_001L));
        check(!guard.admit(UUID.randomUUID(),player,2));
        check(guard.admit(UUID.randomUUID(),player,250_000_001L));
        check(guard.admit(UUID.randomUUID(),UUID.randomUUID(),250_000_001L));
        guard.disconnect(player);check(guard.admit(UUID.randomUUID(),player,250_000_002L));
        check(guard.admit(message,player,61_000_000_002L));
        for(String bad:List.of("/op Bob","\n/stop","hello\u0000","§cspoof"))check(ChatPolicy.validate(bad)==null);
        check(ChatPolicy.validate("hello <click:run_command:/op x>")!=null); // Plain text, never MiniMessage or commands.
        System.out.println("PASS: "+checks+" bridge checks: truncation, size, version, identity, transfer races, duplicate and spam rejection");
    }
}
