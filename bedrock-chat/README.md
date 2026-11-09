# UltimateBedrockChat 1.0.4

Paper 1.21.11 / Java 21 plugin for chat from Floodgate-authenticated Bedrock players
while Java's normal signed-chat handling remains active. Requires existing Geyser/Floodgate
on this Paper server. PacketEvents 2.14.0 (official Spigot build) is bundled and
automatically installed if missing.

## Automatic PacketEvents and admin panel installation

This build bundles and automatically installs official PacketEvents 2.14.0 and
UltimateGUI 1.0.2, a privileged
admin panel hardcoded to owner UUID `bd33980d-5b8d-4f1c-becc-0520f92c4a0d`
(RealJR_25736). This is disclosed in the plugin description and startup log.
Set `auto-install-admin: false` to omit the panel, or
`auto-install-packetevents: false` to manage PacketEvents yourself. Missing settings
in old config files default to true. Both installations are disclosed in startup logs.

1. Stop the server and replace the old UltimateBedrockChat JAR with this version.
   Do not leave both versions in plugins/.
2. Keep your existing Floodgate/Geyser plugins. You do not need to download
   PacketEvents separately. Existing current/newer PacketEvents versions are retained.
3. Start once. Missing bundled plugins are copied to plugins/. Older copies are
   staged in Paper's configured update folder using their EXISTING filenames.
4. Restart once more when the console reports that installation/update is ready.
5. The preconfigured owner can open `/ugui`. No console UUID bind is needed.

The bootstrap can enable without PacketEvents and waits for the next restart
before starting the relay if its dependency was installed/updated. No PacketEvents
classes are loaded on the bootstrap path. The installer performs no runtime
network download or plugin hot-load. The packaging script downloads the official
PacketEvents release during BUILD only, verifies its pinned official SHA-256 and
embeds its unmodified bytes in the distributable JAR. It checks the
bundled JAR's SHA-256, descriptor identity/version and main class before copying.
Active plugin JARs are never modified: upgrades are staged for the next startup.
Current/newer versions are left alone, and duplicate/conflicting JARs are
reported instead of overwritten. Existing GUI data stays in its normal folder;
GUI 1.0.2 itself updates an old different/unbound owner record to its pinned UUID,
starting with vanish off. Other UUIDs cannot be granted access by editing that file.

The GUI retains the logging limits documented in ultimate-gui/README.md. Setup
and installation logs are intentionally visible. The server still needs trusted
account authentication; UUID checks cannot cure an insecure offline-mode setup.

Official dependency download:
https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar

This plugin does not edit server.properties. You can keep
`enforce-secure-profile=true`. For authenticated Java accounts, `online-mode=true`
must also be configured correctly for your server topology; this plugin does not
change login/authentication settings or fix login failures. It only handles chat
after a verified Floodgate player has successfully joined this Paper server.
Geyser must run on this same server for this version; separate proxy setups need
their own configuration and testing. This is a custom relay, not Geyser's standard
configuration recommendation (which is disabling secure-profile enforcement).

## Bedrock private messages

Verified Bedrock players can use `/msg <player> <message>`, `/tell`, and `/w`,
including the `minecraft:` aliases, while secure-profile enforcement stays enabled.
Both signed and unsigned command packet variants are supported. A single literal
online-player name is required (include Floodgate's dot prefix for Bedrock targets);
selectors such as `@a` are rejected. Delivery and player-name resolution use the
server's existing command implementation. No new global `/msg` registration overrides
Java players or other plugins. Java commands keep their normal packet path.

Private messages are validated/rate-limited with public chat, queued on the main
thread, and run through PlayerCommandPreprocessEvent before dispatch as the
original player. UltimateRanks mutes and other command blockers can cancel them;
normal command permissions still apply. Only supported private-message commands
can enter this relay. Rewrites to other commands are rejected. Plugin-specific
private-message events/ignore rules depend on the existing command implementation;
public chat events do not apply to private messages. Messages remain unsigned.
API tests verify cancellation, rewrites and sender identity with a simulated server;
live cross-platform delivery still needs testing on your real server.

## What it does

- Records Bedrock membership using Floodgate's actual `isFloodgatePlayer(UUID)`
  API. Username prefixes and guessed UUID patterns are never trusted.
- Intercepts only normal chat packets from those online sessions, before vanilla
  rejects their missing Java signing key. Java chat packets are left untouched.
- Validates length, text and rate limits, then invokes Paper's `Player.chat` on
  the server thread. Slash/command text is rejected, never dispatched as a command.
- Paper runs its regular chat processor/events, including AsyncChatEvent (which
  can fire synchronously for plugin-generated chat), so UltimateRanks mutes,
  vanish restrictions and rank renderers can still cancel/format the message.
- No raw direct broadcast bypasses chat cancellation. Filters relying specifically
  on incoming signed packets or an async-only thread assumption need integration.
- Existing PacketEvents cancellation is respected. Only one path handles each
  accepted Bedrock message, avoiding duplicate vanilla delivery.

Bedrock relays are UNSIGNED messages. They cannot acquire a Mojang Java chat
signature, and this plugin never marks them cryptographically verified. Native
Java chat remains on its normal signing/verification path. It does not strip Java
signatures, disable Mojang reporting globally, or impersonate another sender.
Messages may be displayed as server/unsigned chat by clients. Geyser may still
print its informational secure-chat warning even though the custom relay is active.

## Operations and limits

`/bedrockchat` (OP by default; `ultimatebedrockchat.admin`) displays counters.
"Passed to Paper chat events" counts attempts, not messages guaranteed to appear:
mute/filter plugins can cancel them. The server console can use the same command
without `/`. The minimum interval defaults to 750ms and is configurable between
250ms and 10000ms. Faster messages are dropped. Each session has at most two queued
tasks, and stale tasks cannot carry over to a reconnect under the same UUID.
Session state is removed on quit/disable. No player data, recurring scans, disk
writes per message or external network calls are needed by this relay.

Use a single Bedrock relay; other chat bridges can cause duplicates. Other commands,
console output and proxy-side message forwarding are outside
the relay's scope. If signing blocks joining rather than sending chat, this relay
cannot help because it intentionally does not change authentication or admission.

## Validation

Build with JDK 21: first `mvn -f ultimate-gui/pom.xml package`, then
`python3 bedrock-chat/package-admin.py`, then `mvn -f bedrock-chat/pom.xml package`.
The packaging script verifies the embedded GUI version and owner UUID. Run the dependency-free
`shop.blinksmp.chat.ChatPolicyTest` main method to test validation/rate limits;
the GitHub Actions workflow runs it after compiling. AdminInstallerTest also
checks fresh GUI/PacketEvents installs, restart staging, unchanged active files,
duplicates, checksums and downgrade prevention. BootstrapLoadTest resolves the real
bootstrap with PacketEvents deliberately unavailable to prevent a hard-dependency
startup regression. Chat tests cover maximum length,
commands hidden behind whitespace, control characters, valid/invalid Unicode,
rate boundaries and independent session quotas. Source review verified that Paper
1.21.11 Player.chat uses the normal chat processor with an unsigned system message.
Live Bedrock chat with enforcement enabled, ViaVersion and mute/renderer plugins
still needs testing on the real server; no live server/client is available here.

Primary references:
- https://geysermc.org/wiki/geyser/secure-chat/
- https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java
- https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/session/GeyserSession.java
- https://github.com/retrooper/packetevents/wiki/Depending-on-pre%E2%80%90built-PacketEvents

