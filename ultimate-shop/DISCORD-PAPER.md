# Run the UltimateSMP Discord bot on Paper

Replace UltimateShop with 1.3.0 on your Paper backends and keep the existing config. This build uses the same shared MariaDB and requires UltimateRanks 1.3.3 or newer. Install UltimateCrates 1.2.2 or newer for key delivery. Keep the existing configured key packages and crates.

On **one** backend, add this block to `plugins/UltimateShop/config.yml` and fully restart Paper.

```yaml
discord:
  enabled: true
  guild-id: '1525992907683921980'
  token: ''
```

Set `DISCORD_TOKEN` in the Paper process environment if your host supports it. Otherwise set `discord.token` in that backend's private config. `DISCORD_GUILD_ID` can override the configured ID. Keep the token out of public logs and backups shared with others.

Invite the bot to the guild with the bot and applications.commands scopes. Its role needs View Channel, Send Messages, and Read Message History in the giveaway channel. Turn off the separate Node bot if you previously ran it. No inbound HTTP port is required.

Run Discord `/link`, then Minecraft `/link <code>`. Minecraft hides the command from tab completion. Restrict command logs because other plugins and Paper may log the code before it is consumed. Staff with Manage Server can run Discord `/giveaway`. Discord commands are unavailable while the bot backend is offline. Overdue draws resume after restart.
