package shop.ultimatesmp.network.velocity;
import java.net.*;import java.net.http.*;import java.time.Duration;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import org.slf4j.Logger;
/** Optional HTTPS status mirror. Never on the teleport, consent or inventory transaction path. */
final class RenderMirror {
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final URI endpoint;private final String token;private final Logger log;private final AtomicBoolean busy=new AtomicBoolean();private volatile String previous="";
 RenderMirror(Logger log){this.log=log;token=System.getenv().getOrDefault("ULTIMATE_NETWORK_RENDER_TOKEN","");String url=System.getenv().getOrDefault("ULTIMATE_NETWORK_RENDER_URL","");URI parsed=null;
  if(!url.isBlank()&&!token.isBlank())try{URI u=URI.create(url);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null)throw new IllegalArgumentException();parsed=u;}catch(Exception ex){log.warn("Render mirror URL must be HTTPS without embedded credentials");}endpoint=parsed;
 }
 void publish(int online,List<String> servers){if(endpoint==null)return;String payload="{\"proxy\":\"UltimateSMP\",\"online\":"+online+",\"servers\":["+String.join(",",servers.stream().filter(s->s.matches("[A-Za-z0-9_-]{1,64}")).sorted().map(s->"\""+s+"\"").toList())+"]}";
  if(online==0&&payload.equals(previous)||!busy.compareAndSet(false,true))return;previous=payload;
  HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build();
  client.sendAsync(request,HttpResponse.BodyHandlers.discarding()).whenComplete((r,error)->{busy.set(false);if(error!=null||r.statusCode()!=200){previous="";log.warn("Render status mirror unavailable; gameplay continues normally");}});
 }
}
