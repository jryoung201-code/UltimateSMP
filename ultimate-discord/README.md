# UltimateSMP Discord giveaways

Install UltimateShop 1.2.0 on each Paper backend. Keep its existing config and use UltimateRanks 1.3.3 or newer and UltimateCrates 1.2.2 or newer. Restart Paper so UltimateShop creates the shared tables. Verify the crate mappings in UltimateShop and configure rewards in their matching crates.

Use a Discord application with a bot invited to your guild with the bot and applications.commands scopes. In giveaway channels, allow the bot to view channels, send messages, and read message history. Run one bot instance on a host that stays running.

Set these environment variables on that host.

| Variable | Value |
| --- | --- |
| DISCORD_TOKEN | Discord bot token |
| DISCORD_GUILD_ID | UltimateSMP Discord server ID |
| GIVEAWAY_DATABASE_URL | mysql:// username, password, hostname, and database path for the same MariaDB that UltimateShop uses |
| GIVEAWAY_DATABASE_CA | Database CA certificate if the server uses a private CA |

URL-encode special characters in the database username and password. Use TLS and restrict the bot database user's grants to its giveaway tables and the shop prize tables it writes. Never commit credentials.

Run `npm install` then `npm start` from this directory with Node.js 22 or newer.

Everyone can run Discord `/link` to get a private five-minute code, then run `/link <code>` in Minecraft. Each Discord account links to one Minecraft UUID and vice versa. Paper and other plugins may log commands, so restrict access to server logs and configure command logging to protect link arguments.

Staff with Manage Server run `/giveaway prize quantity minutes winners`. Players with a linked account enter using the button. For keys, quantity means keys per winner (1–64). For ranks, it means days of access (1–365) starting at the draw. Prizes and the draw result are saved in one database transaction. After a restart, the bot resumes outstanding draws and result announcements. Keys use UltimateShop's existing delivery queue and require a matching crate plus room in the player's inventory. Existing UltimateShop package blocks also apply to giveaway rank grants.
