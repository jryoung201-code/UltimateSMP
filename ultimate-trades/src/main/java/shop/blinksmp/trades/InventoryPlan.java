package shop.blinksmp.trades;

import java.util.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/** Computes complete replacement inventories without mutating either input or offer. */
public final class InventoryPlan {
    private InventoryPlan() {}
    public static boolean empty(ItemStack item) { return item==null || (item.getType()==Material.AIR || item.getType()==Material.CAVE_AIR || item.getType()==Material.VOID_AIR) || item.getAmount()<=0; }
    public static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result=new ItemStack[source.length];
        for(int i=0;i<source.length;i++) result[i]=empty(source[i])?null:source[i].clone();
        return result;
    }
    public static boolean matches(ItemStack[] storage,Map<Integer,ItemStack> offered) {
        return offered.entrySet().stream().allMatch(e->e.getKey()>=0 && e.getKey()<storage.length && !empty(e.getValue()) && e.getValue().equals(storage[e.getKey()]));
    }
    public static ItemStack[] receive(ItemStack[] current,Map<Integer,ItemStack> outgoing,Collection<ItemStack> incoming,int inventoryLimit) {
        if(!matches(current,outgoing)) throw new IllegalArgumentException("An offered stack changed. Confirm the updated trade again.");
        ItemStack[] result=copy(current);
        outgoing.keySet().forEach(slot->result[slot]=null);
        for(ItemStack source:incoming) {
            if(empty(source)) throw new IllegalArgumentException("Invalid incoming item");
            int left=source.getAmount();
            int cap=Math.min(inventoryLimit,source.getMaxStackSize());
            if(cap<1) throw new IllegalArgumentException("Invalid stack size");
            for(int i=0;i<result.length && left>0;i++) {
                ItemStack stack=result[i];
                if(!empty(stack) && stack.isSimilar(source) && stack.getAmount()<cap) {
                    int added=Math.min(left,cap-stack.getAmount());stack.setAmount(stack.getAmount()+added);left-=added;
                }
            }
            for(int i=0;i<result.length && left>0;i++) if(empty(result[i])) {
                ItemStack added=source.clone();added.setAmount(Math.min(left,cap));result[i]=added;left-=added.getAmount();
            }
            if(left>0) throw new IllegalArgumentException("Not enough inventory space. Free space or change the offer.");
        }
        return result;
    }
}
