package shop.ultimatesmp.crates;
import java.util.*;
final class LootPolicy {
 enum Rarity {LEGACY(0),COMMON(60),UNCOMMON(25),RARE(12),LEGENDARY(3);final double weight;Rarity(double w){weight=w;}static Rarity parse(String s){try{return valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception e){throw new IllegalArgumentException("Use common, uncommon, rare or legendary");}}}
 static double[] chances(List<Crate.Reward> rewards){
  double[] result=new double[rewards.size()];if(rewards.isEmpty())return result;double fixed=0;boolean legacy=rewards.stream().allMatch(r->r.rarity()==Rarity.LEGACY);Map<Rarity,Double> sums=new EnumMap<>(Rarity.class);
  for(var r:rewards)if(r.chance()!=null)fixed+=r.chance();else{Rarity group=legacy?Rarity.LEGACY:r.rarity()==Rarity.LEGACY?Rarity.COMMON:r.rarity();sums.merge(group,(double)r.weight(),Double::sum);}
  if(fixed>100.00000001)throw new IllegalArgumentException("Manual chances total more than 100%");if(sums.isEmpty()&&Math.abs(fixed-100)>0.00000001)throw new IllegalArgumentException("When every reward is manual, chances must total 100%");
  double remaining=Math.max(0,100-fixed),budget=sums.keySet().stream().mapToDouble(r->r==Rarity.LEGACY?1:r.weight).sum();
  for(int i=0;i<rewards.size();i++){var r=rewards.get(i);if(r.chance()!=null)result[i]=r.chance()/100;else{Rarity group=legacy?Rarity.LEGACY:r.rarity()==Rarity.LEGACY?Rarity.COMMON:r.rarity();double share=group==Rarity.LEGACY?1:group.weight;result[i]=remaining/100*share/budget*r.weight()/sums.get(group);}}
  return result;
 }
 static int choose(List<Crate.Reward> rewards,double random){if(!Double.isFinite(random)||random<0||random>=1)throw new IllegalArgumentException();double[] chances=chances(rewards);int last=-1;for(int i=0;i<chances.length;i++)if(chances[i]>0){last=i;if(random<chances[i])return i;random-=chances[i];}if(last<0)throw new IllegalArgumentException("No active rewards");return last;}
}
