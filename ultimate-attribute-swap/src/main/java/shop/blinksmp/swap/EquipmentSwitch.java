package shop.blinksmp.swap;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

/** Owns one mutable runtime setting, remembering its value before this plugin changed it. */
public final class EquipmentSwitch {
    private Object owner;
    private Field field;
    private boolean original;
    private static Field find(Object settings) throws ReflectiveOperationException {
        Objects.requireNonNull(settings,"Paper unsupported settings are not initialized");
        Field found=settings.getClass().getField("updateEquipmentOnPlayerActions");
        int flags=found.getModifiers();
        if(found.getType()!=boolean.class || Modifier.isStatic(flags) || Modifier.isFinal(flags))
            throw new NoSuchFieldException("Paper equipment-update setting is not a mutable instance boolean");
        return found;
    }
    public static boolean read(Object settings) throws ReflectiveOperationException { return find(settings).getBoolean(settings); }
    public void apply(Object settings) throws ReflectiveOperationException {
        Field next=find(settings);
        if(owner==settings) { next.setBoolean(settings,false);return; }
        // Validate the new target before releasing the previous one.
        boolean nextOriginal=next.getBoolean(settings);
        restore();next.setBoolean(settings,false);
        owner=settings;field=next;original=nextOriginal;
    }
    public boolean owns(Object settings) { return owner==settings; }
    public void restore() throws ReflectiveOperationException {
        if(owner==null) return;
        // Do not overwrite an external change that has already restored the flag to true.
        if(!field.getBoolean(owner)) field.setBoolean(owner,original);
        owner=null;field=null;
    }
}
