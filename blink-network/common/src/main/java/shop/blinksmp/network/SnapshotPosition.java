package shop.blinksmp.network;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Relocates only known top-level YAML scalars. Inventory and profile bytes are never deserialized or replaced. */
public final class SnapshotPosition {
    private SnapshotPosition() {}
    public static byte[] relocate(byte[] payload,String dimension,double x,double y,double z) {
        Objects.requireNonNull(payload);
        if(payload.length==0||payload.length>2_097_152||!Double.isFinite(y)||Math.abs(y)>4096||Math.abs(x)>29_999_984||Math.abs(z)>29_999_984)throw new IllegalArgumentException("Invalid rescue snapshot or height");
        Region.at(dimension,x,z);
        String text=new String(payload,StandardCharsets.UTF_8);
        Map<String,String> replacements=Map.of("dimension",dimension,"x",Double.toString(x),"y",Double.toString(y),"z",Double.toString(z));
        Set<String> seen=new HashSet<>();String[] lines=text.split("\n",-1);
        for(int i=0;i<lines.length;i++) {
            String line=lines[i];
            String key=line.contains(": ")?line.substring(0,line.indexOf(": ")):"";
            if(replacements.containsKey(key)){
                if(!seen.add(key))throw new IllegalArgumentException("Duplicate position scalar");
                line=key+": "+replacements.get(key)+(line.endsWith("\r")?"\r":"");
            }
            lines[i]=line;
        }
        if(seen.size()!=5)throw new IllegalArgumentException("Missing snapshot position");
        byte[] bytes=String.join("\n",lines).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>2_097_152)throw new IllegalArgumentException("Rescued snapshot too large");return bytes;
    }
}
