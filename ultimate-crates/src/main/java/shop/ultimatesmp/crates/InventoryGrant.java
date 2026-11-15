package shop.ultimatesmp.crates;
import org.bukkit.inventory.ItemStack;
/** Plan the entire inventory change before consuming a key. Never drops overflow. */
final class InventoryGrant {
 static ItemStack[] copy(ItemStack[] source){ItemStack[] out=new ItemStack[source.length];for(int i=0;i<out.length;i++)out[i]=source[i]==null?null:source[i].clone();return out;}
 static ItemStack[] add(ItemStack[] source,ItemStack reward){ItemStack[] out=copy(source);int left=reward.getAmount();for(ItemStack item:out){if(item!=null&&item.isSimilar(reward)){int n=Math.min(left,Math.max(0,item.getMaxStackSize()-item.getAmount()));item.setAmount(item.getAmount()+n);left-=n;if(left==0)return out;}}for(int i=0;i<out.length;i++)if(out[i]==null||out[i].getType().isAir()||out[i].getAmount()==0){int n=Math.min(left,reward.getMaxStackSize());out[i]=reward.clone();out[i].setAmount(n);left-=n;if(left==0)return out;}return null;}
}
