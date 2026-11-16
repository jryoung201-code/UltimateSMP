package shop.ultimatesmp.tempgrants;
import java.util.*;import java.util.regex.*;
record Grant(String kind,String target,long expiry){
 static final long MAX_DURATION=3650L*24*60*60*1000;
 static long duration(String s){Matcher m=Pattern.compile("([1-9][0-9]{0,9})([smhdw])",Pattern.CASE_INSENSITIVE).matcher(s);if(!m.matches())throw new IllegalArgumentException("Use a timer like 30m, 2h, 7d or 4w.");long factor=switch(m.group(2).toLowerCase(Locale.ROOT)){case "s"->1000L;case "m"->60000L;case "h"->3600000L;case "d"->86400000L;default->604800000L;};long n;try{n=Math.multiplyExact(Long.parseLong(m.group(1)),factor);}catch(ArithmeticException e){throw new IllegalArgumentException("Timer too large");}if(n>MAX_DURATION)throw new IllegalArgumentException("Max timer is 3650 days");return n;}
 static long extend(long current,long now,long duration){long result=Math.addExact(Math.max(current,now),duration);if(duration<1000||duration>MAX_DURATION||result-now>MAX_DURATION)throw new IllegalArgumentException("Total remaining duration cannot exceed 3650 days");return result;}
 static String kind(String s){return switch(s.toLowerCase(Locale.ROOT)){case "rank"->"rank";case "perm","permission"->"perm";default->throw new IllegalArgumentException("Use rank or perm");};}
 static void permission(String s){if(s==null||!s.matches("[a-zA-Z0-9_.:-]{1,128}"))throw new IllegalArgumentException("Use one exact permission node, without wildcards.");}
 String key(){return kind+"_"+Base64.getUrlEncoder().withoutPadding().encodeToString(target.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
