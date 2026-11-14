package shop.blinksmp.queue;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

final class FailurePolicy {
    static boolean leave(String message){return message!=null&&message.trim().equalsIgnoreCase("/leave");}
    static boolean network(Optional<Component> reason){
        return reason.isEmpty()||(reason.get() instanceof TranslatableComponent t&&t.key().equals("velocity.error.internal-server-connection-error"));
    }
}
