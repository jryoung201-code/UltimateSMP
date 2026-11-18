package shop.ultimatesmp.shop;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.security.SecureRandom;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.Permission;

final class PaperDiscordBot extends ListenerAdapter implements AutoCloseable {
    private static final String PREFIX = "giveaway:";
    private static final List<String> PRIZES = List.of("key-common", "key-uncommon", "key-rare",
            "key-legendary", "ultimate-plus", "ultimate-plus-plus", "ultimate-plus-plus-plus");
    private final java.util.logging.Logger logger;
    private final ShopStore store;
    private final String guildId;
    private final SecureRandom random = new SecureRandom();
    private final java.util.concurrent.ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "UltimateShop-Discord");
        thread.setDaemon(true);
        return thread;
    });
    private JDA jda;

    PaperDiscordBot(java.util.logging.Logger logger, ShopStore store, String guildId) {
        this.logger = logger;
        this.store = store;
        this.guildId = guildId;
    }

    void start(String token) {
        jda = JDABuilder.createLight(token, List.of()).addEventListeners(this).build();
    }

    @Override public void onReady(ReadyEvent event) {
        var guild = event.getJDA().getGuildById(guildId);
        if (guild == null) {
            logger.warning("Invite the giveaway bot to the configured Discord server, then restart Paper.");
            return;
        }
        guild.upsertCommand("link", "Get a private Minecraft linking code").queue(
                ignored -> {}, error -> report("register /link", error));
        var prize = new OptionData(OptionType.STRING, "prize", "Minecraft prize", true);
        PRIZES.forEach(id -> prize.addChoice(id, id));
        guild.upsertCommand(Commands.slash("giveaway", "Start a Minecraft prize giveaway")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOptions(prize,
                        new OptionData(OptionType.INTEGER, "quantity", "Keys per winner or days of rank access", true).setRequiredRange(1, 365),
                        new OptionData(OptionType.INTEGER, "minutes", "Time to enter", true).setRequiredRange(1, 43200),
                        new OptionData(OptionType.INTEGER, "winners", "Number of winners", true).setRequiredRange(1, 20)))
                .queue(ignored -> logger.info("UltimateSMP Discord giveaway commands registered."),
                        error -> report("register /giveaway", error));
        io.scheduleWithFixedDelay(() -> finishDue(event.getJDA()), 0, 10, TimeUnit.SECONDS);
    }

    @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || !guildId.equals(event.getGuild().getId())) return;
        switch (event.getName()) {
            case "link" -> event.deferReply(true).queue(
                    hook -> reply(hook, () -> link(event.getUser().getId())), error -> report("acknowledge /link", error));
            case "giveaway" -> {
                if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
                    event.reply("Manage Server permission required.").setEphemeral(true).queue();
                    return;
                }
                event.deferReply(true).queue(hook -> reply(hook, () -> startGiveaway(event)),
                        error -> report("acknowledge /giveaway", error));
            }
            default -> { }
        }
    }

    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getGuild() == null || !guildId.equals(event.getGuild().getId())
                || !event.getComponentId().startsWith(PREFIX)) return;
        event.deferReply(true).queue(hook -> reply(hook, () -> enter(event)), error -> report("acknowledge entry", error));
    }

    private void reply(InteractionHook hook, Work<String> work) {
        if (io.isShutdown()) return;
        io.execute(() -> {
            try {
                hook.editOriginal(work.run()).setAllowedMentions(Set.of()).queue(
                        ignored -> {}, error -> report("reply", error));
            } catch (Exception error) {
                report("handle command", error);
                hook.editOriginal("Temporarily unavailable. Try again shortly.").queue(
                        ignored -> {}, failure -> report("reply", failure));
            }
        });
    }

    private String link(String discordId) throws Exception {
        try (var connection = store.connect()) {
            if (linked(connection, discordId)) return "Your Discord account is already linked.";
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            String code = HexFormat.of().formatHex(bytes);
            ShopStore.update(connection, """
                    INSERT INTO ultimate_discord_codes(discord_id,token_hash,expires) VALUES(?,?,?)
                    ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires=VALUES(expires)
                    """, discordId, ShopStore.hash(code), System.currentTimeMillis() + 300_000);
            return "Enter `/link " + code + "` in Minecraft within five minutes. Keep this code private.";
        }
    }

    private boolean linked(Connection connection, String discordId) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM ultimate_discord_links WHERE discord_id=?")) {
            query.setString(1, discordId);
            try (var rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    private String startGiveaway(SlashCommandInteractionEvent event) throws Exception {
        String prize = event.getOption("prize").getAsString();
        int quantity = event.getOption("quantity").getAsInt();
        int winners = event.getOption("winners").getAsInt();
        int minutes = event.getOption("minutes").getAsInt();
        if (!PRIZES.contains(prize) || quantity < 1 || quantity > (isKey(prize) ? 64 : 365)
                || winners < 1 || winners > 20 || minutes < 1 || minutes > 43200) return "Invalid giveaway options.";
        String id = UUID.randomUUID().toString();
        long ends = System.currentTimeMillis() + minutes * 60_000L;
        var message = event.getChannel().sendMessage("**UltimateSMP giveaway**\n" + describe(prize, quantity)
                + " per winner · " + winners + " winner(s)\nEnds <t:" + ends / 1000
                + ":R>. Link your Minecraft account with /link to enter.")
                .setActionRow(Button.primary(PREFIX + id, "Enter giveaway")).complete();
        try (var connection = store.connect()) {
            ShopStore.update(connection, """
                    INSERT INTO ultimate_discord_giveaways(id,guild_id,channel_id,message_id,prize,quantity,winners,ends)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, id, guildId, event.getChannel().getId(), message.getId(), prize, quantity, winners, ends);
        } catch (Exception error) {
            message.delete().queue(ignored -> {}, failure -> report("remove unsaved giveaway", failure));
            throw error;
        }
        return "Giveaway started.";
    }

    private String enter(ButtonInteractionEvent event) throws Exception {
        String id = event.getComponentId().substring(PREFIX.length());
        return transaction(connection -> {
            try (var query = connection.prepareStatement("""
                    SELECT ends,result FROM ultimate_discord_giveaways
                    WHERE id=? AND guild_id=? AND message_id=? FOR UPDATE
                    """)) {
                query.setString(1, id);
                query.setString(2, guildId);
                query.setString(3, event.getMessageId());
                try (var rows = query.executeQuery()) {
                    if (!rows.next() || rows.getString("result") != null || rows.getLong("ends") <= System.currentTimeMillis()) {
                        return "This giveaway is closed.";
                    }
                }
            }
            String discordId = event.getUser().getId();
            if (!linked(connection, discordId)) return "Link your Minecraft account with /link first.";
            int inserted = ShopStore.update(connection, """
                    INSERT IGNORE INTO ultimate_discord_entries(giveaway_id,discord_id) VALUES(?,?)
                    """, id, discordId);
            return inserted == 1 ? "You entered the giveaway." : "You already entered this giveaway.";
        });
    }

    private void finishDue(JDA discord) {
        try {
            List<String> due = new ArrayList<>();
            try (var connection = store.connect(); var query = connection.prepareStatement("""
                    SELECT id FROM ultimate_discord_giveaways
                    WHERE guild_id=? AND announced=0 AND ends<=? ORDER BY ends LIMIT 25
                    """)) {
                query.setString(1, guildId);
                query.setLong(2, System.currentTimeMillis());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) due.add(rows.getString(1));
                }
            }
            for (String id : due) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    Result result = settle(id);
                    if (result != null) announce(discord, id, result);
                } catch (Exception error) {
                    report("finish giveaway", error);
                }
            }
        } catch (Exception error) {
            report("check giveaways", error);
        }
    }

    private Result settle(String id) throws Exception {
        return transaction(connection -> {
            String prize, channel, message, result;
            int quantity, count;
            try (var query = connection.prepareStatement("""
                    SELECT * FROM ultimate_discord_giveaways WHERE id=? AND guild_id=? FOR UPDATE
                    """)) {
                query.setString(1, id);
                query.setString(2, guildId);
                try (var rows = query.executeQuery()) {
                    if (!rows.next() || rows.getBoolean("announced") || rows.getLong("ends") > System.currentTimeMillis()) return null;
                    prize = rows.getString("prize");
                    channel = rows.getString("channel_id");
                    message = rows.getString("message_id");
                    quantity = rows.getInt("quantity");
                    count = rows.getInt("winners");
                    result = rows.getString("result");
                }
            }
            if (result != null) return new Result(channel, message, prize, quantity, result);
            List<Entrant> entries = new ArrayList<>();
            try (var query = connection.prepareStatement("""
                    SELECT e.discord_id,l.player_id FROM ultimate_discord_entries e
                    JOIN ultimate_discord_links l ON e.discord_id=l.discord_id WHERE e.giveaway_id=?
                    """)) {
                query.setString(1, id);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) entries.add(new Entrant(rows.getString(1), rows.getString(2)));
                }
            }
            List<String> winners = new ArrayList<>();
            long now = System.currentTimeMillis();
            while (winners.size() < count && !entries.isEmpty()) {
                Entrant winner = entries.remove(random.nextInt(entries.size()));
                String source = PREFIX + id + ":" + winner.discord();
                if (isKey(prize)) {
                    ShopStore.update(connection, """
                            INSERT INTO ultimate_shop_key_deliveries(id,source,player_id,package_id,amount,state,updated_at)
                            VALUES(?,?,?,?,?,'pending',?)
                            """, UUID.randomUUID().toString(), source, winner.player(), prize, quantity, now);
                } else {
                    ShopStore.update(connection, """
                            INSERT INTO ultimate_shop_grants(source,player_id,package_id,expires) VALUES(?,?,?,?)
                            """, source, winner.player(), prize, now + quantity * 86_400_000L);
                }
                winners.add("\"" + winner.discord() + "\"");
            }
            result = "[" + String.join(",", winners) + "]";
            ShopStore.update(connection, "UPDATE ultimate_discord_giveaways SET result=? WHERE id=?", result, id);
            return new Result(channel, message, prize, quantity, result);
        });
    }

    private void announce(JDA discord, String id, Result result) throws Exception {
        var channel = discord.getChannelById(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel.class, result.channel());
        if (channel == null) {
            // A temporarily inaccessible channel can become available again.
            logger.warning("Giveaway prizes saved; result channel is unavailable.");
            return;
        }
        List<String> mentions = new ArrayList<>();
        var ids = java.util.regex.Pattern.compile("\"([0-9]{17,20})\"").matcher(result.winners());
        while (ids.find()) mentions.add("<@" + ids.group(1) + ">");
        String outcome = mentions.isEmpty() ? "No eligible entries." : "Winners: " + String.join(", ", mentions)
                + (isKey(result.prize()) ? "\nKeys arrive when you join with inventory space." : "\nRank access starts at the draw.");
        try {
            channel.editMessageById(result.message(), "**UltimateSMP giveaway ended**\n"
                    + describe(result.prize(), result.quantity()) + "\n" + outcome)
                    .setComponents(List.of()).setAllowedMentions(Set.of()).complete();
        } catch (ErrorResponseException error) {
            if (error.getErrorCode() != 10008) throw error;
            logger.warning("Giveaway prizes saved; the Discord post was deleted.");
        }
        try (var connection = store.connect()) {
            ShopStore.update(connection, "UPDATE ultimate_discord_giveaways SET announced=1 WHERE id=?", id);
        }
    }

    private <T> T transaction(Transaction<T> operation) throws Exception {
        try (var connection = store.connect()) {
            connection.setAutoCommit(false);
            try {
                T result = operation.run(connection);
                connection.commit();
                return result;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private static boolean isKey(String prize) { return prize.startsWith("key-"); }
    private static String describe(String prize, int quantity) {
        return isKey(prize) ? quantity + " × " + prize.substring(4) + " crate key(s)"
                : prize + " for " + quantity + " day(s)";
    }

    private void report(String operation, Throwable error) {
        String code = error instanceof SQLException sql ? ", SQL code " + sql.getErrorCode()
                : error instanceof ErrorResponseException discord ? ", Discord code " + discord.getErrorCode() : "";
        logger.warning("Discord bot could not " + operation + " (" + error.getClass().getSimpleName() + code + ").");
    }

    @Override public void close() {
        if (jda != null) jda.shutdownNow();
        io.shutdownNow();
    }

    private record Entrant(String discord, String player) {}
    private record Result(String channel, String message, String prize, int quantity, String winners) {}
    private interface Work<T> { T run() throws Exception; }
    private interface Transaction<T> { T run(Connection connection) throws Exception; }
}
