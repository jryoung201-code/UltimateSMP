package shop.blinksmp.ranks;
import java.util.*;
public class ShopRankTest {
 public static void main(String[] args){
  UUID bedrock=UUID.fromString("00000000-0000-0000-0009-01fb5b0f0d11");if(bedrock.equals(new UUID(0,0)))throw new AssertionError();
  var permanent=Set.of(Rank.OWNER);var voucher=TemporaryRankPolicy.combine(permanent,Map.of(Rank.ULTIMATE_PLUS,2000L),1000);
  var shop=TemporaryRankPolicy.combine(voucher,Map.of(Rank.ULTIMATE_PLUS_PLUS,2000L),1000);
  if(!shop.containsAll(Set.of(Rank.OWNER,Rank.ULTIMATE_PLUS,Rank.ULTIMATE_PLUS_PLUS)))throw new AssertionError("Sources must combine");
  var expired=TemporaryRankPolicy.combine(voucher,Map.of(Rank.ULTIMATE_PLUS_PLUS,500L),1000);
  if(expired.contains(Rank.ULTIMATE_PLUS_PLUS)||!expired.contains(Rank.OWNER)||!expired.contains(Rank.ULTIMATE_PLUS))throw new AssertionError("Expiry must preserve permanent and voucher assignments");
  for(Rank r:Rank.values())if(r.name().contains("SUPPORTER")&&(r.level!=0||r.homes!=0||r.defaultWeight()>=100))throw new AssertionError("Recognition never grants staff authority or homes");
  if(Rank.display(shop,Map.of())!=Rank.OWNER)throw new AssertionError("Staff remains visible by default");
  System.out.println("Shop rank policy checks passed");
 }
}
