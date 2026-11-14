# Ultimate donor rank naming

Ultimate+, Ultimate++ and Ultimate+++ replace the old Blink donor names. Existing player assignments, custom weights, permissions and prefix formatting migrate automatically. Old Blink names remain accepted as command/data aliases; new saves use ULTIMATE_PLUS identifiers. Old `ultimateranks.rank.blink_plus` permission nodes remain aliases for integrations. Upgrade UltimateRanks on every Paper backend together. UltimateHomes 1.1.0 and UltimateSettings 1.1.0 remain compatible and do not need another update.

# UltimateRanks 1.2.1

A player can hold multiple ranks. Existing `rank` entries migrate automatically to a `ranks` list on the next save. Names, UUIDs and sanctions are preserved.

- `/rank add <player-or-uuid> <rank>` adds a membership.
- `/rank remove <player-or-uuid> <rank>` removes only that membership. Removing the last rank restores MEMBER.
- `/rank set <player-or-uuid> <rank>` replaces all memberships (legacy behavior).
- `/rank remove <player-or-uuid>` resets all memberships to MEMBER (legacy behavior).
- `/rank info [player]` lists every rank and the visible rank; `/rank list` shows weights.

`weights` in `plugins/UltimateRanks/config.yml` determines the single tag used in chat and TAB. Higher weights appear first in TAB. Equal display weights use stable enum order; equal player weights sort by name then UUID. Defaults are OWNER 600, DEV 500, ADMIN 400, MOD 300, TRIAL_MOD 200, HELPER 100, Ultimate+++ 30, Ultimate++ 20, Ultimate+ 10, MEMBER 0. Existing configs gain these defaults while retaining custom settings. Restart after changes.

Permissions from every assigned rank combine. Ultimate+ grants `maxhomes.3`, Ultimate++ grants `maxhomes.5`, Ultimate+++ grants `maxhomes.9`. OWNER grants `maxhomes.unlimited`, independently of which tag is visible. ADMIN and higher get `ultimatehomes.admin` for `/maxhomes`.

Display weights never change the fixed staff authority hierarchy. Rank management still requires ADMIN or higher and can only affect players and ranks below the actor; only console can assign OWNER. No automatic staff assignment or OP.

With BlinkNetwork profile sync enabled, all memberships are exported in the existing ranks module; sanctions remain separate. Upgrade UltimateRanks on every Paper backend together to avoid an old single-rank build flattening memberships. UltimateHomes 1.1.0 is required for unlimited homes. Install these on Paper, not Velocity. If using UltimateSettings for personal rank tags, update it to 1.1.0 too; older versions group donor ranks with MEMBER.

# UltimateRanks 1.0.3

Standalone Paper 1.21.11 / Java 21 plugin for UltimateSMP. No LuckPerms or client
mod required. Java and Floodgate Bedrock players use the same rank system.
Prefixes appear in public chat and the tab list. Without UltimateSettings,
above-head nameplates are not modified. PLAYER UUIDs identify ranks and punishments; MEMBER is the default.

## Install and first OWNER

1. Stop the server. Upload UltimateRanks-1.0.3.jar into plugins/ and start it.
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

## Blink player ranks and home limits

| Rank | Built-in permission | Maximum homes |
| --- | --- | ---: |
| Ultimate+ | `maxhomes.3` | 3 |
| Ultimate++ | `maxhomes.5` | 5 |
| Ultimate+++ | `maxhomes.9` | 9 |

Add alongside existing ranks with `/rank add PlayerName Ultimate+`, `/rank add PlayerName Ultimate++`, or
`/rank add PlayerName Ultimate+++`. Console uses the same commands without `/`.
ADMIN and higher can assign these ranks to lower-ranked players. Names with
pluses are accepted case-insensitively, appear in chat/TAB, and autocomplete.
The stored names are `ULTIMATE_PLUS`, `ULTIMATE_PLUS_PLUS`, `ULTIMATE_PLUS_PLUS_PLUS`;
these are also the optional prefix/extra-permission config keys.

These player ranks have the same moderation level as MEMBER and grant no staff
commands, staff chat, or OP. Use `/rank add` to retain staff memberships;
`/rank set` intentionally replaces all memberships.

UltimateHomes (or another plugin that reads `maxhomes.#`) must be installed to
provide home commands. Its highest true numeric permission wins, so a higher
permission granted elsewhere can increase these limits. Changing/removing a rank
refreshes this plugin's permission attachment immediately; a downgrade keeps
existing homes but blocks extra new homes according to UltimateHomes' rules.

Upgrade by stopping the server, replacing the old UltimateRanks JAR with
UltimateRanks-1.0.3.jar, then restarting. Keep `players.yml` and `config.yml`.
The built-in home grants and prefix fallbacks work with an existing config;
no data reset, LuckPerms, or UltimateHomes update is required.

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
| `/vanish` or `/v` | Toggle hiding from ALL other players, including staff and OPs |
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
rank hierarchy. Extra permissions are for other plugins and combine across all assigned ranks.
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
It removes locally vanished players from the Paper Java ping count/sample and
filters normal tab completions. It cannot guarantee concealment in independent
plugins' web maps, custom lists, proxy lists or messages. With UltimateGUI 1.0.3,
the configured owner's /vanish delegates to the GUI's single persisted state;
other staff keep session-only rank vanish. Update both plugins together. No fake join message is
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
artifact named UltimateRanks-1.0.3. No live Minecraft/Bedrock client test has
been performed in this build environment.

## Personal rank tags with UltimateSettings

With UltimateSettings installed, chat rank prefixes respect each viewer's
Show Rank Tags preference. Settings manages per-viewer scoreboard prefixes for
nametags and the tab list when no custom scoreboard is active. It leaves custom
scoreboards and existing main-scoreboard teams/objectives alone. Those other
plugins control their own tab/nametag appearance. Without UltimateSettings, the
original global chat/tab prefixes continue to work. Rank data is unchanged.


