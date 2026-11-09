package shop.blinksmp.swap;

/** Exercises the same reflection path against fake configuration instances, without a live server. */
public final class EquipmentSwitchTest {
    public static final class Settings { public boolean updateEquipmentOnPlayerActions;Settings(boolean enabled) { updateEquipmentOnPlayerActions=enabled; } }
    public static final class Invalid { public String updateEquipmentOnPlayerActions="true"; }
    public static final class FinalSetting { public final boolean updateEquipmentOnPlayerActions=true; }
    public static final class StaticSetting { public static boolean updateEquipmentOnPlayerActions=true; }
    private static int checks;
    private static void check(boolean value,String label) { checks++;if(!value) throw new AssertionError(label); }
    public static void main(String[] args) throws Exception {
        EquipmentSwitch control=new EquipmentSwitch();Settings first=new Settings(true);
        control.apply(first);check(!EquipmentSwitch.read(first) && control.owns(first),"Turn off Paper's extra updates");
        control.apply(first);control.restore();check(first.updateEquipmentOnPlayerActions,"Repeated enable preserved original value");
        Settings alreadyDisabled=new Settings(false);control.apply(alreadyDisabled);control.restore();
        check(!alreadyDisabled.updateEquipmentOnPlayerActions,"Respect original false setting");
        control.apply(first);Settings replacement=new Settings(true);control.apply(replacement);
        check(first.updateEquipmentOnPlayerActions && !replacement.updateEquipmentOnPlayerActions && control.owns(replacement),"Configuration reload replaces instance correctly");
        control.restore();check(replacement.updateEquipmentOnPlayerActions,"Reload instance restores correct original");
        control.restore();check(replacement.updateEquipmentOnPlayerActions,"Disable is idempotent");
        control.apply(alreadyDisabled);alreadyDisabled.updateEquipmentOnPlayerActions=true;control.restore();
        check(alreadyDisabled.updateEquipmentOnPlayerActions,"Preserve an external change back to true");
        control.apply(first);
        for(Object invalid:new Object[]{new Invalid(),new FinalSetting(),new StaticSetting(),new Object()}) {
            try { control.apply(invalid);throw new AssertionError("Unsupported schema accepted"); }
            catch(ReflectiveOperationException expected) { checks++; }
            check(control.owns(first) && !first.updateEquipmentOnPlayerActions,"Invalid replacement preserved working setting");
        }
        control.restore();check(first.updateEquipmentOnPlayerActions,"Working setting still restores after failed replacements");
        System.out.println("PASS: "+checks+" reflection, restore, external-change and configuration-reload checks");
    }
}
