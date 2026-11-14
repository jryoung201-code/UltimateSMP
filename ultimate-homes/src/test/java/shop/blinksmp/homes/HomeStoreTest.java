package shop.blinksmp.homes;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import shop.blinksmp.homes.HomeStore.Home;

public final class HomeStoreTest {
    private static int checks;
    private static void check(boolean condition,String name) { checks++;if(!condition) throw new AssertionError(name); }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("ultimate-homes-test-");
        try {
            UUID player=UUID.randomUUID(),other=UUID.randomUUID(),world=UUID.randomUUID();HomeStore store=new HomeStore(dir);
            Home home=new Home("Base",world,"world",12.5,64,42.25,90f,10f);
            store.put(player,home,1);check(store.homes(player).size()==1 && store.homes(other).isEmpty(),"UUID ownership isolation");
            HomeStore restarted=new HomeStore(dir);check(home.equals(restarted.get(player,"BASE")),"Exact world, coordinates and rotation survive restart");
            Home second=new Home("Farm",world,"world",100,70,-200,0,0);
            byte[] before=Files.readAllBytes(dir.resolve(player+".yml"));
            try { store.put(player,second,1);throw new AssertionError("Limit bypass"); } catch(IllegalArgumentException expected) { checks++; }
            check(Arrays.equals(before,Files.readAllBytes(dir.resolve(player+".yml"))),"Limit rejection does not modify disk");
            store.put(player,second,2);check(store.homes(player).size()==2,"Higher permission allows new home");
            Home updated=new Home("BASE",world,"world",55,80,55,180,0);store.put(player,updated,1);
            check(store.homes(player).size()==2 && store.get(player,"base").equals(updated),"Downgrade preserves existing homes and permits updates");
            try { store.put(player,new Home("third",world,"world",0,64,0,0,0),1);throw new AssertionError("Downgrade allowed extra home"); } catch(IllegalArgumentException expected) { checks++; }
            check(store.delete(player,"FARM") && !store.delete(player,"missing"),"Delete exact normalized home");
            check(new HomeStore(dir).homes(player).size()==1,"Deletion survives restart");
            store.unload(player);check(store.get(player,"base").equals(updated),"Unload/rejoin reloads data");
            try { store.homes(player).clear();throw new AssertionError("Mutable public view"); } catch(UnsupportedOperationException expected) { checks++; }
            try { new Home("bad",world,"world",Double.NaN,64,0,0,0);throw new AssertionError("NaN coordinate accepted"); } catch(IllegalArgumentException expected) { checks++; }
            store.setOverride(player,9);
            check(new HomeStore(dir).override(player)==9,"Staff override survives restart");
            store.put(player,second,HomeLimits.UNLIMITED);
            check(store.override(player)==9,"Saving home preserves override");
            HomeStore replica=new HomeStore(dir.resolve("replica"));
            replica.importNetworkModule(player,store.exportNetworkModule(player));
            check(replica.override(player)==9 && replica.homes(player).equals(store.homes(player)),"Network round trip includes override and homes");
            String invalidProfile=store.exportNetworkModule(player).replace("max-homes-override: 9","max-homes-override: -1");
            try{replica.importNetworkModule(player,invalidProfile);throw new AssertionError("Invalid override accepted");}catch(IOException expected){checks++;}
            check(replica.override(player)==9,"Rejected network override preserves previous state");
            store.setOverride(player,0);check(new HomeStore(dir).override(player)==0,"Zero override persists");
            store.setOverride(player,null);replica.importNetworkModule(player,store.exportNetworkModule(player));
            check(replica.override(player)==null && new HomeStore(dir).override(player)==null,"Reset propagates and persists");
            check(!store.homes(player).isEmpty(),"Override reset never deletes homes");
            // Legacy files without override continue to load.
            check(store.override(other)==null,"Legacy files default to permission limits");
            // Corrupt data must never be silently overwritten by a new home.
            Path file=dir.resolve(player+".yml");Files.writeString(file,"version: 1\nowner: 'wrong-owner'\nhomes: {}\n");store.unload(player);
            byte[] invalid=Files.readAllBytes(file);
            try { store.put(player,home,10);throw new AssertionError("Corrupt owner overwritten"); } catch(IOException expected) { checks++; }
            check(Arrays.equals(invalid,Files.readAllBytes(file)),"Corrupt input preserved");
            // Force a write failure: the per-UUID target is an occupied directory.
            UUID blocked=UUID.randomUUID();store.homes(blocked);Path occupied=dir.resolve(blocked+".yml");Files.createDirectory(occupied);Files.writeString(occupied.resolve("keep.txt"),"keep");
            try { store.put(blocked,home,10);throw new AssertionError("Write failure not reported"); } catch(IOException expected) { checks++; }
            try{store.setOverride(blocked,5);throw new AssertionError("Override write failure not reported");}catch(IOException expected){checks++;}
            check(store.override(blocked)==null,"Failed override save does not update memory");
            check(store.homes(blocked).isEmpty() && Files.readString(occupied.resolve("keep.txt")).equals("keep"),"Failed save publishes no in-memory home or destructive replacement");
            try(var files=Files.list(dir)) { check(files.noneMatch(p->p.toString().endsWith(".tmp")),"Temporary files cleaned after success/failure"); }
            System.out.println("PASS: "+checks+" atomic persistence, restart, limit, corruption and write-failure checks");
        } finally { try(var walk=Files.walk(dir)) { for(Path path:walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); } }
    }
}

