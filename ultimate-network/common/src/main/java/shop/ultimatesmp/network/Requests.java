package shop.ultimatesmp.network;
import java.util.*;
/** Caller serializes access. No consent survives a network disconnect. */
public final class Requests {
 public record Request(UUID from,UUID to,String type,long expires){public UUID mover(){return type.equals("here")?to:from;}public UUID destination(){return type.equals("here")?from:to;}}
 private final Map<UUID,Request> pending=new HashMap<>();private final Map<UUID,Long> last=new HashMap<>();
 public Request send(UUID from,UUID to,String type,long now){expire(now);if(from.equals(to)||!Set.of("tpa","here","trade").contains(type))throw new IllegalArgumentException("Choose another player");if(now-last.getOrDefault(from,Long.MIN_VALUE/2)<5000)throw new IllegalArgumentException("Wait 5 seconds before requesting again");if(pending.values().stream().filter(r->r.to.equals(to)).count()>=5)throw new IllegalArgumentException("That player has too many requests");last.put(from,now);Request r=new Request(from,to,type,now+30000);pending.put(from,r);return r;}
 public List<Request> incoming(UUID to,String type,long now){expire(now);return pending.values().stream().filter(r->r.to.equals(to)&&r.type.equals("trade")==type.equals("trade")).toList();}
 public Request take(UUID recipient,UUID from,String type,long now){Request r=pending.get(from);if(r==null||!r.to.equals(recipient)||r.type.equals("trade")!=type.equals("trade")||now>=r.expires)throw new IllegalArgumentException("No matching active request");pending.remove(from);return r;}
 public Request cancel(UUID from){return pending.remove(from);}public void leave(UUID id){pending.values().removeIf(r->r.from.equals(id)||r.to.equals(id));last.remove(id);}
 public void expire(long now){pending.values().removeIf(r->now>=r.expires);}public int size(){return pending.size();}
}
