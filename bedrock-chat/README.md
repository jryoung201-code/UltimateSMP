# UltimateBedrockChat 1.1.0 — Java, Bedrock and Velocity chat

Paper 1.21.11 / Java 21+. The existing plugin name and data folder are retained. Both editions' public packets are intercepted before Paper's signed-chat handler and dispatched once through Player.chat. Normal Paper legacy/modern events, UltimateRanks mutes/vanish restrictions, filters, local viewer preferences and rank rendering still run. Public chat cannot dispatch commands. The existing private-message allowlist uses player permissions and command preprocess events; private messages stay with the existing local command provider.

## Install

1. Replace old UltimateBedrockChat JARs with **UltimateBedrockChat-1.1.0.jar** in plugins on **each Paper backend**.
2. Put **UltimateChat-Velocity-1.1.0.jar** in **Velocity's** plugins. Do not put the Paper JAR on Velocity or the Velocity JAR on Paper.
3. Keep PacketEvents enabled on Paper. The Paper JAR retains the official PacketEvents 2.14.0 and owner-bound UltimateGUI 1.0.2 installers. If PacketEvents was missing, restart again after its first installation. Floodgate is optional for chat; it still handles Bedrock identity/login where configured.
4. Restart fully. `/bedrockchat` (alias `/ultimatechat`, operator only) shows ACTIVE, session and outgoing frame counters. Velocity must log `UltimateChat 1.1.0 ready`.

Existing config files need no replacement. This new option defaults to true even when absent:

```yaml
proxy-chat:
  enabled: true
```

No database or shared secret is needed. Channel: `ultimatesmp:chat`. Local chat still works without the proxy companion; remote delivery requires it on the same proxy.

## Missing profile public key / secure-chat state

The plugin does not invent Mojang keys or edit security settings. If signing is advertised as mandatory, a client may disable its own chat UI before it sends any packet. Explicitly configure unsigned-compatible chat:

Every Paper backend, **server.properties**:

```properties
enforce-secure-profile=false
```

Velocity, **velocity.toml**, top level:

```toml
force-key-authentication = false
```

Keep Velocity `online-mode = true`, modern player forwarding, matching forwarding secrets and backend proxy protection. Backend online-mode=false is normal behind authenticated Velocity; do not turn off proxy account authentication to fix chat. Restart and reconnect clients after editing. These signing options allow unsigned chat; messages do not acquire valid Mojang signatures. Client multiplayer permissions and chat visibility still apply.

Official references:
- https://geysermc.org/wiki/geyser/secure-chat/
- https://docs.papermc.io/velocity/dev/plugin-messaging/
- https://docs.papermc.io/velocity/player-information-forwarding/

## Routing and limits

Source Paper owns local delivery. After all event listeners finish, the relay rechecks cancellation and rejects restricted audiences (any local player or console removed). It serializes the final public/console Adventure rendering. User text is never parsed as MiniMessage or commands.

Velocity consumes the channel before checking its source. Client custom payloads are rejected. Backend frames must identify the carrying player and arrive from their current server. A bounded/versioned envelope, rate limit and duplicate detection prevent malformed or repeated delivery. Only players outside the source backend receive the proxy system-chat message, including virtual-queue players; it never loops back through another backend. Empty backends require no carrier.

Remote messages use source public/console formatting. Per-recipient rank-tag visibility, ignore lists and backend-only recipient filters on remote servers are not consulted. Servers needing local-only chat semantics can set proxy-chat.enabled=false. Separate Velocity instances are not connected. There is no durable/replayed chat or cross-server /msg. Disconnect/server-switch races may lose remote delivery while local chat remains delivered.

## Tests

CI builds both JARs and runs existing installer, bootstrap, validation and private-message tests; protocol truncation, size, version, identity, transfer race, duplicate and rate-limit checks; and an API-level test of rank rendering, final cancellation, restricted viewers, disconnects and exactly one outgoing frame. Live Java/Bedrock chat and cross-server delivery still require an in-game check.
