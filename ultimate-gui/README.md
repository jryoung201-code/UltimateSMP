# UltimateGUI 1.0.1

Personal in-game admin panel for RealJR_25736, on Paper 1.21.11 / Java 21.
Requires the official PacketEvents Spigot plugin, version 2.14.0. Existing
UltimateBedrockChat already uses the same dependency; do not add a duplicate JAR.

## Install and preconfigured owner

UltimateBedrockChat 1.0.1 automatically installs this panel, or stages an older
installed copy through Paper's update folder. Restart once after an installation
or staged update is reported. You can also manually replace the old GUI JAR with
UltimateGUI-1.0.1.jar while the server is stopped.

The sole owner is hardcoded to UUID `bd33980d-5b8d-4f1c-becc-0520f92c4a0d`.
Open `/ugui` or `/ultimategui` using that account. No console bind is required;
OP, ranks, names and edited owner.yml UUIDs cannot override the access check.
Console can display the configured UUID but cannot rebind/open the panel.
Existing owner.yml vanish state is retained only if its UUID matches this owner;
an older different/unbound owner state is updated with vanish off.

Identity is only as strong as the server's authentication: offline-mode servers
can allow username/derived-UUID impersonation. Use authenticated Java login or a
properly secured/authenticated proxy to make "only this account" meaningful. This
plugin does not change online-mode or your server's authentication configuration.

## Panel

- Survival, Creative, Adventure and Spectator buttons switch directly through the
  Paper API, without dispatching `/gamemode` or broadcasting vanilla OP feedback.
- Vanish toggles this plugin's own privacy mode and persists its setting for
  future joins/restarts. Feedback is sent only to the owner.
- Close button. The inventory is read-only; all clicks/drags are cancelled and
  actions run next tick outside the inventory transaction, rechecking the UUID.

## Vanish coverage

- Hide the owner entity and TAB entry from ALL other online players, including
  staff and OPs. New viewers also receive the hide request. There is no see-vanish
  permission bypass in this plugin.
- Suppress public join/quit announcements for the owner while hidden. This runs
  before UltimateSettings distributes its per-viewer join/leave notices.
- Remove the owner's name/UUID from the Paper Java server-list player sample and
  subtract the hidden online player from the displayed count, clamping to zero.
- Filter normal synchronous and asynchronous completion results containing the
  hidden name, including console completions. Paper's player visibility also
  removes ordinary client-side player-name completions.
- Hide the panel command from other players' command trees. Unauthorized manual
  requests are denied even if the sender is OP.
- Filter ordinary `/list`, `minecraft:list` and `bukkit:list` output for other
  players and console/RCON, while preserving their usual list permission checks.
- Disable collision, natural-spawn participation and ordinary entity sounds while
  hidden; restore the captured values on exit. Mark the player with this plugin's
  `vanished` Bukkit metadata for integrations that honor it.
- Block public chat, damage dealt/taken, new mob targeting, block actions,
  interactions and item pickup/drop while hidden. Use /sc for staff chat if your
  rank permits it. Gamemode changes remain available through the panel.

Other plugins' independent vanish settings are not cleared when you turn this
one off. Disable the previous rank plugin's vanish first if you want one toggle
to describe all active hiding. Player hide/show calls are scoped to this plugin;
they cannot unhide someone hidden by another plugin.

## Quiet command scope and logging limits

PacketEvents intercepts ONLY the bound, online owner's GUI command routes
(`/ultimategui`, `/ugui`, and this plugin's namespaced equivalents) before vanilla
command dispatch/logging. It opens the panel directly. Every other command stays
on the normal execution, permission and logging path. It is not a general silent
command executor. Root commands have no signed chat arguments.

Panel gamemode/vanish actions use APIs and do not call commands, send OP broadcasts
or write routine action messages to this plugin's logger. Startup/errors remain visible. It does not erase logs or install global logging
filters. Standard Minecraft connection/disconnection lines can still be logged.
Host consoles, packet loggers, third-party audit plugins, web maps, management
APIs, plugin-specific player lists, cached completions, sounds/events generated
elsewhere, proxy counts and Geyser's independent Bedrock ping are outside these
controls. This is not a promise of invisibility to server operators, the host or
software with direct access to player state. Other event handlers can also
override cosmetic counts or outputs. Server internals still know the owner is
online so gameplay and saving continue to work.

## Verification

Run `mvn -f ultimate-gui/pom.xml package` with JDK 21. The workflow compiles and
runs UUID access, narrow command matching, completion filtering and player-count
checks. The delivered JAR has Java 21 bytecode and validated ZIP integrity.
Live menu interaction, packet interception/logging behavior and vanish on both
Java and Bedrock still need testing on the actual server; no live server/client
is available in this build environment.

Official PacketEvents dependency:
https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar
