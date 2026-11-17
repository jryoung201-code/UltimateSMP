package shop.ultimatesmp.spawn;
import java.util.*;import org.bukkit.attribute.Attribute;import org.bukkit.configuration.ConfigurationSection;import org.bukkit.entity.Player;import org.bukkit.potion.PotionEffect;
record DuelVitals(double health,int food,float saturation,float exhaustion,double absorption,long captured,List<Map<String,Object>> effects){
 DuelVitals{if(!Double.isFinite(health)||health<=0||health>2048||food<0||food>20||!Float.isFinite(saturation)||saturation<0||!Float.isFinite(exhaustion)||exhaustion<0||!Double.isFinite(absorption)||absorption<0)throw new IllegalArgumentException("Invalid duel vitals");effects=List.copyOf(effects);}
 static DuelVitals capture(Player p){return new DuelVitals(p.getHealth(),p.getFoodLevel(),p.getSaturation(),p.getExhaustion(),p.getAbsorptionAmount(),System.currentTimeMillis(),p.getActivePotionEffects().stream().map(e->new LinkedHashMap<String,Object>(e.serialize())).map(m->(Map<String,Object>)m).toList());}
 void write(ConfigurationSection y){y.set("health",health);y.set("food",food);y.set("saturation",saturation);y.set("exhaustion",exhaustion);y.set("absorption",absorption);y.set("captured",captured);y.set("effects",effects);}
 static DuelVitals read(ConfigurationSection y){List<Map<String,Object>> effects=new ArrayList<>();for(var raw:y.getMapList("effects")){Map<String,Object> copy=new HashMap<>();raw.forEach((k,v)->copy.put(String.valueOf(k),v));effects.add(copy);}return new DuelVitals(y.getDouble("health"),y.getInt("food"),(float)y.getDouble("saturation"),(float)y.getDouble("exhaustion"),y.getDouble("absorption"),y.getLong("captured"),effects);}
 void apply(Player p){
  for(var effect:List.copyOf(p.getActivePotionEffects()))p.removePotionEffect(effect.getType());
  long elapsed=Math.max(0,(System.currentTimeMillis()-captured)/50);
  for(var raw:effects){PotionEffect effect=new PotionEffect(raw);if(effect.isInfinite())p.addPotionEffect(effect);else{int left=(int)Math.max(0,effect.getDuration()-elapsed);if(left>0)p.addPotionEffect(effect.withDuration(left));}}
  double max=Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue();p.setHealth(Math.max(.5,Math.min(health,max)));p.setFoodLevel(food);p.setSaturation(saturation);p.setExhaustion(exhaustion);p.setAbsorptionAmount(absorption);p.setFireTicks(0);p.setFreezeTicks(0);p.setFallDistance(0);p.setVelocity(new org.bukkit.util.Vector());
 }
}
