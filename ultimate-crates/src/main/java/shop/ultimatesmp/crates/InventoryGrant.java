package shop.ultimatesmp.crates;
import org.bukkit.inventory.ItemStack;
/** Plan the entire inventory change before consuming a key. Never drops overflow. */
final class InventoryGrant {
 static boolean empty(ItemStack item){return item==null||item.getAmount()<=0||item.getType()==org.bukkit.Material.AIR||item.getType()==org.bukkit.Material.CAVE_AIR||item.getType()==org.bukkit.Material.VOID_AIR;}
 static boolean acceptsAll(ItemStack[] source,java.util.List<ItemStack> rewards){for(ItemStack reward:rewards)if(add(source,reward)==null)return false;return true;}
 static ItemStack[] copy(ItemStack[] source){ItemStack[] out=new ItemStack[source.length];for(int i=0;i<out.length;i++)out[i]=source[i]==null?null:source[i].clone();return out;}
 static ItemStack[] add(ItemStack[] source,ItemStack reward){ItemStack[] out=copy(source);int left=reward.getAmount();for(ItemStack item:out){if(item!=null&&item.isSimilar(reward)){int n=Math.min(left,Math.max(0,item.getMaxStackSize()-item.getAmount()));item.setAmount(item.getAmount()+n);left-=n;if(left==0)return out;}}for(int i=0;i<out.length;i++)if(empty(out[i])){int n=Math.min(left,reward.getMaxStackSize());out[i]=reward.clone();out[i].setAmount(n);left-=n;if(left==0)return out;}return null;}
}
