package shop.blinksmp.tpr;

import java.util.UUID;

public final class RequestBookTest {
    private static int checks;
    private static void check(boolean condition,String message) { checks++;if(!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();RequestBook book=new RequestBook();long now=1000,ttl=100;
        book.send(a,b,false,now,ttl,5);
        check(book.incoming(b,now).size()==1 && book.incoming(a,now).isEmpty(),"Only the recipient has an incoming request");
        check(book.take(c,a,now).isEmpty(),"A third party cannot accept another player's request");
        check(book.take(a,a,now).isEmpty(),"The sender cannot accept their own request");
        RequestBook.Request normal=book.take(b,a,now).orElseThrow();
        check(normal.mover().equals(a) && normal.destinationPlayer().equals(b),"TPR moves sender to recipient");
        check(book.take(b,a,now).isEmpty(),"Acceptance consumes request exactly once");
        book.send(a,b,true,now,ttl,5);RequestBook.Request here=book.take(b,a,now).orElseThrow();
        check(here.mover().equals(b) && here.destinationPlayer().equals(a),"TPRHere moves recipient to sender");
        book.send(a,b,false,now,ttl,5);
        check(book.incoming(b,now+ttl-1).size()==1 && book.take(b,a,now+ttl).isEmpty(),"Expiry boundary is enforced even before cleanup");
        check(book.expire(now+ttl).size()==1 && book.expire(now+ttl).isEmpty(),"Expiry notification once");
        book.send(a,b,false,now,ttl,5);check(book.cancel(c)==null,"Third-party cancel does not remove request");
        check(book.cancel(a)!=null && book.take(b,a,now).isEmpty(),"Sender cancellation prevents acceptance");
        book.send(a,b,false,now,ttl,5);book.send(a,c,true,now+1,ttl,5);
        check(book.incoming(b,now+1).isEmpty() && book.incoming(c,now+1).size()==1,"Replacing request invalidates old recipient");
        book.removeParticipant(c);check(book.take(c,a,now+2).isEmpty(),"Recipient disconnect cleanup");
        book.send(a,b,false,now,ttl,5);book.send(c,b,true,now,ttl,5);
        check(book.incoming(b,now).size()==2,"Multiple incoming requests stay separate");
        book.take(b,a,now);check(book.incoming(b,now).size()==1 && book.incoming(b,now).get(0).from().equals(c),"Accepting a named request leaves the other request alone");
        book.removeParticipant(c);check(book.incoming(b,now).isEmpty(),"Sender disconnect cleanup");
        book.send(a,b,false,now,ttl,1);
        try { book.send(c,b,true,now,ttl,1);throw new AssertionError("Incoming limit ignored"); } catch(IllegalArgumentException expected) { checks++; }
        check(book.incoming(b,now).size()==1,"Failed request preserves existing request");
        book.send(a,b,true,now,ttl,1);check(book.incoming(b,now).get(0).here(),"Sender can replace its own request at capacity");
        try { book.send(a,a,false,now,ttl,5);throw new AssertionError("Self request allowed"); } catch(IllegalArgumentException expected) { checks++; }
        book.clear();check(book.incoming(b,now).isEmpty(),"Shutdown cleanup");
        System.out.println("PASS: "+checks+" teleport direction, consent, expiry, cancellation and request-limit checks");
    }
}
