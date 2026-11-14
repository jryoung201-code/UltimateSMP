package shop.blinksmp.network;
import java.io.*;
import java.util.UUID;

public record Protocol(UUID player,UUID ticket,String target) {
    public static final String CHANNEL="blinksmp:network";
    public Protocol { if(!target.matches("BlinkSMP-Queue-(0[1-9]|[1-9][0-9])"))Region.dimensionOf(target); }
    public byte[] bytes() throws IOException {
        var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer);
        out.writeInt(1);out.writeUTF(player.toString());out.writeUTF(ticket.toString());out.writeUTF(target);return buffer.toByteArray();
    }
    public static Protocol read(byte[] data) throws IOException {
        if(data.length>256)throw new IOException("Oversized message");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(data));
            if(in.readInt()!=1)throw new IOException("Unsupported version");
            Protocol p=new Protocol(UUID.fromString(in.readUTF()),UUID.fromString(in.readUTF()),in.readUTF());
            if(in.available()!=0)throw new IOException("Trailing data");return p;
        } catch(IllegalArgumentException e) { throw new IOException("Invalid message",e); }
    }
}


