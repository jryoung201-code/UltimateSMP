package shop.blinksmp.lodestones;

import java.util.*;
import java.util.concurrent.atomic.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.*;
import static org.mockito.Mockito.*;

public final class CompassTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        for(String dimension:List.of("Overworld","Nether","End")){
            Target target=new Target(dimension,-300,43,-395);check(target.equals(Target.decode(target.encode())));
        }
        for(String bad:List.of("","1|Overworld|1|2","2|Overworld|1|2|3","1|Unknown|1|2|3","1|Overworld|30000000|2|3","1|End|1|2048|3","1|End|-2147483648|0|0","x".repeat(101))){
            try{Target.decode(bad);throw new AssertionError("Bad target accepted");}catch(IllegalArgumentException expected){checks++;}
        }
        World first=mock(World.class),second=mock(World.class),nether=mock(World.class),custom=mock(World.class);
        when(first.getEnvironment()).thenReturn(World.Environment.NORMAL);when(second.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);when(custom.getEnvironment()).thenReturn(World.Environment.NORMAL);
        ItemStack item=mock(ItemStack.class);CompassMeta meta=mock(CompassMeta.class);PersistentDataContainer data=mock(PersistentDataContainer.class);
        Map<NamespacedKey,String> tags=new HashMap<>();AtomicReference<Location> location=new AtomicReference<>(new Location(first,-300,43,-395));AtomicBoolean tracked=new AtomicBoolean(true);
        when(item.getType()).thenReturn(Material.COMPASS);when(item.getItemMeta()).thenReturn(meta);when(item.setItemMeta(meta)).thenReturn(true);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(CompassService.TARGET_KEY,PersistentDataType.STRING)).thenAnswer(a->tags.get(CompassService.TARGET_KEY));
        doAnswer(a->{tags.put(a.getArgument(0),a.getArgument(2));return null;}).when(data).set(eq(CompassService.TARGET_KEY),eq(PersistentDataType.STRING),anyString());
        doAnswer(a->{tags.remove(a.getArgument(0));return null;}).when(data).remove(CompassService.TARGET_KEY);
        when(meta.hasLodestone()).thenAnswer(a->location.get()!=null);when(meta.getLodestone()).thenAnswer(a->location.get());
        when(meta.isLodestoneTracked()).thenAnswer(a->tracked.get());doAnswer(a->{tracked.set(a.getArgument(0));return null;}).when(meta).setLodestoneTracked(anyBoolean());
        doAnswer(a->{location.set(a.getArgument(0));return null;}).when(meta).setLodestone(any(Location.class));
        doAnswer(a->{location.set(null);return null;}).when(meta).clearLodestone();
        when(meta.serialize()).thenReturn(Map.of("LodestonePosWorld","minecraft:overworld"));
        CompassService source=new CompassService(Map.of("Overworld",first),true);
        check(source.repair(item));check(!tracked.get());String saved=tags.get(CompassService.TARGET_KEY);check(saved.equals("1|Overworld|-300|43|-395"));
        check(!source.repair(item)); // Idempotence: no repeated inventory updates.
        CompassService destination=new CompassService(Map.of("Overworld",second,"Nether",nether),true);
        location.set(new Location(null,-300,43,-395)); // Target world unresolved after a backend transfer.
        check(destination.repair(item));check(location.get().getWorld()==second);check(saved.equals(tags.get(CompassService.TARGET_KEY)));
        location.set(null);check(destination.repair(item));check(location.get().getBlockX()==-300&&!tracked.get());
        location.set(new Location(nether,50,70,-7));tracked.set(true);check(destination.repair(item));
        check(destination.target(meta).equals(new Target("Nether",50,70,-7)));check(!tracked.get());
        CompassService missing=new CompassService(Map.of(),true);check(!missing.repair(item));check(destination.target(meta).dimension().equals("Nether"));
        location.set(new Location(custom,1,70,2));tracked.set(true);check(!destination.repair(item));check(location.get().getWorld()==custom&&tracked.get());
        location.set(new Location(nether,50,70,-7));check(destination.forget(item));check(!tags.containsKey(CompassService.TARGET_KEY));check(location.get()==null);check(!destination.repair(item));
        check(!destination.repair(null));ItemStack ordinary=mock(ItemStack.class);when(ordinary.getType()).thenReturn(Material.RECOVERY_COMPASS);check(!destination.repair(ordinary));
        location.set(new Location(second,0,64,0));check(!new CompassService(Map.of("Overworld",second),false).repair(item));
        verify(item,never()).setAmount(anyInt());verify(meta,never()).setLore(any());verify(meta,never()).setDisplayName(anyString());
        verify(first,never()).getChunkAt(anyInt(),anyInt());verify(second,never()).getChunkAt(anyInt(),anyInt());
        System.out.println("PASS: "+checks+" target and API checks: cross-server remapping, preserved coordinates, rebind, missing worlds, custom-world isolation, no item-count/name/lore changes, no chunk loads, forget and idempotence");
    }
}
