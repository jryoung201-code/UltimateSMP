package shop.ultimatesmp.crates;
import java.util.*;import org.bukkit.inventory.ItemStack;
record Crate(String id,String name,long revision,List<Reward> rewards){
 Crate{if(id==null||!id.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("Use lowercase letters, numbers, _ or - (max 32)");if(name==null||name.isBlank()||name.length()>64)throw new IllegalArgumentException("Name must be 1-64 characters");rewards=List.copyOf(rewards);if(rewards.size()>45)throw new IllegalArgumentException("Max 45 rewards");}
 record Reward(ItemStack item,int weight){Reward{if(item==null||item.getType().isAir()||item.getAmount()<1||item.getAmount()>item.getMaxStackSize()||weight<1||weight>1000000)throw new IllegalArgumentException("Invalid reward or weight");item=item.clone();}}
 static int select(List<Integer> weights,long ticket){long total=0;for(int w:weights){if(w<1)throw new IllegalArgumentException();total+=w;}if(ticket<0||ticket>=total)throw new IllegalArgumentException();for(int i=0;i<weights.size();i++){ticket-=weights.get(i);if(ticket<0)return i;}throw new IllegalStateException();}
}
