# BlinkNetwork 0.1.0 — test release

Paper 1.21.11 / Java 21 region-border transfers with Velocity 3.4+, a shared Neon PostgreSQL database, and persistent fenced sessions. This is a first network-layer release: live Java/Bedrock and fault testing is required before migrating the public server.

## What is implemented

- Two JARs: `BlinkNetwork-Paper-0.1.0.jar` for every backend and `BlinkNetwork-Velocity-0.1.0.jar` for the proxy. PostgreSQL driver and small connection pool are bundled.
- Server IDs `BlinkSMP-(Overworld|Nether|End)-(01..99)`; additional registered servers need no code changes.
- 9x9-chunk ownership regions (144x144 blocks), floor division for negative coordinates; initial ownership assignment, refresh within five seconds.
- Automatic crossings to available destinations, vanilla teleport/portal destination interception, and reconnect routing to the last server. New players first enter the bootstrap server.
- Main-thread item/state capture, off-thread JDBC, UUID session fencing and one-time pending tickets. Source quit never overwrites prepared destination state. Ordinary failed switches roll back only if the source still owns the handoff. Unknown database outcomes retain a lock rather than loading an arbitrary old inventory.
- Serialized inventory/armor/offhand and Ender Chest using Paper NBT, selected slot, health, hunger, saturation, exhaustion, XP, gamemode, effects, air/fire and destination position. Effect timers pause during transfer. Fall distance is reset at arrival.
- Only the configured local dimension/world is playable per backend. Unassigned/offline borders are blocked. Block break/place/interaction is restricted to assigned territory. Vehicles/passengers and open container/crafting transfers are rejected.
- Persistent locks do not automatically expire after a process crash. Administrative recovery requires an explicit console acknowledgement.

## Not implemented by this release

- Chunk/build/entity migration or live region reassignment. Both world copies must be prepared before first assigning territory. Existing regions cannot be overwritten with `/blinknet assign`.
- Existing plugin data synchronization: UltimateRanks sanctions/ranks, UltimateHomes, UltimateSettings, vanish state, economy, trades, chat and teleport requests remain local. Copying plugin JARs alone does not share their state. Do not open the network publicly until the relevant plugins have network adapters. In particular cross-server homes/TPR need server-aware destinations, and sanctions must be enforced across the network.
- Advancement/statistic/recipe synchronization, respawn/bed binding across servers, player persistent-data/custom attributes, or arbitrary third-party plugin state. Player-data schema is versioned for future adapters.
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
5. Apply `common/src/main/resources/schema.sql` using a Neon **direct** URL and a migration/admin role, first on an isolated development branch. A console-only `blinknet schema` helper is also available on an enabled/configured backend, but normal runtime credentials should not have schema-creation rights. It is an explicit schema migration, never an automatic startup operation.
6. Configure the same database/branch in both backends and the proxy. Recommended environment variables:

   ```text
   BLINK_NETWORK_JDBC_URL=jdbc:postgresql://NEON_HOST/DATABASE?sslmode=verify-full
   BLINK_NETWORK_DB_USER=ROLE
   BLINK_NETWORK_DB_PASSWORD=PASSWORD
   ```

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
9. Start/restart Velocity after schema/config are ready. Join only its public address. Configure Geyser/Floodgate at the proxy using their documented Velocity setup and consistent Bedrock UUID forwarding; do not run conflicting backend and proxy login paths. Verify Bedrock identities and gameplay in staging. This build does not auto-install or modify Geyser.
10. Test item-rich transfers, both directions, disconnects, server outage, database outage, reconnect routing and existing gameplay-plugin compatibility. Setup changes require full restarts; `/reload` is unsupported.

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
