# BlinkNetwork 0.2.4 — particle borders and safer transfers

Paper 1.21.11 / Java 21 region-border transfers with Velocity 3.4+, a shared Neon PostgreSQL database, and persistent fenced sessions. This is a first network-layer release: live Java/Bedrock and fault testing is required before migrating the public server.

## New in 0.2.4
- Adds per-player particle walls near region edges. Cyan means the neighboring region belongs to another backend; red means unassigned. Shared boundaries within the same server are invisible. Configure visibility, distance, spacing, height, update interval, and hex colors under `particle-border` on each Paper backend.
- Treats a claimed destination as an accepted handoff during source timeout, preventing false "Transfer state changed" source kicks and any source overwrite after the target owns the snapshot. An incomplete/uncertain handoff remains fenced for safety.
- Returning players connect through Velocity to the backend owning their **saved region**. If a player's last position is in an unassigned region, assign that region or recover the player to a verified safe coordinate; do not bypass ownership by forcing an unrelated backend.
- These are staging fixes, not proof of end-to-end live transfer success. Test transfers in both directions and inspect proxy/source/destination logs on failures.

## Previous in 0.2.2
- Fixes initial player-data import on Paper 1.21.11 by accepting its 43-slot inventory layout (including body and saddle) and restores old 41-slot snapshots without dropping items. Always back up original `world/playerdata` before first import.

- Each Overworld, Nether and End can have one shared square world border configured on the protected map admin website. All Paper instances in a dimension apply Neon-defined borders during their normal five-second refresh. Unconfigured dimensions retain their vanilla borders.
- Crossing a region or teleporting to a coordinate on a different backend initiates a fenced player transfer through Velocity (the original 0.2.0 movement logic).
- An offline returning player is routed according to the ownership of the region containing their saved position. Legacy snapshots without readable coordinates retain old behavior.
- Server heartbeat initialization retries after transient Neon outages and duplicate instance claims; the plugin no longer remains permanently BLOCKED after a one-time failure.
- Additive schema migration: create `blinksmp_network_v1.world_borders` **before deploying updated Paper JARs**, but keep `meta.version=2`. Do not overwrite existing player snapshots or active ownership. The map's removal operation deletes ownership metadata only, never world files. It rejects active players and protects the origin/spawn region.

## What is implemented

- Two JARs: `BlinkNetwork-Paper-0.2.4.jar` for every backend and `BlinkNetwork-Velocity-0.2.4.jar` for the proxy. PostgreSQL driver and small connection pool are bundled.
- Server IDs `BlinkSMP-(Overworld|Nether|End)-(01..99)`; additional registered servers need no code changes.
- 9x9-chunk ownership regions (144x144 blocks), floor division for negative coordinates; initial ownership assignment, refresh within five seconds.
- Automatic crossings to available destinations, vanilla teleport/portal destination interception, and reconnect routing using the player's saved coordinates and live region ownership. New players first enter the bootstrap server.
- Main-thread item/state capture, off-thread JDBC, UUID session fencing and one-time pending tickets. Source quit never overwrites prepared destination state. Ordinary failed switches roll back only if the source still owns the handoff. Unknown database outcomes retain a lock rather than loading an arbitrary old inventory.
- Serialized inventory/armor/offhand and Ender Chest using Paper NBT, selected slot, health, hunger, saturation, exhaustion, XP, gamemode, effects, air/fire and destination position. Effect timers pause during transfer. Fall distance is reset at arrival.
- Only the configured local dimension/world is playable per backend. Unassigned/offline borders are blocked. Block break/place/interaction is restricted to assigned territory. Vehicles/passengers and open container/crafting transfers are rejected.
- Persistent locks do not automatically expire after a process crash. Administrative recovery requires an explicit console acknowledgement.
- **Opt-in profile sync:** Neon-backed versioned modules for rank, sanctions (ban/mute/warnings), homes, settings, statistics, and experimental advancements. All profile writes use optimistic revisions to reject lost updates. UltimateRanks, UltimateHomes and UltimateSettings are updated in this same repository and **must also be installed as matching builds**. Home records retain dimension metadata and remote `/home` destinations are handed to BlinkNetwork's region router. An enabled server polls for remote rank/ban changes.

## Not implemented by this release

