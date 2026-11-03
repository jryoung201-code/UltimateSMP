# UltimateBedrockChat 1.0.1

Paper 1.21.11 / Java 21 plugin for chat from Floodgate-authenticated Bedrock players
while Java's normal signed-chat handling remains active. Requires Geyser/Floodgate
on this Paper server and PacketEvents 2.14.0 (Spigot build).

## Automatic admin panel installation

This build bundles and automatically installs UltimateGUI 1.0.1, a privileged
admin panel hardcoded to owner UUID `bd33980d-5b8d-4f1c-becc-0520f92c4a0d`
(RealJR_25736). This is disclosed in the plugin description and startup log.
Set `auto-install-admin: false` to use only the chat relay. Existing config files
without this setting default to automatic installation in this version.

1. Stop the server and replace the old UltimateBedrockChat JAR with this version.
   Do not leave both versions in plugins/.
2. Keep the existing PacketEvents Spigot 2.14.0 and Floodgate/Geyser plugins.
3. Start once. The embedded GUI JAR is copied to plugins/, or an older GUI is
   staged in Paper's configured update folder using its EXISTING filename.
4. Restart once more when the console reports that installation/update is ready.
5. The preconfigured owner can open `/ugui`. No console UUID bind is needed.

The installer performs no network download or plugin hot-load. It checks the
bundled JAR's SHA-256, descriptor identity/version and main class before copying.
An active GUI JAR is never modified: upgrades are staged for the next startup.
Current/newer GUI versions are left alone, and duplicate/conflicting JARs are
reported instead of overwritten. Existing GUI data stays in its normal folder;
GUI 1.0.1 itself updates an old different/unbound owner record to its pinned UUID,
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

Use a single Bedrock relay; other chat bridges can cause duplicates. Commands,
private messages, console output and proxy-side message forwarding are outside
the relay's scope. If signing blocks joining rather than sending chat, this relay
cannot help because it intentionally does not change authentication or admission.

## Validation

Build with JDK 21: first `mvn -f ultimate-gui/pom.xml package`, then
`python3 bedrock-chat/package-admin.py`, then `mvn -f bedrock-chat/pom.xml package`.
The packaging script verifies the embedded GUI version and owner UUID. Run the dependency-free
`shop.blinksmp.chat.ChatPolicyTest` main method to test validation/rate limits;
the GitHub Actions workflow runs it after compiling. AdminInstallerTest also
checks fresh installs, restart staging, unchanged active files, duplicates,
checksums and downgrade prevention. Chat tests cover maximum length,
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
