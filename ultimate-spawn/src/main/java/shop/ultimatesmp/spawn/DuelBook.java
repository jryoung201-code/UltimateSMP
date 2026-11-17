package shop.ultimatesmp.spawn;
import java.util.*;
/** Main-thread, one-arena consent and match lifecycle. No requests grant combat access. */
final class DuelBook {
 record Request(UUID from,UUID to,long expires){}
 record Match(UUID first,UUID second,long starts,long ends){
  boolean contains(UUID id){return first.equals(id)||second.equals(id);}
  UUID opponent(UUID id){if(!contains(id))throw new IllegalArgumentException();return first.equals(id)?second:first;}
 }
 private final Map<UUID,Request> incoming=new HashMap<>();private Match match;
 Match match(){return match;}
 void expire(long now){incoming.values().removeIf(r->r.expires()<=now);}
 boolean pending(UUID id){return incoming.values().stream().anyMatch(r->r.from().equals(id)||r.to().equals(id));}
 boolean request(UUID from,UUID to,long now,long duration){expire(now);if(from.equals(to)||match!=null||pending(from)||pending(to))return false;incoming.put(to,new Request(from,to,now+duration));return true;}
 Request incoming(UUID to,long now){expire(now);return incoming.get(to);}
 Request cancel(UUID id){for(Request r:List.copyOf(incoming.values()))if(r.from().equals(id)||r.to().equals(id)){incoming.remove(r.to());return r;}return null;}
 Match accept(UUID to,UUID expectedFrom,long now){
  Request r=incoming(to,now);if(match!=null||r==null||!r.from().equals(expectedFrom))return null;
  incoming.remove(to);match=new Match(r.from(),r.to(),Long.MAX_VALUE,Long.MAX_VALUE);return match;
 }
 Match arm(long now,long countdown,long duration){if(match==null||match.starts()!=Long.MAX_VALUE)return null;match=new Match(match.first(),match.second(),now+countdown,now+countdown+duration);return match;}
 Match finish(){Match ended=match;match=null;return ended;}
 static boolean combatAllowed(Match m,UUID attacker,UUID victim,long now){return m!=null&&now>=m.starts()&&now<m.ends()&&!attacker.equals(victim)&&m.contains(attacker)&&m.contains(victim);}
}