- Chunk/build/entity migration or live region reassignment. Both world copies must be prepared before first assigning territory. Existing regions cannot be overwritten with `/blinknet assign`.
- Vanish state, economy, trades, chat and TPR requests remain local. Cross-server homes are supported by the updated UltimateHomes adapter, but this does not implement a cross-server `/tpr` destination.
- Recipe synchronization, respawn/bed binding across servers, player persistent-data/custom attributes, or arbitrary third-party plugin state. Player-data schema is versioned for future adapters. Advancement synchronization is **experimental and disabled by default** because granting criteria may retrigger rewards/commands; leave it off on production until reward-safe transfer behavior is validated.
- Terrain tiles, public player tracking, or a built-in HTTP API. See WEBSITE-INTEGRATION.md for the actual DB contract; the website's previously proposed schema is not assumed to exist.
- Seamless entity visibility, fluids/redstone across servers, or guaranteed invisible transitions. Separate world copies remain independent; avoid bases/farms straddling borders. Keep backups. A crash can roll back player data to the last successful checkpoint (normally <=30 seconds), and manual recovery needs review for inventory/world mismatches. This is not a crash-atomic world+inventory system.

## Installation — maintenance/test network first

1. Back up UltimateSMP's world and player/plugin data. Keep both communities' original files until the test network is verified.
2. Put Velocity on `V` (2 GB). Register backend names exactly in `velocity.toml`, e.g.:

   ```toml
   [servers]
   BlinkSMP-Overworld-01 = "BACKEND_1_HOST:PORT"
   BlinkSMP-Overworld-02 = "BACKEND_2_HOST:PORT"
   try = ["BlinkSMP-Overworld-01"]
   ```

   Use Velocity `online-mode = true` and modern player forwarding. Configure backend Paper `proxies.velocity.enabled`, matching forwarding secret and online-mode setting; backend server.properties online-mode=false belongs ONLY behind properly authenticated forwarding. Restrict direct backend access with firewall rules where available. Do not expose an unauthenticated offline backend. Disable the legacy BungeeCord plugin-message switching channel (`bungee-plugin-message-channel = false`) and remove ordinary players' `/server` access.
3. Put the proxy JAR in Velocity's `plugins/`; put the Paper JAR in both Paper `plugins/` folders. Start once with their default `enabled=false` to generate configs, then stop.
4. Prepare the two world copies. Same seed alone does not copy builds; use a stopped consistent copy of UltimateSMP's world as the starting point if preserving its terrain/builds. The runtime copies must have separate folders and storage. Set matching version, seed, generator and relevant datapacks. Allocate each region to only one backend. Default IDs are Overworld-01 and Overworld-02, with `world: world` on both. Each Nether/End backend instead names its actual local dimension world.
5. **Stop the old proxy and all backends** and apply the new v2 `common/src/main/resources/schema.sql` using a Neon **direct** URL and a migration/admin role, first on an isolated development branch. Do not mix v1 and v2 network JARs. A console-only `blinknet schema` helper is also available on an enabled/configured backend, but normal runtime credentials should not have schema-creation rights. It is an explicit schema migration, never an automatic startup operation.
6. Configure the same database/branch in both backends and the proxy. Recommended environment variables:

   ```text
   BLINK_NETWORK_JDBC_URL=jdbc:postgresql://NEON_HOST/DATABASE?sslmode=require&channelBinding=require
   BLINK_NETWORK_DB_USER=ROLE
   BLINK_NETWORK_DB_PASSWORD=PASSWORD
   ```

   Use the Java connection parameters from Neon's Connect dialog; its current example uses `sslmode=require&channelBinding=require`. The optional `verify-full` example in the generated config requires a configured trusted root certificate or an appropriate Java SSL factory. See https://neon.com/docs/guides/java.

   Alternatively use private config fields (never commit passwords). A normal `postgresql://user:password@...` URL is NOT JDBC syntax; split its credentials into the separate fields. The runtime can use a pooled Neon endpoint; use a direct URL for migrations. All copies must use the same logical database and schema. Do not reuse a backup plugin that independently loads inventories on join.
7. Set distinct backend `server-id`, matching `world`, `proxy-security-configured: true`, and `enabled: true`. Set `import-local-players: true` ONLY on `BlinkSMP-Overworld-01` while intentionally importing local data. Unknown player records cannot bootstrap elsewhere. On the proxy set `enabled=true`, the same JDBC fields and `bootstrap-server=BlinkSMP-Overworld-01` in `plugins/blinknetwork/network.properties`.
8. Start the backends. They register themselves as disabled until explicitly enabled. With console, register/enable each backend, then initially assign the regions you intend to make playable. Assign the entire spawn area before joining:

   ```text
   blinknet register BlinkSMP-Overworld-01
   blinknet register BlinkSMP-Overworld-02
   blinknet enable BlinkSMP-Overworld-01
   blinknet enable BlinkSMP-Overworld-02
   blinknet assign Overworld 0 0 BlinkSMP-Overworld-01
   blinknet assign Overworld 1 0 BlinkSMP-Overworld-02
   blinknet status
   ```

   Region (0,0) is blocks x/z [0,144). Region (1,0) is x [144,288), z [0,144). If spawn is at negative coordinates, assign those regions too. Unassigned territory is closed. A new assignment assumes its terrain is already prepared; the command does not copy world files.
