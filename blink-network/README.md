# BlinkNetwork 0.3.0 — Paper 1.21.11 / Velocity / Java 21

Each gameplay backend can own the same 9×9 Minecraft-chunk regions (144×144 blocks) in the Overworld, Nether and End. Nether coordinates still follow vanilla portal scaling; region ownership uses actual coordinates in each dimension. Matching region X/Z means the same numeric region, not an eight-times-larger mirrored area.

## Upgrade from 0.2.x

1. Back up Neon and every world. Stop Velocity and all gameplay backends before migration. Do not mix 0.2.x and 0.3.0 binaries.
2. Apply `common/src/main/resources/schema.sql` with the direct Neon connection and migration role. Schema version becomes 3; schema name remains `blinksmp_network_v1`. Existing inventories, profiles and explicit dimension assignments are retained. No world chunks are moved.
3. Replace the Paper JAR on every gameplay backend and the Velocity JAR on the proxy. Remove old duplicate JARs. Remove/disable the separate BlinkDimensions plugin: dimension routing is now built into BlinkNetwork, and its previous bridge deliberately rejects this build to prevent double transfers.
4. On both gameplay backends, configure the same three local world names, enable Nether in `server.properties` and End in `bukkit.yml`. Copy the original dimension world data deliberately; the same seed alone does not copy player builds. Existing `world:` remains the primary world matching the ID.
5. Start Paper backends first, then Velocity. Each backend advertises its all-dimension capability only after configuration succeeds. `blinknet status` must report ready. Player bootstrap/import and opt-in profile sync settings remain unchanged.

```yaml
enabled: true
role: world
server-id: BlinkSMP-Overworld-01  # second backend: BlinkSMP-Overworld-02
world: world
all-dimensions: true
worlds:
  Overworld: world
  Nether: world_nether
  End: world_the_end
queue-server: BlinkSMP-Queue-01
bootstrap-server: BlinkSMP-Overworld-01
proxy-security-configured: true
```

Supply private Neon JDBC URL/user/password in the existing config or `BLINK_NETWORK_JDBC_URL`, `BLINK_NETWORK_DB_USER`, `BLINK_NETWORK_DB_PASSWORD`. Never expose these to the map browser. Velocity modern forwarding and backend access restrictions are required on gameplay AND queue servers. All entry goes through Velocity. Do not use `/reload`.

## Ownership and dimensions

Existing Overworld ownership automatically supplies missing Nether/End assignments on a backend advertising `all_dimensions=true`. An explicit assignment in that dimension overrides the mirror. For example, an Overworld region `(2,-1)` owned by Overworld-01 is also owned by it in the Nether and End unless separately assigned. This preserves previous explicit dimension owners rather than silently replacing terrain.

`blinknet assign <Overworld|Nether|End> <regionX> <regionZ> <serverID>` remains console-only and INSERT-only. Existing ownership must only be moved with a planned terrain migration while affected servers are stopped. Coordinates use floor division, including negatives. World borders are separate per dimension. Check that Nether portal arrival coordinates and End spawn/return regions are assigned before opening them.

Homes using the BlinkNetwork bridge, Bukkit teleports, ender pearls, dimension portals and walking across regions all check ownership and readiness. An offline/unassigned/maintenance destination is cancelled with **This server is under maintenance. Please try again later.** Existing cached health is refreshed every 5 seconds, expires after 15 seconds, and transfer preparation also checks the database. An outage cannot be detected instantaneously; transfers racing a failure roll back safely to the source.

## Dedicated restart queue

You need a third, separate Paper process/server, registered in `velocity.toml` as `BlinkSMP-Queue-01`. It cannot share the Velocity process or a gameplay server's directory/port. Its address must be supplied by the host; this release does not provision hosting accounts.

Install the SAME Paper JAR on that dedicated server, then set:

```yaml
role: queue
```

Queue mode runs even with `enabled: false`, does not open Neon, import inventory or run gameplay profiles. It clears the queue's independent local inventory, prevents interaction, damage, hunger and building, and keeps players near spawn with flight. Put a safe lobby spawn there and install no gameplay/inventory plugins on it. Configure the same Velocity forwarding secret and restrict direct backend access.

In `plugins/blinknetwork/network.properties` on Velocity, set `queue-server=BlinkSMP-Queue-01` and register all gameplay backends under their exact IDs. The proxy probes queue health. Keep the queue running unless you configure a supported host-start endpoint.

### Starting a sleeping queue

