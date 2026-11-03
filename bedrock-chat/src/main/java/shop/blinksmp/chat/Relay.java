package shop.blinksmp.chat;

import org.bukkit.command.CommandSender;

/** Boundary used by the bootstrap without loading PacketEvents classes. */
public interface Relay {
    void start();
    void stop();
    void status(CommandSender sender);
}