9. **Profile migration:** leave `profile-sync.enabled: false` until a backup and staging test are complete. The same per-category choices must be enabled on every Paper backend. Configure `seed-missing-from-local: true` **only** on the original bootstrap server for a one-time migration of existing local rank/home/settings records into Neon; use `false` on all other servers. Each existing player should first join the bootstrap server to seed their missing categories. Then set `seed-missing-from-local: false` and fully restart. No module should be re-seeded from stale server files after migration. The v2 schema is required even if profile sync is off. Make sure all three Ultimate plugins are the matching updated versions.

10. Start/restart Velocity after schema/config are ready. Join only its public address. Configure Geyser/Floodgate at the proxy using their documented Velocity setup and consistent Bedrock UUID forwarding; do not run conflicting backend and proxy login paths. Verify Bedrock identities and gameplay in staging. This build does not auto-install or modify Geyser.
11. Test item-rich transfers, both directions, disconnects, server outage, database outage, reconnect routing and existing gameplay-plugin compatibility. Also test: ranks granted across both backends, bans applied to offline and online players, warnings/mutes, settings toggles, local and remote homes across dimensions, and statistics. Enable advancements only on an isolated test server with a disposable player profile. Setup changes require full restarts; `/reload` is unsupported.

## Website contract

See WEBSITE-INTEGRATION.md. No website code was changed by this plugin release, and no production Neon schema/credentials were created or altered. Its connection fields are deliberately blank.

## Recovery and troubleshooting

- Missing schema, wrong IDs/world environment, DB errors or duplicate live server IDs block login. Correct configuration then restart. Logs omit exception messages that might contain connection secrets.
- A briefly unavailable destination leaves the player at the source; handoffs time out after 15 seconds. A loading player is disconnected after 30 seconds. An unavailable last-server is not replaced with an unrelated world/spawn.
- If a lock survives a crash, stop **every server process that might still hold that player** before recovery. Leave one separate management backend connected, or execute a reviewed DB migration. On the management backend console:

  ```text
  blinknet recover PLAYER_UUID ALL_PLAYER_SERVER_PROCESSES_STOPPED
  ```

  This uses the last committed source snapshot if a handoff was pending. It cannot reconstruct actions after the last checkpoint and must not be used to force access while the old process is alive. Inspect inventories and world changes before reopening. Audit records mark recovery.
- Disabled mode does not enforce network login or routing; keep maintenance access controls until all components are enabled. Do not hot-disable/reload while players are online.

## Build and verification

`mvn -B -ntp -f blink-network/pom.xml verify`

CI uses Java 21 and an isolated PostgreSQL 16 service. Policy tests cover floor boundaries, strict IDs, malformed tickets and routing. DB tests exercise real handoffs, concurrent claims, fencing, rollback, unavailable destinations, recovery, empty-login cleanup, payload bounds and immutable initial assignments. Without `TEST_JDBC_URL`, DB integration tests are explicitly skipped; CI always supplies it.

## Profile sync behavior and caveats

- Enable `profile-sync.enabled` on **every** Paper backend with identical category switches. The existing data stores remain on disk as local cache/backup; Neon revisions are authoritative once initialized.
- Edits to rank/sanctions/settings/homes enqueue a database update. Player joins, 30-second checkpoints, transfers, and disconnects persist any changed enabled modules. Rank/sanction updates on another server are polled roughly every five seconds. An unexpected revision conflict is treated as a safety error, not silently overwritten.
- A failed module write can leave a live player frozen or disconnected pending staff review. Back up both Neon and plugin files before migration. Reconcile local versus remote records before using `blinknet recover`; that command only recovers inventory/session data.
- Home dimension information is new. Old home files infer the dimension from `_nether` / `_the_end` world names; if a legacy world used a custom name, review and correct its home data before migration. Remote homes use their coordinate's current assigned region; no chunk data is moved.
- Statistics use Bukkit/Paper's native counters; large profiles can increase tick cost. Advancement progress uses native criteria APIs which may trigger advancement rewards again. It is **off by default** for this reason. Recipes, economy, and unrelated plugin data are not yet synchronized.
- This is a staging release. Passing CI is not proof of correct behavior under network partitions, server crashes, overlapping edits, or Bedrock identities. Do not deploy directly to a public network.
