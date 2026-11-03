package shop.blinksmp.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Called only on the server thread for a current, authenticated Bedrock session. */
public final class PrivateMessages {
    private PrivateMessages() {}
    public static boolean dispatch(Player sender,String command) {
        String valid=PrivateMessagePolicy.validate(command);
        if(valid==null) return false;
        // Bukkit dispatch alone omits this event. Run it so mutes/command blockers can cancel.
        PlayerCommandPreprocessEvent event=new PlayerCommandPreprocessEvent(sender,"/"+valid);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled()) return false;
        String rewritten=event.getMessage();
        if(rewritten==null || !rewritten.startsWith("/")) return false;
        valid=PrivateMessagePolicy.validate(rewritten.substring(1));
        if(valid==null) return false;
        // The player remains the command sender, so the normal command permission checks apply.
        return Bukkit.dispatchCommand(sender,valid);
    }
}
