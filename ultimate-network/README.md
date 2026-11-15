# UltimateNetwork 1.0.0 — Paper 1.21.11 / Java 21 / Velocity

This release connects the existing Ultimate plugins using a backend-only Velocity channel. BlinkNetwork remains the sole inventory owner and transfer implementation. No server connects a player directly around that handoff.

## Install

1. Back up the shared Gravel database and each server's plugins/world/player files. Stop the proxy and both Paper servers for a coordinated update.
2. On **both Paper backends**, replace existing versions with the contents of `Paper/plugins/`. Keep Geyser, Floodgate, PacketEvents, UltimateTPR, UltimateGUI, UltimateOptimize, UltimateAttributeSwap and BlinkDimensions installed. Do not leave duplicate older JARs. UltimateBedrockChat can install its bundled PacketEvents/Admin GUI if absent, and never downgrades a newer GUI.
3. On **Velocity**, replace BlinkNetwork-Velocity with 0.5.1 and add UltimateNetwork-Velocity. Keep BlinkQueue/LimboAPI and UltimateChat-Velocity installed. Never put Paper JARs on Velocity.
4. Preserve both existing BlinkNetwork database credentials, server IDs, world mappings, assignments, bootstrap-server and forwarding settings. Schema remains **3**. No SQL rebuild or player reset is needed.
5. On **both Paper servers**, enable `profile-sync.enabled`, `ranks`, `sanctions`, `homes`, and `settings` in BlinkNetwork's existing config. Statistics/advancements are optional and must match across backends. Existing rank/home imports remain intact. If importing old personal settings, choose the original bootstrap server as the source and use the existing `seed-missing-from-local` procedure before allowing players onto the other backend; never overwrite existing database modules or bulk-copy player inventories.
6. Confirm Velocity modern forwarding and backend access restrictions are configured, and retain `proxy-security-configured: true` on each backend.
7. Start the two Paper servers, then Velocity. `/unetwork` should show the bridge online. Test with one player on each backend before opening the network.

## Commands and behavior

- `/tp <player>` and `/bring <player>` work across backends with existing UltimateRanks staff permissions. `/staff tp ...` aliases also route. Frozen/busy players and unavailable destinations are rejected.
- `/tpr <player>` / `/tpa`, `/tprhere` / `/tpahere`, `/tprAccept [player]` / `/tpaccept`, `/tprDeny [player]` / `/tpdeny`, and `/tprCancel` / `/tpcancel` work across the network. `teleportRequest...` aliases are retained. Shared requests expire after 30 seconds, have a five-second send cooldown, a five-request recipient cap and a three-second warmup. Movement/damage cancels the mover's warmup. These network limits replace the local TPR timing options while this bridge is active.
- `/msg`, `/tell`, `/w`, `/whisper`, `/pm`, `/reply`, `/r`, and `/sc` work network-wide. Private-message preferences and mutes are checked. Vanished players cannot be discovered through names, private messages or requests.
- Remote `/warn`, `/warnings`, `/kick`, `/mute`, `/unmute`, `/ban`, `/tempban`, `/unban`, `/freeze`, `/heal`, `/feed`, and `/rank add|remove|set|info` route to the player's backend. UltimateRanks still enforces staff rank, target hierarchy, issuer reversal and duration limits. Remote commands execute in a restricted actor context, **never as console**. Existing local/offline moderation stays in UltimateRanks.
- `/invsee <remote player>` first transfers the authorized staff member through the protected handoff, then opens the original read-only GUI once both inventories are ready. This command moves the staff member.
- `/maxhomes <remote player> set <number>` and `reset` work across backends with `ultimatehomes.admin`. OWNER/unlimited permission still takes priority.
- `/trade <player>` works network-wide. Acceptance brings the requester to the acceptor when necessary, then opens the **existing** two-confirmation trade GUI. Both inventories must be ready; item transfer never happens through plugin messages. Local distance/mode/cursor checks still apply. `/trade toggle` carries its request preference across server changes until proxy restart. Active trade cancellation remains in UltimateTrades.
- Ranks, homes, sanctions and personal settings follow the existing shared database profile mechanism. Homes retain their remote-world destination support.
- General announcements are relayed across the network with each player's normal title/chat/action-bar/boss-bar rendering. Automatic rotation runs only on the bootstrap server, or `network.automatic-leader` configured in UltimateAnnouncements. Restart countdowns remain specific to the backend actually restarting.
- Join messages appear once, after the player's first backend inventory becomes ready; leave messages appear only at proxy disconnect. Switching servers emits neither. Viewers' join-message/player-visibility settings and vanish are respected.
- Remote visible players appear in TAB and command-name completion. Rank prefixes and display weights come from UltimateRanks; viewers can hide rank tags or players with Settings. Authority uses staff rank, not cosmetic weights.
- Optimizer, attribute swapping, monster spawning settings and dimension logic continue running locally on every backend. Lodestone metadata already survives transfers using UltimateLodestones. This does not automatically adapt unrelated third-party plugins or merge world files.

## Optional Render backend

The existing website can serve `POST https://www.blinksmp.shop/network/api/v1/presence` and `GET https://www.blinksmp.shop/network/api/v1/status`. Render stores an allowlisted status snapshot in Gravel: visible online count and server IDs only. No player names, positions, private messages, items, homes or credentials are uploaded.

Set these environment variables on **Velocity**:

```
ULTIMATE_NETWORK_RENDER_URL=https://www.blinksmp.shop/network/api/v1/presence
ULTIMATE_NETWORK_RENDER_TOKEN=<copy the same private token from Render's environment>
```

Render uses `ULTIMATE_NETWORK_RENDER_TOKEN` too. Keep the token out of Git and chat. Without these variables the mirror is disabled; the gameplay bridge still works. HTTPS requests are asynchronous, bounded and at most once a minute while players are active; no polling is used to defeat a hosting plan's idle policy. After the last player leaves, the mirror sends one zero-count snapshot and stops. Stale data becomes unavailable after two minutes. A Render restart, cold start, failed request or database outage never authorizes, blocks or retries an inventory transfer.

## Verification and limits

CI builds both platform adapters and all updated plugins. Unit tests cover bounded frames, spoofed sources, recipient-bound/expired consent, request spam, initial joins vs internal transfers, hidden-player rosters, cross-server TPA correlation, private replies and message opt-outs. The BlinkNetwork workflow separately tests its existing inventory/database safety on PostgreSQL, MySQL and MariaDB.

Builds and automated tests do not replace a two-player live test on your configured servers. Bukkit cancel/visibility rules are rechecked on the owning server. A transfer can still fail safely if a world is missing, a destination becomes unavailable, or its landing is unsafe. Console/audit logs from the host and other plugins remain independent. Existing third-party ignore/social-block plugins need their own adapters before they can filter the new proxy-delivered private messages. Existing UltimateBedrockChat remains required for unsigned public chat relay; UltimateNetwork does not replace it.
