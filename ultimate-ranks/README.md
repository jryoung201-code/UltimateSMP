# UltimateRanks 1.0.1

Standalone Paper 1.21.11 / Java 21 plugin for UltimateSMP. No LuckPerms or client
mod required. Java and Floodgate Bedrock players use the same rank system.
Prefixes appear in public chat and the tab list. Without UltimateSettings,
above-head nameplates are not modified. PLAYER UUIDs identify ranks and punishments; MEMBER is the default.

## Install and first OWNER

1. Stop the server. Upload UltimateRanks-1.0.1.jar into plugins/ and start it.
2. Join the server once so your exact name and UUID are recorded.
3. In the SERVER CONSOLE run `rank set YourExactName OWNER` (without `/`).
4. In game run `/rank info` and `/staff` to check your rank and available tools.

No username is hardcoded as staff. OP does not bypass this plugin's internal
hierarchy. Assigning OWNER always requires console, even if you already are OWNER.
Console can use a UUID before a player has joined; the stored display name updates
when that player joins. Bedrock names may include Floodgate's prefix, such as `.Name`.

## Rank hierarchy and staff access

| Rank | Included access (inherits lower staff access) |
| --- | --- |
| OWNER | Manage every lower rank, including DEV; same staff commands as DEV |
| DEV | Manage ADMIN and lower; same staff commands as ADMIN |
| ADMIN | Permanent bans, heal/feed, assign MOD and lower |
| MOD | Tempbans up to 7d, unban, bring, spectator toggle |
| TRIAL MOD | Kick, freeze, vanish, read-only invsee, teleport |
| HELPER | Warn, warning history, mute up to 10m, unmute, staff chat |
| MEMBER | View rank information; no staff access |

Trial Mod mutes are limited to 1h. Mod and higher mutes and Admin+ temporary bans
allow up to 365d. All moderation targets must have a strictly lower rank; you can
heal/feed yourself. Staff cannot reverse or replace a punishment from a higher
rank. Only console can reverse punishments issued by console. No permanent mute
command is provided.

## Commands

| Command | Purpose |
| --- | --- |
| `/rank list` | Show ranks |
| `/rank info [player]` | Show own or another known player's rank |
| `/rank set <player-or-uuid> <rank>` | Assign a lower rank |
| `/rank remove <player-or-uuid>` | Set rank back to MEMBER |
| `/staff` | List available staff commands |
| `/staff <command> ...` | Alternative entry point, e.g. `/staff mute Name 10m Spam` |
| `/sc <message>` | Staff-only chat (alias for `/staffchat`) |
| `/warn <player> [reason]` | Save and deliver a warning |
| `/warnings <player>` | Show latest 10 warnings; retain latest 100 per player |
| `/kick <player> [reason]` | Kick an online player |
| `/mute <player> <duration> [reason]` | Mute public chat and common message commands |
| `/unmute <player>` | Remove an UltimateRanks mute |
| `/tempban <player> <duration> [reason]` | Temporary UUID ban |
| `/ban <player> [reason]` | Permanent UUID ban |
| `/unban <player>` | Remove an UltimateRanks ban |
| `/freeze <player>` | Toggle a session freeze; same/higher issuer can undo it |
| `/vanish` or `/v` | Toggle hiding from members/helpers; Trial Mods+ can see staff |
| `/invsee <player>` | Open a cloned read-only inventory snapshot |
| `/tp <player>` | Teleport yourself to an online visible player |
| `/bring <player>` | Teleport a lower-rank player to you |
| `/gsp` | Toggle spectator, then restore previous mode |
| `/heal [player]` | Heal and refill food (defaults to self) |
| `/feed [player]` | Refill food (defaults to self) |

Duration examples: `30s`, `10m`, `2h`, `7d`, `1w`. No spaces or compound durations.
Offline moderation uses known names or explicit UUIDs, without external profile
lookups. Rank input accepts `TRIAL_MOD`, `TRIAL MOD`, `trial-mod`, and the `OWER`
spelling as an alias for OWNER.

If another plugin owns a short command, use `/staff <command> ...` or the explicit
namespace, e.g. `/ultimateranks:ban`. This plugin's bans live in its own database;
`/unban` does not remove a vanilla or other plugin's ban. It never dispatches
commands to other plugins when moderating.

## Persistence, configuration and integration

`plugins/UltimateRanks/players.yml` stores names, UUID ranks, punishments, expiry
times, issuing ranks and warning history. A save must succeed before authorization
changes take effect. Writes use a temporary file and atomic replacement when
supported. Back this file up. Do not edit it while the server is running.

Prefixes and exact-rank extra permission nodes are configured in config.yml;
restart after editing. Staff capabilities inside this plugin are fixed by the
rank hierarchy. Extra permissions are for other plugins and are NOT inherited.
OWNER, DEV and ADMIN receive `ultimateoptimize.admin` by default. No rank receives
OP or `*`. Vanilla OP powers and permissions granted by other plugins remain
independent; use ranks without OP for ordinary staff.

Freeze blocks movement, teleports, most commands, damage, building, interactions,
inventory actions and item pickup/drop. Chat remains available. Unfreeze a player
before bringing them. It is session-only and clears on disconnect/restart; leaving
while frozen is logged. It is not an anti-cheat or movement-packet filter.

Vanish is session-only and hides the player entity/tab entry through Paper's
visibility API. It suppresses public chat while active; use /sc. Building,
interactions, pickup/drop and dealing/taking damage are blocked while vanished.
It does not disguise total player counts or guarantee concealment in other
plugins' web maps, custom lists, proxy lists or messages. No fake join message is
sent when toggling. Spectator mode restores on toggle-off, demotion, logout and
plugin disable.

Inventory inspection is a snapshot, not live editing: shift-clicks, number keys,
drags and other inventory clicks are cancelled. Reopen to refresh.

Mute checks public chat and common message aliases including namespaced variants.
Custom plugin chat channels/aliases may need integration with their own moderation
system. Command blocks cannot issue staff actions. Console/RCON are trusted.

Runs event-driven with no repeating scans or network requests. Player data is
saved on first join/name change and staff changes; very large databases may need
a database backend. Chat and login checks use immutable in-memory snapshots.
Another chat/tab formatting or permission plugin may override presentation or
permissions; avoid two rank managers controlling the same server.

## Build and tests

Run `mvn -f ultimate-ranks/pom.xml package` from the repository with JDK 21.
The GitHub Actions ranks workflow compiles the plugin, runs authorization and
duration checks, and tests data persistence, UUID identity, punishment expiry,
corrupt-data rejection and failed-save behavior. The final JAR is an Actions
artifact named UltimateRanks-1.0.1. No live Minecraft/Bedrock client test has
been performed in this build environment.

## Personal rank tags with UltimateSettings

With UltimateSettings installed, chat rank prefixes respect each viewer's
Show Rank Tags preference. Settings manages per-viewer scoreboard prefixes for
nametags and the tab list when no custom scoreboard is active. It leaves custom
scoreboards and existing main-scoreboard teams/objectives alone. Those other
plugins control their own tab/nametag appearance. Without UltimateSettings, the
original global chat/tab prefixes continue to work. Rank data is unchanged.
