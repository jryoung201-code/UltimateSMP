# BlinkNetwork 0.4.0 — virtual queue update

Use **BlinkQueue 1.1.0 + LimboAPI 1.2.0** on Velocity for the restart queue. The dedicated Paper queue server is no longer needed. Follow [the current installation guide](../blink-queue/README.md). The world ownership, inventory/profile synchronization, border-loop protections and schema-3 session fencing remain in place.

Proxy runtime: Java 25 because of LimboAPI. Gameplay Paper: Java 21 / 1.21.11. Existing schema 3 requires no migration for 0.4.0. Upgrading from schema 2 still requires the existing schema-3 migration with all old gameplay processes stopped. Take a database snapshot first. Preserve existing Neon credentials, world data, bootstrap/import and profile synchronization choices.

Queue readiness now means the virtual room plugin is ready on the proxy. No queue backend ping, physical server allocation, host API or startup webhook is used. An internal queue-id (old queue-server alias accepted) only identifies drain messages and health rows. Remove the physical queue from velocity.toml and its try list after upgrading. No server/world files are deleted automatically.

Console-only `blinknet restart <10..86400 seconds>` marks maintenance, counts down, verifies virtual queue readiness, saves each exact session, drains players and dispatches restart after they leave. Failed drains pause the restart. `blinknet resume` clears maintenance after an operator fixes a failed drain. External panel force-stops bypass graceful saves; scheduled plugins should dispatch `blinknet restart 10` instead of raw `restart`.

The public queue bridge chooses a verified saved world or a safe RTP point and returns its ID to LimboAPI, which connects through normal Velocity routing. It never loads active player data into a second session. A stopped/replaced old runtime is permanently fenced before its snapshot can be reused. New world admissions use short-lived exact-target permits, and are checked again against the database. The virtual room retries route selection so a recovered world can admit players without waiting for an unrelated queue server.

Gameplay backends still own Overworld, Nether and End regions as configured. Missing Nether/End assignments mirror the same numeric 9×9-chunk Overworld region on all-dimension backends; explicit dimension assignments take precedence. Unavailable homes/teleports/pearls are cancelled with the maintenance message. Verify portal landing territories and copied terrain before opening access.

Optional profile synchronization requires matching network-aware Ultimate plugin versions and the same choices on all backends. It does not automatically synchronize arbitrary plugin economies/trades. Crash recovery uses the last committed player checkpoint; world and inventory persistence are not globally crash-atomic.

Console `blinknet rescue <UUID> <x> <y> <z>` remains offline-only, preserves inventory/profiles and uses an already-generated safe location in the backend's primary dimension. Active/handoff locks cannot be bypassed. Manual `blinknet recover <UUID> ALL_PLAYER_SERVER_PROCESSES_STOPPED` remains an explicit administrative recovery step requiring all possible old processes to be stopped.
