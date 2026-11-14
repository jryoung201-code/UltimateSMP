package shop.blinksmp.network;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SwitchRequestTest {
    @Test void authorizedIntentRoundTripsWithoutTransmittingSessionSecret()throws Exception{
        UUID token=UUID.randomUUID();var request=new SwitchRequest(UUID.randomUUID(),UUID.randomUUID(),"BlinkSMP-Overworld-02",61_000);
        byte[] bytes=request.bytes(token);assertEquals(request,SwitchRequest.read(bytes,token,1000));assertFalse(new String(bytes,StandardCharsets.ISO_8859_1).contains(token.toString()));
    }
    @Test void wrongSessionAndPayloadTamperingAreRejected()throws Exception{
        UUID token=UUID.randomUUID();byte[] bytes=new SwitchRequest(UUID.randomUUID(),UUID.randomUUID(),"BlinkSMP-Overworld-02",61_000).bytes(token);
        assertThrows(IOException.class,()->SwitchRequest.read(bytes,UUID.randomUUID(),1000));
        for(int i:new int[]{0,20,80,bytes.length-1}){byte[] changed=bytes.clone();changed[i]^=1;assertThrows(IOException.class,()->SwitchRequest.read(changed,token,1000));}
    }
    @Test void expiredDistantAndOversizedRequestsAreRejected()throws Exception{
        UUID token=UUID.randomUUID();var request=new SwitchRequest(UUID.randomUUID(),UUID.randomUUID(),"BlinkSMP-Overworld-02",61_000);
        assertThrows(IOException.class,()->SwitchRequest.read(request.bytes(token),token,61_000));assertThrows(IOException.class,()->SwitchRequest.read(request.bytes(token),token,-100_000));
        assertThrows(IOException.class,()->SwitchRequest.read(new byte[257],token,1000));assertThrows(IllegalArgumentException.class,()->new SwitchRequest(UUID.randomUUID(),UUID.randomUUID(),"admin",1000));
    }
}
