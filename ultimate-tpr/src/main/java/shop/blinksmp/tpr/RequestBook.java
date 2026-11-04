package shop.blinksmp.tpr;

import java.util.*;

/** UUID-based authorization and expiry. Accessed only on the main thread. */
public final class RequestBook {
    public record Request(UUID from,UUID to,boolean here,long expires) {
        public UUID mover() { return here?to:from; }
        public UUID destinationPlayer() { return here?from:to; }
    }
    private final Map<UUID,Request> outgoing=new HashMap<>();
    public Request send(UUID from,UUID to,boolean here,long now,long duration,int incomingLimit) {
        Objects.requireNonNull(from);Objects.requireNonNull(to);
        if(from.equals(to)) throw new IllegalArgumentException("You cannot send a teleport request to yourself.");
        if(duration<=0 || incomingLimit<1) throw new IllegalArgumentException("Invalid request limits.");
        long count=outgoing.values().stream().filter(r->r.to().equals(to) && !r.from().equals(from) && now-r.expires()<0).count();
        if(count>=incomingLimit) throw new IllegalArgumentException("That player already has too many pending requests. Try again later.");
        return outgoing.put(from,new Request(from,to,here,now+duration));
    }
    public List<Request> incoming(UUID to,long now) {
        return outgoing.values().stream().filter(r->r.to().equals(to) && now-r.expires()<0).toList();
    }
    public Optional<Request> take(UUID to,UUID from,long now) {
        Request request=outgoing.get(from);
        if(request==null || !request.to().equals(to) || now-request.expires()>=0) return Optional.empty();
        outgoing.remove(from);return Optional.of(request);
    }
    public Request cancel(UUID from) { return outgoing.remove(from); }
    public List<Request> expire(long now) {
        List<Request> expired=new ArrayList<>();outgoing.values().removeIf(r-> { if(now-r.expires()<0) return false;expired.add(r);return true; });return expired;
    }
    public void removeParticipant(UUID id) { outgoing.values().removeIf(r->r.from().equals(id)||r.to().equals(id)); }
    public void clear() { outgoing.clear(); }
}
