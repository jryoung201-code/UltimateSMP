package shop.ultimatesmp.network;
import java.io.*;import java.util.*;
/** A bounded backend-only protocol. UUID always identifies the authenticated carrier upstream. */
public record Wire(UUID id,String kind,UUID actor,List<String> args){
 public static final String CHANNEL="ultimatesmp:network";public static final int LIMIT=30000;
 public Wire{Objects.requireNonNull(id);Objects.requireNonNull(actor);if(kind==null||!kind.matches("[A-Z_]{1,24}"))throw new IllegalArgumentException("Invalid kind");args=List.copyOf(args);if(args.size()>512||args.stream().anyMatch(s->s==null||s.length()>4096||s.indexOf('\u0000')>=0))throw new IllegalArgumentException("Invalid fields");}
 public static Wire of(String kind,UUID actor,String...args){return new Wire(UUID.randomUUID(),kind,actor,List.of(args));}
 public byte[] encode(){try{var b=new ByteArrayOutputStream();var o=new DataOutputStream(b);o.writeInt(0x554e0100);o.writeLong(id.getMostSignificantBits());o.writeLong(id.getLeastSignificantBits());o.writeUTF(kind);o.writeLong(actor.getMostSignificantBits());o.writeLong(actor.getLeastSignificantBits());o.writeShort(args.size());for(String s:args)o.writeUTF(s);if(b.size()>LIMIT)throw new IllegalArgumentException("Frame too large");return b.toByteArray();}catch(IOException e){throw new IllegalArgumentException(e);}}
 public static Wire decode(byte[] data){if(data==null||data.length>LIMIT)throw new IllegalArgumentException("Frame too large");try{var i=new DataInputStream(new ByteArrayInputStream(data));if(i.readInt()!=0x554e0100)throw new IOException("Protocol mismatch");UUID id=new UUID(i.readLong(),i.readLong());String kind=i.readUTF();UUID actor=new UUID(i.readLong(),i.readLong());int n=i.readUnsignedShort();if(n>512)throw new IOException("Too many fields");var a=new ArrayList<String>();for(int k=0;k<n;k++)a.add(i.readUTF());if(i.available()!=0)throw new IOException("Trailing data");return new Wire(id,kind,actor,a);}catch(IOException e){throw new IllegalArgumentException("Malformed frame",e);}}
 public String at(int i){if(i<0||i>=args.size())throw new IllegalArgumentException("Missing field");return args.get(i);}
 public static boolean authentic(UUID claimed,UUID carrier,String source,String current){return claimed.equals(carrier)&&source!=null&&source.equals(current);}
}
