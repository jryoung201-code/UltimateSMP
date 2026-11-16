package shop.ultimatesmp.crates;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
record LabelStyle(String text,String color,float size,double height,boolean visible){
 LabelStyle{if(text==null||text.length()>256||text.isBlank())throw new IllegalArgumentException("Text must be 1-256 characters");color=color.toLowerCase(Locale.ROOT);if(parseColor(color)==null)throw new IllegalArgumentException("Use a named color or #RRGGBB");if(!Float.isFinite(size)||size<.25f||size>4)throw new IllegalArgumentException("Size must be 0.25-4");if(!Double.isFinite(height)||height<1.1||height>6)throw new IllegalArgumentException("Height must be 1.1-6 blocks above the crate base");}
 static LabelStyle defaults(){return new LabelStyle("{name}","gold",1f,1.7,true);}
 static TextColor parseColor(String s){return s.startsWith("#")?s.matches("#[a-fA-F0-9]{6}")?TextColor.fromHexString(s):null:NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));}
 Component component(String name){return Component.empty().color(parseColor(color)).append(LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace("{name}",name).replace("\\n","\n")));}
 LabelStyle text(String v){return new LabelStyle(v,color,size,height,visible);}LabelStyle color(String v){return new LabelStyle(text,v,size,height,visible);}LabelStyle size(float v){return new LabelStyle(text,color,v,height,visible);}LabelStyle height(double v){return new LabelStyle(text,color,size,v,visible);}LabelStyle visible(boolean v){return new LabelStyle(text,color,size,height,v);}
}
