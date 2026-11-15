package shop.ultimatesmp.network.velocity;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.util.*;import java.time.Duration;import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;import shop.ultimatesmp.network.*;
@Plugin(id="ultimatenetwork",name="UltimateNetwork",version="1.0.0",authors={"Aven"})
public final class UltimateNetworkProxy {
 private static final MinecraftChannelIdentifier CHANNEL=MinecraftChannelIdentifier.from(Wire.CHANNEL);
 private final Map<UUID,Set<UUID>> remoteTabs=new HashMap<>();
 private RenderMirror render;private long nextMirror;
 private final ProxyServer proxy;private final Logger log;
 private record State(String server,boolean hidden,boolean messages,boolean joins,boolean players,String authority,boolean ready,boolean trade,boolean muted,boolean homeAdmin,boolean tags,String prefix,int weight,long seen){}
 private record Operation(UUID id,UUID actor,UUID mover,UUID target,String moverServer,String targetServer,String after,long expires){}
 private final Map<UUID,State> states=new HashMap<>();private final Set<UUID> announced=new HashSet<>();
 private final Map<UUID,Operation> operations=new HashMap<>();private final Map<UUID,UUID> replies=new HashMap<>();
 private final Map<UUID,Long> lastCommand=new HashMap<>();private final Map<UUID,String> carryVanish=new HashMap<>();
 private final Set<UUID> moved=new HashSet<>();
 private final Requests requests=new Requests();private final Set<String> seenFrames=new LinkedHashSet<>();
 @Inject public UltimateNetworkProxy(ProxyServer proxy,Logger log){this.proxy=proxy;this.log=log;}
 @Subscribe public void start(ProxyInitializeEvent e){proxy.getChannelRegistrar().register(CHANNEL);render=new RenderMirror(log);proxy.getScheduler().buildTask(this,this::tick).repeat(1,TimeUnit.SECONDS).schedule();log.info("UltimateNetwork ready: network presence, requests, safe teleports, private chat and staff routing.");}
 private static long now(){return System.currentTimeMillis();}
 private String server(Player p){return p.getCurrentServer().map(s->s.getServerInfo().getName()).orElse("");}
 private Player player(UUID id){return proxy.getPlayer(id).orElse(null);}
 private boolean fresh(State s){return s!=null&&s.ready&&now()-s.seen<12000;}
 private boolean available(Player p){State s=p==null?null:states.get(p.getUniqueId());return p!=null&&fresh(s)&&s.server.equals(server(p));}
 private boolean visible(Player p){return available(p)&&!states.get(p.getUniqueId()).hidden;}
 private void tell(UUID id,String message){Player p=player(id);if(p!=null)p.sendMessage(Component.text("[UltimateSMP] ",NamedTextColor.GOLD).append(Component.text(message,NamedTextColor.YELLOW)));}
 private boolean send(Player p,Wire f){return p!=null&&p.getCurrentServer().map(s->s.sendPluginMessage(CHANNEL,f.encode())).orElse(false);}
 private Player find(String name){return proxy.getAllPlayers().stream().filter(p->p.getUsername().equalsIgnoreCase(name)||p.getUniqueId().toString().equalsIgnoreCase(name)).filter(this::visible).findFirst().orElseThrow(()->new IllegalArgumentException("Player not found or their server is unavailable"));}
 private void notice(Player subject,boolean join){Component c=Component.text(subject.getUsername()+(join?" joined UltimateSMP":" left UltimateSMP"),join?NamedTextColor.GREEN:NamedTextColor.GRAY);for(Player p:proxy.getAllPlayers()){State s=states.get(p.getUniqueId());if(fresh(s)&&s.joins&&s.players)p.sendMessage(c);}}
 @Subscribe public synchronized void connected(ServerConnectedEvent e){State old=states.get(e.getPlayer().getUniqueId());if(old!=null&&old.hidden)carryVanish.put(e.getPlayer().getUniqueId(),server(e.getPlayer()));send(e.getPlayer(),Wire.of("HELLO",e.getPlayer().getUniqueId()));rosters();}
 @Subscribe public synchronized void message(PluginMessageEvent e){
  if(!CHANNEL.equals(e.getIdentifier()))return;e.setResult(PluginMessageEvent.ForwardResult.handled());
  if(!(e.getSource() instanceof ServerConnection source)||!(e.getTarget() instanceof Player carrier))return;
  try{Wire f=Wire.decode(e.getData());if(source.getPlayer()!=carrier||!Wire.authentic(f.actor(),carrier.getUniqueId(),source.getServerInfo().getName(),server(carrier)))return;
   if(!seenFrames.add(f.kind()+":"+f.actor()+":"+f.id()))return;if(seenFrames.size()>4096)seenFrames.remove(seenFrames.iterator().next());
   switch(f.kind()){
    case "STATE" -> state(carrier,f);
    case "COMMAND" -> {if(!available(carrier))throw new IllegalArgumentException("Network data is still loading; try again shortly");long n=now();if(n-lastCommand.getOrDefault(f.actor(),0L)<200)throw new IllegalArgumentException("Please slow down");lastCommand.put(f.actor(),n);command(carrier,f);}
    case "LOCATION" -> location(carrier,f);
    case "FAIL" -> {Operation op=operations.get(f.id());if(op!=null&&(f.actor().equals(op.mover)||f.actor().equals(op.target))){operations.remove(op.id);moved.remove(op.id);tell(op.actor,f.at(0));}}
    case "MOVED" -> {Operation op=operations.get(f.id());if(op!=null&&f.actor().equals(op.mover)){if(op.after.isEmpty())operations.remove(op.id);else moved.add(op.id);tell(op.actor,"Teleport started through the protected inventory handoff.");}}
    case "STAFF_REPLY" -> {UUID to=UUID.fromString(f.at(0));tell(to,f.at(1));}
    case "NOTICE" -> {if(f.args().size()<3||f.args().size()>18||!Set.of("CHAT","TITLE","ACTIONBAR","BOSSBAR").contains(f.at(0))||Integer.parseInt(f.at(1))<1||Integer.parseInt(f.at(1))>60)throw new IllegalArgumentException("Invalid notice");for(Player p:proxy.getAllPlayers())send(p,new Wire(UUID.randomUUID(),"NOTICE",p.getUniqueId(),f.args()));}
    case "ANNOUNCE" -> {for(Player p:proxy.getAllPlayers())if(!server(p).equals(server(carrier)))p.sendMessage(Component.text(f.at(0),NamedTextColor.GOLD));}
   }
  }catch(IllegalArgumentException ex){tell(carrier.getUniqueId(),ex.getMessage());}catch(Exception ex){log.warn("Rejected network bridge frame ({})",ex.getClass().getSimpleName());}
 }
 private void state(Player p,Wire f){
  boolean hidden=Boolean.parseBoolean(f.at(0)),ready=Boolean.parseBoolean(f.at(5));String carry=carryVanish.get(p.getUniqueId());
  if(carry!=null){if(!hidden){send(p,Wire.of("VANISH",p.getUniqueId(),"true"));hidden=true;}else carryVanish.remove(p.getUniqueId());}
  State next=new State(server(p),hidden,Boolean.parseBoolean(f.at(1)),Boolean.parseBoolean(f.at(2)),Boolean.parseBoolean(f.at(3)),f.at(4),ready,Boolean.parseBoolean(f.at(6)),Boolean.parseBoolean(f.at(7)),Boolean.parseBoolean(f.at(8)),Boolean.parseBoolean(f.at(9)),f.at(10),Integer.parseInt(f.at(11)),now());
  State old=states.put(p.getUniqueId(),next);
  if(ready&&announced.add(p.getUniqueId())&&!hidden)notice(p,true);
  if(old==null||!same(old,next))rosters();
 }
 private static boolean same(State a,State b){return a.server.equals(b.server)&&a.hidden==b.hidden&&a.messages==b.messages&&a.joins==b.joins&&a.players==b.players&&a.ready==b.ready&&a.authority.equals(b.authority)&&a.trade==b.trade&&a.muted==b.muted&&a.homeAdmin==b.homeAdmin&&a.tags==b.tags&&a.prefix.equals(b.prefix)&&a.weight==b.weight;}
 private void rosters(){tabs();List<String> entries=proxy.getAllPlayers().stream().filter(this::visible).limit(256).map(p->p.getUniqueId()+"|"+p.getUsername()+"|"+server(p)).toList();for(Player p:proxy.getAllPlayers())send(p,new Wire(UUID.randomUUID(),"ROSTER",p.getUniqueId(),entries));}
 private void tabs(){
  for(Player viewer:proxy.getAllPlayers()){
   var tab=viewer.getTabList();if(tab==null)continue;State v=states.get(viewer.getUniqueId());if(v==null)continue;
   Set<UUID> prior=remoteTabs.computeIfAbsent(viewer.getUniqueId(),id->new HashSet<>()),next=new HashSet<>();
   for(Player subject:proxy.getAllPlayers()){
    State s=states.get(subject.getUniqueId());if(!visible(subject)||!v.players)continue;
    if(server(viewer).equals(server(subject)))continue;UUID id=subject.getUniqueId();next.add(id);
    Component display=v.tags?net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(s.prefix+subject.getUsername()):Component.text(subject.getUsername());
    var entry=tab.getEntry(id).orElse(null);
    if(entry==null)tab.addEntry(com.velocitypowered.api.proxy.player.TabListEntry.builder().tabList(tab).profile(subject.getGameProfile()).displayName(display).latency((int)Math.min(Integer.MAX_VALUE,subject.getPing())).listOrder(s.weight).build());
    else entry.setDisplayName(display).setListOrder(s.weight);
   }
   for(UUID id:new HashSet<>(prior))if(!next.contains(id)){Player p=player(id);if(p==null||!server(viewer).equals(server(p)))tab.removeEntry(id);}
   prior.clear();prior.addAll(next);
  }
 }
 private void command(Player p,Wire f){List<String> a=f.args();String c=Commands.canonical(f.at(0));if(!Commands.routed(c))throw new IllegalArgumentException("Unsupported network command");String arg=a.size()>1?a.get(1):"";UUID id=p.getUniqueId();State s=states.get(id);
  if(c.equals("msg")||c.equals("reply")){if(s.muted)throw new IllegalArgumentException("You are muted");Player to;String text;if(c.equals("reply")){UUID other=replies.get(id);to=other==null?null:player(other);if(!visible(to))throw new IllegalArgumentException("No available reply target");text=String.join(" ",a.subList(1,a.size()));}else{to=find(arg);text=String.join(" ",a.subList(2,a.size()));}if(text.isBlank()||text.length()>500)throw new IllegalArgumentException("Use /msg <player> <message> (up to 500 characters)");if(!s.messages||!states.get(to.getUniqueId()).messages)throw new IllegalArgumentException("Direct messages are disabled");if(id.equals(to.getUniqueId()))throw new IllegalArgumentException("Choose another player");tell(id,"To "+to.getUsername()+": "+text);tell(to.getUniqueId(),"From "+p.getUsername()+": "+text);replies.put(id,to.getUniqueId());replies.put(to.getUniqueId(),id);return;}
  if(c.equals("staffchat")){if(Commands.level(s.authority)<2)throw new IllegalArgumentException("Staff only");String text=String.join(" ",a.subList(1,a.size()));if(text.isBlank()||text.length()>500)throw new IllegalArgumentException("Use /sc <message>");for(Player to:proxy.getAllPlayers()){State st=states.get(to.getUniqueId());if(fresh(st)&&Commands.level(st.authority)>=2)tell(to.getUniqueId(),"[Staff] "+p.getUsername()+": "+text);}return;}
  if(c.equals("tprcancel")||c.equals("trade")&&arg.equalsIgnoreCase("cancel")){requests.cancel(id);for(Operation op:new ArrayList<>(operations.values()))if(op.actor.equals(id)||op.mover.equals(id)||op.target.equals(id)){send(player(op.mover),new Wire(op.id,"CANCEL",op.mover,List.of()));operations.remove(op.id);tell(op.actor,"Teleport cancelled.");}tell(id,"Your network request was cancelled.");return;}
  boolean trade=c.equals("trade");
  if(c.equals("tpraccept")||c.equals("tprdeny")||trade&&(arg.equalsIgnoreCase("accept")||arg.equalsIgnoreCase("deny"))){String name=trade?(a.size()>2?a.get(2):""):arg;List<Requests.Request> incoming=requests.incoming(id,trade?"trade":"tpa",now());if(!name.isBlank()){Player from=find(name);incoming=incoming.stream().filter(r->r.from().equals(from.getUniqueId())).toList();}if(incoming.size()!=1)throw new IllegalArgumentException(incoming.isEmpty()?"No matching active request":"Choose a player by name");Requests.Request r=requests.take(id,incoming.get(0).from(),trade?"trade":"tpa",now());if(c.equals("tprdeny")||arg.equalsIgnoreCase("deny")&&trade){tell(id,"Request declined.");tell(r.from(),p.getUsername()+" declined your request.");return;}Player mover=player(r.mover()),dest=player(r.destination());if(!visible(mover)||!visible(dest))throw new IllegalArgumentException("Both players must be available");if(trade&&(!s.trade||!states.get(r.from()).trade))throw new IllegalArgumentException("Trading is unavailable for one of you");start(id,mover,dest,trade?"trade":"",true);return;}
  if(c.equals("tpr")||c.equals("tprhere")||trade){Player to=find(arg);if(s.hidden||states.get(to.getUniqueId()).hidden)throw new IllegalArgumentException("Disable vanish before requesting");if(trade&&(!s.trade||!states.get(to.getUniqueId()).trade))throw new IllegalArgumentException("Trading is unavailable for one of you");Requests.Request r=requests.send(id,to.getUniqueId(),trade?"trade":c.equals("tprhere")?"here":"tpa",now());tell(id,"Request sent to "+to.getUsername()+"; expires in 30 seconds.");tell(to.getUniqueId(),trade?p.getUsername()+" wants to trade. Cross-server acceptance brings them to you. /trade accept "+p.getUsername()+" or /trade deny "+p.getUsername():p.getUsername()+(r.type().equals("here")?" asks you to teleport to them. ":" asks to teleport to you. ")+"/tprAccept "+p.getUsername()+" or /tprDeny "+p.getUsername());return;}
  Player target=find(c.equals("rank")?(a.size()>2?a.get(2):""):arg);
  if(c.equals("tp")||c.equals("bring")||c.equals("invsee")){int min=c.equals("bring")?4:3;if(Commands.level(s.authority)<min)throw new IllegalArgumentException("Your staff rank cannot use this command");if(c.equals("bring")&&Commands.level(s.authority)<=Commands.level(states.get(target.getUniqueId()).authority))throw new IllegalArgumentException("You can only bring a lower rank");start(id,c.equals("bring")?target:p,c.equals("bring")?p:target,c.equals("invsee")?"invsee":"",false);return;}
  if(c.equals("maxhomes")?!s.homeAdmin:Commands.level(s.authority)<2)throw new IllegalArgumentException("Staff permission required");var fields=new ArrayList<String>(List.of(id.toString(),p.getUsername(),s.authority,c));fields.addAll(a.subList(1,a.size()));int targetIndex=c.equals("rank")?5:4;fields.set(targetIndex,target.getUniqueId().toString());send(target,new Wire(UUID.randomUUID(),"STAFF",target.getUniqueId(),fields));
 }
 private void start(UUID actor,Player mover,Player target,String after,boolean warmup){
  if(!available(mover)||!available(target))throw new IllegalArgumentException("This server is under maintenance. Join again later.");if(mover.getUniqueId().equals(target.getUniqueId()))throw new IllegalArgumentException("Choose another player");
  if(operations.values().stream().anyMatch(o->o.mover.equals(mover.getUniqueId())||o.mover.equals(target.getUniqueId())||o.target.equals(mover.getUniqueId())||o.target.equals(target.getUniqueId())))throw new IllegalArgumentException("One of you already has a teleport in progress");
  Operation op=new Operation(UUID.randomUUID(),actor,mover.getUniqueId(),target.getUniqueId(),server(mover),server(target),after,now()+20000);operations.put(op.id,op);send(target,new Wire(op.id,"LOCATE",target.getUniqueId(),List.of(warmup?"3":"0",states.get(mover.getUniqueId()).authority,after)));
 }
 private void location(Player target,Wire f){Operation op=operations.get(f.id());if(op==null||!op.target.equals(target.getUniqueId())||!op.targetServer.equals(server(target))||now()>=op.expires)return;Player mover=player(op.mover);if(!available(mover)||!op.moverServer.equals(server(mover)))throw new IllegalArgumentException("Teleport cancelled: player changed servers");var data=new ArrayList<>(f.args());data.add(target.getUniqueId().toString());send(mover,new Wire(op.id,"MOVE",mover.getUniqueId(),data));}
 private synchronized void tick(){requests.expire(now());
 if(render!=null&&now()>=nextMirror){nextMirror=now()+60000;render.publish((int)proxy.getAllPlayers().stream().filter(this::visible).count(),states.values().stream().filter(this::fresh).map(State::server).distinct().toList());}
for(Operation op:new ArrayList<>(operations.values())){
  if(player(op.target)==null||!server(player(op.target)).equals(op.targetServer)||now()>=op.expires){operations.remove(op.id);moved.remove(op.id);send(player(op.mover),new Wire(op.id,"CANCEL",op.mover,List.of()));tell(op.actor,"Teleport timed out; your inventory remains protected.");continue;}
  if(!op.after.isEmpty()){Player mover=player(op.mover),target=player(op.target);if(available(mover)&&available(target)&&server(mover).equals(op.targetServer)&&server(target).equals(op.targetServer)&&(moved.contains(op.id)||!server(mover).equals(op.moverServer))){operations.remove(op.id);moved.remove(op.id);send(mover,new Wire(op.id,op.after.equals("trade")?"TRADE_OPEN":"INSPECT",op.mover,List.of(op.target.toString())));}}
 }
 }
 @Subscribe public synchronized void disconnect(DisconnectEvent e){Player p=e.getPlayer();UUID id=p.getUniqueId();State s=states.get(id);if(announced.remove(id)&&s!=null&&!s.hidden)notice(p,false);states.remove(id);remoteTabs.remove(id);carryVanish.remove(id);requests.leave(id);replies.remove(id);replies.values().removeIf(id::equals);lastCommand.remove(id);for(Operation op:new ArrayList<>(operations.values()))if(op.mover.equals(id)||op.target.equals(id)){send(player(op.mover),new Wire(op.id,"CANCEL",op.mover,List.of()));operations.remove(op.id);tell(op.actor,"Teleport cancelled: a participant left the network.");}rosters();}
 @Subscribe public void stop(ProxyShutdownEvent e){proxy.getChannelRegistrar().unregister(CHANNEL);}
}
