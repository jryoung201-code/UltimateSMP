package shop.blinksmp.settings;

import java.util.List;

public final class PreferencesTest {
    public static void main(String[] args) {
        int checks=0;
        var keys=List.of("booster","rank-tags","players","messages","join-messages","coordinates","exclude-monsters");
        for(String key:keys) {
            Preferences changed=Preferences.DEFAULT.toggle(key);
            if(!changed.toggle(key).equals(Preferences.DEFAULT)) throw new AssertionError("Toggle roundtrip: "+key);
            for(String other:keys) {
                boolean expected=key.equals(other)?!Preferences.DEFAULT.value(other):Preferences.DEFAULT.value(other);
                if(changed.value(other)!=expected) throw new AssertionError("Unrelated choice changed: "+other);
                checks++;
            }
        }
        for(int configured=2;configured<=32;configured++) for(int world=2;world<=32;world++) for(int previous=2;previous<=32;previous++) {
            int value=Preferences.chunkCap(configured,world,previous);
            if(value<2 || value>world || value>previous || value>configured) throw new AssertionError("Booster must not raise distances");
            checks++;
        }
        if(Preferences.DEFAULT.booster() || Preferences.DEFAULT.coordinates() || !Preferences.DEFAULT.rankTags()
                || !Preferences.DEFAULT.players() || !Preferences.DEFAULT.messages() || !Preferences.DEFAULT.joinMessages()) throw new AssertionError("Defaults changed");
        Preferences excluded=Preferences.DEFAULT.toggle("exclude-monsters");
        if(Preferences.DEFAULT.excludeMonsters()) throw new AssertionError("Opt-out defaults off");
        for(String key:keys) if(!key.equals("exclude-monsters") && !excluded.toggle(key).excludeMonsters()) throw new AssertionError("Other setting cleared monster opt-out");
        System.out.println("PASS: "+checks+" preference isolation and chunk-cap checks");
    }
}

