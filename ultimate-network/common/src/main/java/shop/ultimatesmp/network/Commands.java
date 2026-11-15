package shop.ultimatesmp.network;
import java.util.*;
public final class Commands {
 private Commands(){}public static final Set<String> STAFF=Set.of("warn","warnings","kick","mute","unmute","ban","tempban","unban","freeze","heal","feed","invsee");
 public static String canonical(String s){s=s.toLowerCase(Locale.ROOT);s=s.substring(s.lastIndexOf(':')+1);return switch(s){case "teleportrequest","tpa"->"tpr";case "teleportrequesthere","tpahere"->"tprhere";case "teleportrequestaccept","tpaccept"->"tpraccept";case "teleportrequestcancel","tpcancel"->"tprcancel";case "tpdeny","teleportrequestdeny"->"tprdeny";case "tell","w","whisper","pm","message"->"msg";case "r"->"reply";case "sc"->"staffchat";case "tphere"->"bring";case "trading"->"trade";default->s;};}
 public static int level(String s){return switch(s){case "OWNER"->7;case "DEV"->6;case "ADMIN"->5;case "MOD"->4;case "TRIAL_MOD"->3;case "HELPER"->2;default->0;};}
 public static String permission(String command){if(command.equals("maxhomes"))return "ultimatehomes.admin";if(command.startsWith("tpr"))return "ultimatetpr.use";if(command.equals("trade"))return "ultimatetrades.use";if(STAFF.contains(command)||Set.of("tp","bring","staffchat","rank").contains(command))return "ultimateranks.command."+command;return null;}
 public static boolean routed(String c){return c.startsWith("tpr")&&Set.of("tpr","tprhere","tpraccept","tprdeny","tprcancel").contains(c)||STAFF.contains(c)||Set.of("tp","bring","msg","reply","staffchat","trade","rank","maxhomes").contains(c);}
}
