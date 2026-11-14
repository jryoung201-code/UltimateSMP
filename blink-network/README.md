# BlinkNetwork 0.5.0: PostgreSQL, MySQL and MariaDB

The Paper backends and Velocity proxy can now use the same existing MySQL/MariaDB database, including host-provided databases. `jdbc:mysql:` and `jdbc:mariadb:` select the MySQL dialect; `jdbc:postgresql:` keeps the existing PostgreSQL dialect. Both JARs bundle the required drivers. Schema version remains 3. PostgreSQL users do not need to change database settings.

MySQL uses isolated `blinksmp_network_v1_` table prefixes inside the selected database; it does not require CREATE DATABASE or alter other plugins' tables. Initialize with `common/src/main/resources/schema-mysql.sql` in the host database tool, or `blinknet schema` from a stopped/maintenance backend console after configuration. MySQL DDL implicitly commits; initialize while the network is offline and verify version 3 only after the full script succeeds. Existing PostgreSQL schema.sql is unchanged.

All Paper backends AND Velocity must use the same database and upgraded JARs. Configure the existing `database.jdbc-url`, `database.username`, and `database.password` (or the existing BLINK_NETWORK_* environment overrides). Example (substitute the actual database name; keep credentials private):

```yaml
database:
  jdbc-url: jdbc:mariadb://ny.sql.gravelhost.com:3306/YOUR_DATABASE?sslMode=verify-full
  username: YOUR_DATABASE_USER
  password: YOUR_DATABASE_PASSWORD
```

Check the host's TLS/certificate configuration and remote connection access from both Paper hosts and the proxy before cutover. Do not disable certificate validation as a workaround. MySQL 8.0+ / MariaDB 10.6+ with InnoDB and enforced CHECK constraints are required. CI verifies PostgreSQL 16, MySQL 8.4 and MariaDB 11.4. UUIDs use CHAR(36), snapshots/profile data use bounded binary blobs, and session fencing/row locks and optimistic profile revisions remain enforced. `useAffectedRows=false` is rejected because it breaks duplicate-profile detection.

Changing databases does not copy inventory, rank, home, ownership or recovery data. While Neon is quota-blocked, its rows cannot be exported through the blocked connection. Preserve Neon, back up all local plugin/player files, and prepare a complete import before enabling joins on the new database. Do not initialize fresh inventory state for existing players without choosing the correct recovery source. The website map still uses its configured PostgreSQL connection; it requires its own adapter/configuration update to follow MySQL.

# BlinkNetwork 0.5.0 — Overworld travel fixes

Fallback RTP and /leave only select Overworld points. Paper publishes only verified Overworld samples, and the database rejects a fallback relocation into Nether or End even if a stale old point exists.

Use **/overworld** to return to a safe owned Overworld landing, preferring the current physical backend. Use **/blinkserver <server-ID>** to move to a safe Overworld location on that backend. Both are public by default through `blinknetwork.travel`. Normal proxy server switches to registered healthy gameplay IDs now ask the source Paper backend to prepare an inventory-safe handoff instead of misreporting maintenance. A request is HMAC-authenticated with the private exact-session token, expires, is debounced and is checked again on the source; the session token is never transmitted. Arbitrary direct connections still cannot claim an active inventory.

Nether portal returns retain vanilla scaled coordinates when their target region is assigned. If the scaled Overworld exit has no owner, a portal may use a verified safe Overworld landing instead. Homes and exact-coordinate teleports do not silently change their destination: an unassigned region now reports that it is outside assigned server regions. Genuine offline/maintenance targets stay blocked. No ownership rows or world chunks are reassigned automatically.

Install **BlinkNetwork-Paper 0.5.0 on BOTH world backends** and **BlinkNetwork-Velocity 0.5.0 on the proxy**, replacing older JARs. Keep BlinkQueue 1.1.0 and LimboAPI 1.2.0 unchanged. Proxy remains on Java 25; gameplay Paper remains Java 21. Existing schema 3 and credentials remain unchanged. Restart all affected processes normally; no /reload.

Use **BlinkQueue 1.1.0 + LimboAPI 1.2.0** on Velocity for the restart queue. The dedicated Paper queue server is no longer needed. Follow [the current installation guide](../blink-queue/README.md). The world ownership, inventory/profile synchronization, border-loop protections and schema-3 session fencing remain in place.

Proxy runtime: Java 25 because of LimboAPI. Gameplay Paper: Java 21 / 1.21.11. Existing schema 3 requires no migration for 0.4.0. Upgrading from schema 2 still requires the existing schema-3 migration with all old gameplay processes stopped. Take a database snapshot first. Preserve existing Neon credentials, world data, bootstrap/import and profile synchronization choices.

Queue readiness now means the virtual room plugin is ready on the proxy. No queue backend ping, physical server allocation, host API or startup webhook is used. An internal queue-id (old queue-server alias accepted) only identifies drain messages and health rows. Remove the physical queue from velocity.toml and its try list after upgrading. No server/world files are deleted automatically.

Console-only `blinknet restart <10..86400 seconds>` marks maintenance, counts down, verifies virtual queue readiness, saves each exact session, drains players and dispatches restart after they leave. Failed drains pause the restart. `blinknet resume` clears maintenance after an operator fixes a failed drain. External panel force-stops bypass graceful saves; scheduled plugins should dispatch `blinknet restart 10` instead of raw `restart`.

The public queue bridge chooses a verified saved world or a safe RTP point and returns its ID to LimboAPI, which connects through normal Velocity routing. It never loads active player data into a second session. A stopped/replaced old runtime is permanently fenced before its snapshot can be reused. New world admissions use short-lived exact-target permits, and are checked again against the database. The virtual room retries route selection so a recovered world can admit players without waiting for an unrelated queue server.

Gameplay backends still own Overworld, Nether and End regions as configured. Missing Nether/End assignments mirror the same numeric 9×9-chunk Overworld region on all-dimension backends; explicit dimension assignments take precedence. Unavailable homes/teleports/pearls are cancelled with the maintenance message. Verify portal landing territories and copied terrain before opening access.

Optional profile synchronization requires matching network-aware Ultimate plugin versions and the same choices on all backends. It does not automatically synchronize arbitrary plugin economies/trades. Crash recovery uses the last committed player checkpoint; world and inventory persistence are not globally crash-atomic.

Console `blinknet rescue <UUID> <x> <y> <z>` remains offline-only, preserves inventory/profiles and uses an already-generated safe location in the backend's primary dimension. Active/handoff locks cannot be bypassed. Manual `blinknet recover <UUID> ALL_PLAYER_SERVER_PROCESSES_STOPPED` remains an explicit administrative recovery step requiring all possible old processes to be stopped.

