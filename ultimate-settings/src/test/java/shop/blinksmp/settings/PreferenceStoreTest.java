package shop.blinksmp.settings;

import java.nio.file.*;
import java.util.*;

public final class PreferenceStoreTest {
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("ultimate-settings-test-"); Path path=dir.resolve("players.yml");
        UUID one=UUID.randomUUID(),two=UUID.randomUUID();
        try {
            PreferenceStore store=new PreferenceStore(path);
            check(store.get(one).equals(Preferences.DEFAULT),"New player defaults");
            Preferences modified=Preferences.DEFAULT.toggle("booster").toggle("rank-tags").toggle("messages");
            store.put(one,modified); store.put(two,Preferences.DEFAULT.toggle("coordinates"));
            PreferenceStore reloaded=new PreferenceStore(path);
            check(reloaded.get(one).equals(modified),"Player choices must survive restart");
            check(reloaded.get(two).coordinates() && reloaded.get(two).rankTags(),"One player's choice must not change another's tags");
            reloaded.put(one,Preferences.DEFAULT);
            check(new PreferenceStore(path).get(one).equals(Preferences.DEFAULT),"Reset persists");
            Path archived=dir.resolveSibling(dir.getFileName()+"-moved"); Files.move(dir,archived); Files.writeString(dir,"blocked");
            try {
                boolean failed=false; try { reloaded.put(one,modified); } catch(java.io.IOException ex) { failed=true; }
                check(failed && reloaded.get(one).equals(Preferences.DEFAULT),"Failed save cannot apply choices");
            } finally { Files.delete(dir); Files.move(archived,dir); }
            Files.writeString(path,"players:\n  "+one+":\n    booster: this-is-not-a-boolean\n");
            boolean rejected=false; try { new PreferenceStore(path); } catch(Exception ex) { rejected=true; }
            check(rejected,"Invalid preference data must be rejected");
            System.out.println("PASS: settings persistence, UUID isolation, reset, failed save and corrupt data checks");
        } finally { try(var paths=Files.walk(dir)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
    }
}
