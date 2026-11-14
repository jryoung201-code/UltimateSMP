package shop.blinksmp.network;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Proxy-authorized travel intent, authenticated with the source's private exact-session token. */
public record SwitchRequest(UUID player,UUID nonce,String target,long expiresAt){
    public static final String CHANNEL="blinksmp:switch";
    public SwitchRequest{Objects.requireNonNull(player);Objects.requireNonNull(nonce);Region.dimensionOf(target);}
    private byte[] body()throws IOException{
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(1);out.writeUTF(player.toString());out.writeUTF(nonce.toString());out.writeUTF(target);out.writeLong(expiresAt);return bytes.toByteArray();
    }
    private static byte[] mac(byte[] bytes,UUID session)throws GeneralSecurityException{
        Mac mac=Mac.getInstance("HmacSHA256");byte[] key=ByteBuffer.allocate(16).putLong(session.getMostSignificantBits()).putLong(session.getLeastSignificantBits()).array();
        mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(bytes);
    }
    public byte[] bytes(UUID session)throws IOException,GeneralSecurityException{
        byte[] body=body(),tag=mac(body,session);var out=new ByteArrayOutputStream();out.write(body);out.write(tag);return out.toByteArray();
    }
    public static SwitchRequest read(byte[] bytes,UUID session,long now)throws IOException,GeneralSecurityException{
        if(bytes.length<33||bytes.length>256)throw new IOException("Invalid request size");
        byte[] body=Arrays.copyOf(bytes,bytes.length-32),tag=Arrays.copyOfRange(bytes,bytes.length-32,bytes.length);
        if(!MessageDigest.isEqual(tag,mac(body,session)))throw new IOException("Invalid request authentication");
        try{
            var in=new DataInputStream(new ByteArrayInputStream(body));if(in.readInt()!=1)throw new IOException("Invalid request version");
            var request=new SwitchRequest(UUID.fromString(in.readUTF()),UUID.fromString(in.readUTF()),in.readUTF(),in.readLong());
            if(in.available()!=0||request.expiresAt()<=now||request.expiresAt()-now>90_000)throw new IOException("Expired request");return request;
        }catch(IllegalArgumentException ex){throw new IOException("Invalid request",ex);}
    }
}