Optional `BLINK_QUEUE_START_URL` (or `queue-start-url`) must point to YOUR operator-owned HTTPS endpoint that starts this specific queue server. The plugin sends POST with no body, at most once a minute while maintenance/queue demand exists. Optional `BLINK_QUEUE_START_TOKEN` supplies a Bearer token. Redirects are not followed; URLs/tokens are not logged. Your endpoint must validate authorization and use your host's documented power API. No host API path, server ID or credential is guessed. Leave blank for an always-running queue.

The plugin cannot start a powered-off server using Minecraft commands alone. A configured host API/webhook or an already-running queue is required. Host plan rules and sleep restrictions still apply.

## Planned restarts

From the gameplay console: `blinknet restart 60` (10–86400 seconds). Plain console `restart` and authorized in-game `/restart` are intercepted into a 10-second drain. This runs `restart`, never `stop`; the host must implement the actual restart behavior.

The backend immediately marks itself under maintenance, which requests queue startup through the proxy. Players see the final 10-second countdown. At expiry it waits for a healthy queue, closes containers, saves inventory/profiles, releases the exact gameplay session, and sends a verified queue ticket. Restart happens only after players have left the source. If queue readiness or saving/transfer does not complete within 120 seconds, restart is paused and logged; fix the problem and use console `blinknet resume`. External panel force-stops bypass this graceful path. Other plugins using `Bukkit.dispatchCommand` directly may bypass console preprocessing too: configure their scheduled action as `blinknet restart 10` instead of raw `restart`.

Queue players automatically return to their original server once ready, or switch to another healthy server if it is unavailable. `/leave` requests random safe relocation to a DIFFERENT gameplay backend, excluding the origin. It is handled by Velocity without command registration, so it is absent from tab/autocomplete. With no alternative safe landing, the player stays queued and receives a waiting message. Disconnecting never deletes gameplay inventory.

## Unexpected outages and RTP

Players reconnecting to an unavailable world are routed to the queue. Network-disconnect events without a supplied kick reason, or with Velocity’s own internal connection error, also route there. Set Velocity `failover-on-unexpected-server-disconnect = true` so abrupt socket closures fire the failover event. Explicit moderation kicks and rejected logins retain their disconnect behavior, so the queue does not bypass bans or repeatedly bounce failed restores.

RTP chooses a randomly sampled safe position on another healthy backend, inside assigned territory and its world border. Paper samples one random territory location every 5 seconds across its configured dimensions, generates only the sampled chunk asynchronously, rejects fluids/hazards/void and Nether-roof landings, and publishes verified points. Points expire after 45 seconds. Destination feet, head and floor are checked again before state is applied. Remote portal arrivals also search nearby loaded owned terrain for a safe floor without overwriting builds; a failed arrival keeps a recovery marker for queue relocation on reconnect. There is no guarantee that every dimension has a usable candidate at every instant. If none exists, wait in the queue. A changed/blocked landing is rejected with the saved snapshot intact; reconnecting retries via the queue instead of repeating that landing indefinitely.

A live player session is never force-unlocked just because a destination is disabled. After an actual heartbeat outage, recovery waits at least 45 seconds and permanently retires the old runtime instance before releasing the last committed snapshot. If a replacement process is already running, the snapshot's recorded owner instance allows the orphan to be fenced without stopping that replacement. The old process's later heartbeat/saves are rejected. This is recovery from the last committed inventory checkpoint, not distributed crash-atomic world persistence: unsaved gameplay since the last checkpoint may roll back. Profile revisions are retained.

## Profiles and rescue

Rank, sanctions, homes, settings and optional vanilla profiles retain the existing opt-in synchronization contract. Use matching network-aware Ultimate plugins. Profile CAS conflicts fail closed. Sanctions are imported before gameplay; queue has no gameplay permissions or inventory. Economy, trading and arbitrary plugin state are not automatically synchronized.

Border transfers commit the source-side location, inset foreign seams by two blocks on restore, and enforce a cooldown. Console-only `blinknet rescue <UUID> <x> <y> <z>` repairs an OFFLINE snapshot at a generated safe point in this backend's primary dimension without changing items/profiles. Active locks cannot be bypassed. Manual `blinknet recover <UUID> ALL_PLAYER_SERVER_PROCESSES_STOPPED` is only for deliberate administrative recovery after every possible copy of that player process is stopped.

## Validation

CI compiles both Java 21 artifacts and runs unit tests plus integration tests against isolated PostgreSQL 16. Tests cover dimension mirrors/overrides, stale instance fencing and replacement processes, maintenance blocking, exact-session queue saves, RTP inventory preservation, border-loop safety, inventory layout and kick-loop policy. Live host restart behavior, Bedrock/Geyser transfer presentation, actual portal terrain and your queue webhook require a staging test before opening the network.
