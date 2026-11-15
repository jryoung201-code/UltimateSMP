package shop.blinksmp.chat.bridge;

import java.io.*;
import java.util.UUID;

/** Bounded, versioned server-to-proxy public-chat envelope. No commands or routing instructions. */
public record ChatFrame(UUID messageId, UUID playerId, String json) {
    public static final String CHANNEL="ultimatesmp:chat";
    public static final int MAX_BYTES=32768, MAX_JSON=8192;
    public byte[] encode() throws IOException {
        if(messageId==null||playerId==null||json==null||json.isBlank()||json.length()>MAX_JSON)throw new IOException("Invalid chat frame");
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            out.writeInt(1);out.writeLong(messageId.getMostSignificantBits());out.writeLong(messageId.getLeastSignificantBits());
            out.writeLong(playerId.getMostSignificantBits());out.writeLong(playerId.getLeastSignificantBits());out.writeUTF(json);
        }
        if(bytes.size()>MAX_BYTES)throw new IOException("Chat frame too large");
        return bytes.toByteArray();
    }
    public static ChatFrame decode(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length>MAX_BYTES)throw new IOException("Chat frame too large");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(in.readInt()!=1)throw new IOException("Unsupported chat protocol");
            var frame=new ChatFrame(new UUID(in.readLong(),in.readLong()),new UUID(in.readLong(),in.readLong()),in.readUTF());
            if(in.available()!=0||frame.json.isBlank()||frame.json.length()>MAX_JSON)throw new IOException("Malformed chat frame");
            return frame;
        }
    }
}
