# UltimateSettings 1.0.0

Paper 1.21.11 / Java 21. Every player can use `/settings` (aliases `/prefs` and
`/preferences`) without OP or a staff rank. Java inventory menus work through
Geyser for Bedrock players. No resource pack or client mod is required.

## Install

1. Stop the server.
2. Put UltimateSettings-1.0.0.jar in plugins/.
3. Replace UltimateRanks-1.0.0.jar with the included UltimateRanks-1.0.1.jar.
   Keep plugins/UltimateRanks/ and its players.yml. Do not leave both rank JARs.
4. Start the server and run `/settings` in game.

The settings plugin also works alone; personal rank display integration requires
UltimateRanks 1.0.1. Rank data and punishments keep the same file format.
UltimateOptimize can remain installed unchanged.

## Menu choices

| Setting | Default | Effect |
| --- | --- | --- |
| Bedrock Booster | OFF | Caps terrain chunks sent to this player at 4 (or a lower world/existing cap) |
| Show Rank Tags | ON | Per-viewer rank prefixes in chat and supported tab/nametag scoreboards |
| Show Other Players | ON | Show/hide other player entities and tab entries on your screen |
| Allow Direct Messages | ON | Allows incoming common `/msg`, `/tell`, `/w` and similar direct message commands |
| Join / Leave Messages | ON | Shows ordinary join/leave chat announcements |
| Coordinates | OFF | Shows your XYZ above the hotbar once per second |
| Reset to Defaults | — | Restores and saves all six default choices |

Booster is available to Java players too. It sends less terrain, which may help
Geyser translation, bandwidth and low-end client chunk/render load. It does not
change internet ping, tick rate, graphics settings, particles or farm simulation
distance. It will not fix protocol errors, poor Wi-Fi or an overloaded host.
The tradeoff is a shorter terrain view; enabling/disabling can cause normal chunk
reloads. It does not issue manual chunk unloads or force garbage collection.

Players inherit current world distances again when disabling Booster if their
starting cap matched that world, or recover their lower explicit starting cap.
Inherited-vs-explicit equal caps cannot be distinguished through the public API.
Avoid another plugin managing per-player sending distances while Booster is on.
Global world-distance changes from UltimateOptimize are respected, checked every
five seconds and on world changes. Restoring inherited distances uses Paper's -1
default; no server.properties/world configuration is modified.

Rank visibility is a VISUAL preference; it does not remove anyone's actual rank
or permissions. Updated UltimateRanks renders chat for each viewer. Tab prefixes
and nametag prefixes use personal scoreboards. Existing custom scoreboards or
main-scoreboard teams/objectives are preserved, so their tab/nametag displays are
controlled by the other plugin. With `manage-rank-scoreboards: false`, this plugin
only controls chat rank prefixes; UltimateRanks keeps its global tab prefixes.
No sidebar/objectives are created. Rank teams keep friendly fire enabled and
friendly-invisibility visibility disabled to preserve normal potion behavior.

Show Other Players affects only the selecting player. It can hide PvP opponents;
players should understand that tradeoff. Showing players again only releases this
plugin's own visibility requests and does not undo staff vanish from UltimateRanks.

Direct-message controls intercept common direct-target commands, including
namespaced variants. Exact player names are required; selectors are rejected for
these commands. `/reply`, custom plugin channels, offline mail and console messages
are outside this generic bridge and need integration with their owning plugin.
Staff can still communicate using warnings. The coordinate action bar can replace
another plugin's action bar while enabled; it is opt-in.

Join/leave notices preserve the message component from the normal event, send it
to console and eligible viewers, and suppress the default broadcast. Null/silent
messages, hidden players and UltimateRanks vanish quits are respected. Other
plugins broadcasting their own announcements remain independent.

## Persistence and commands

Choices save by UUID to plugins/UltimateSettings/players.yml and survive restart.
Other players' settings are independent. Writes must succeed before choices
apply, use a temporary file and atomic replacement when supported, and are limited
to deliberate changes. No repeating disk writes, entity/chunk scans or network
requests occur. A lightweight shared one-second task handles opt-in coordinates;
Booster cap checks run every five seconds for enabled players only.

- `/settings`: open menu.
- `/settings toggle booster`: toggle reduced chunk sending.
- `/settings toggle rank-tags`: toggle your rank display preference.
- `/settings toggle players`, `messages`, `join-messages`, `coordinates`: other toggles.
- `/settings reset`: reset all defaults.

Permission `ultimatesettings.use` defaults to true. Toggle/reset actions have a
one-second per-player cooldown. Menu clicks and drags are cancelled so items cannot
be taken; inventory replacement happens on the next tick outside click handling.
Restart after editing config.yml. Do not edit players.yml while the server runs.

## Build and verification

Run `mvn -f ultimate-settings/pom.xml package` with JDK 21. The settings workflow
builds the JAR and tests defaults, all setting toggles, per-UUID isolation,
distance caps, persistence/reset, malformed data and failed saves. The ranks
workflow builds UltimateRanks 1.0.1 and checks authorization/punishment storage.
No live Paper server or Bedrock client is available in this build environment;
menu interaction, Geyser rendering and scoreboard integration still need an
in-game check.
