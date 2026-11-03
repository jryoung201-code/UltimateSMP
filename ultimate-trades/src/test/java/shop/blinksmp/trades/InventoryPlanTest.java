package shop.blinksmp.trades;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Planner tests with simulated item equality/stack limits; no running server required. */
public final class InventoryPlanTest {
    private static final class Stack extends ItemStack {
        private final String tag;
        private final int cap;
        private int amount;
        Stack(String tag,int amount,int cap) { super();this.tag=tag;this.amount=amount;this.cap=cap; }
        @Override public Material getType() { return Material.DIAMOND; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount=amount; }
        @Override public int getMaxStackSize() { return cap; }
        @Override public boolean isSimilar(ItemStack other) { return other instanceof Stack s && tag.equals(s.tag) && cap==s.cap; }
        @Override public boolean equals(Object other) { return other instanceof Stack s && isSimilar(s) && amount==s.amount; }
        @Override public int hashCode() { return Objects.hash(tag,cap,amount); }
        @Override public ItemStack clone() { return new Stack(tag,amount,cap); }
    }
    private static int checks;
    private static void check(boolean good,String label) { checks++;if(!good) throw new AssertionError(label); }
    private static Stack stack(String tag,int amount) { return new Stack(tag,amount,64); }
    private static void fails(Runnable operation,String name) { try { operation.run();throw new AssertionError(name); } catch(IllegalArgumentException expected) { checks++; } }
    private static Map<String,Integer> totals(ItemStack[]... inventories) {
        Map<String,Integer> result=new TreeMap<>();
        for(ItemStack[] inventory:inventories) for(ItemStack item:inventory) if(item instanceof Stack s) result.merge(s.tag,s.getAmount(),Integer::sum);
        return result;
    }
    public static void main(String[] args) {
        ItemStack[] a={stack("a",20),null},b={stack("b",32),null};
        Map<Integer,ItemStack> offerA=Map.of(0,a[0].clone()),offerB=Map.of(0,b[0].clone());
        ItemStack[] nextA=InventoryPlan.receive(a,offerA,offerB.values(),64),nextB=InventoryPlan.receive(b,offerB,offerA.values(),64);
        check(totals(a,b).equals(totals(nextA,nextB)),"All items conserved");
        check(nextA[0].equals(b[0]) && nextB[0].equals(a[0]),"Exchanged correctly");
        check(a[0].getAmount()==20 && b[0].getAmount()==32,"Planning leaves both original inventories unchanged");
        nextA[0].setAmount(1);check(b[0].getAmount()==32 && offerB.get(0).getAmount()==32,"No mutable aliases");
        fails(()->InventoryPlan.receive(new ItemStack[]{stack("a",19)},offerA,List.of(),64),"Quantity changed");
        fails(()->InventoryPlan.receive(new ItemStack[]{stack("other-meta",20)},offerA,List.of(),64),"Different metadata");
        fails(()->InventoryPlan.receive(new ItemStack[]{stack("a",64)},Map.of(),List.of(stack("b",1)),64),"Full inventory");
        ItemStack[] partial={stack("a",63)};
        fails(()->InventoryPlan.receive(partial,Map.of(),List.of(stack("a",2)),64),"Partial fit rejected without mutations");
        check(partial[0].getAmount()==63,"Failed plan did not mutate source");
        ItemStack[] merged=InventoryPlan.receive(partial,Map.of(),List.of(stack("a",1)),64);
        check(merged[0].getAmount()==64,"Stack merge");
        ItemStack[] split=InventoryPlan.receive(new ItemStack[3],Map.of(),List.of(new Stack("custom",33,16)),64);
        check(split[0].getAmount()==16 && split[1].getAmount()==16 && split[2].getAmount()==1,"Custom stack limit");
        split=InventoryPlan.receive(new ItemStack[2],Map.of(),List.of(stack("a",10)),8);
        check(split[0].getAmount()==8 && split[1].getAmount()==2,"Inventory stack limit");
        Random random=new Random(25736);
        for(int iteration=0;iteration<1000;iteration++) {
            ItemStack[] left=new ItemStack[36],right=new ItemStack[36];Map<Integer,ItemStack> x=new LinkedHashMap<>(),y=new LinkedHashMap<>();
            for(int i=0;i<12;i++) {
                left[i]=new Stack("item"+random.nextInt(5),1+random.nextInt(16),16);
                right[i]=new Stack("item"+random.nextInt(5),1+random.nextInt(16),16);
                if(random.nextBoolean()) x.put(i,left[i].clone());
                if(random.nextBoolean()) y.put(i,right[i].clone());
            }
            Map<String,Integer> before=totals(left,right);
            ItemStack[] l=InventoryPlan.receive(left,x,y.values(),64),r=InventoryPlan.receive(right,y,x.values(),64);
            check(before.equals(totals(l,r)),"Randomized item conservation");
            check(before.equals(totals(left,right)),"Randomized input immutability");
        }
        System.out.println("PASS: "+checks+" inventory capacity, metadata equality, stack-limit and conservation checks (simulated stacks)");
    }
}
