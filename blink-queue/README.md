# UltimateSMP Join and Restart Queues — BlinkQueue 1.2.0

Install BlinkQueue-Velocity 1.2.0 and BlinkNetwork-Velocity 0.5.2 together on Velocity (Java 25 / LimboAPI 1.2.0). Update both world backends to BlinkNetwork-Paper 0.5.2 (Java 21+, Paper 1.21.11). Keep existing JDBC credentials, IDs, world mappings and schema 3. These are in-place JAR updates: no database reset or physical queue server is needed. Remove duplicate older JARs and perform full restarts, not /reload.

## Join Queue

Fresh network connections enter the **UltimateSMP Join Queue**. Saved players get an available, verified **Overworld RTP** through the existing inventory-preserving relocation. The choice is retained while admission is pending. There is no `/leave` prompt, command registration or tab completion in this queue.

An entirely new saved profile must first load/import its local inventory on the configured bootstrap backend. The updated Paper plugin then performs that player's first safe local Overworld RTP once their inventory is ready. This preserves any pre-existing local inventory instead of creating an empty proxy-side snapshot. If terrain sampling cannot find a safe landing, the player remains on the bootstrap world and can retry `/overworld`.

A still-owned handoff cannot be rerouted until its inventory lock is resolved. Admission to that exact ticket is allowed where safe; the queue never overwrites an active inventory session.

## Restart Queue

A player already on a world backend when its planned restart evacuates them enters the **UltimateSMP Restart Queue**. Their exact saved dimension, coordinates, rotation and inventory remain untouched. The queue waits for that saved destination to become healthy and automatically returns them. Another online backend does not trigger fallback RTP.

Only Restart Queue players can type the hidden `/leave` command or see its prompt. It cancels the automatic-return intent and changes their wait to Join/RTP mode, excluding the original server. If no other safe backend is ready, they keep waiting. `/leave` remains absent from command trees and autocomplete and is ignored in Join Queue and normal gameplay by this plugin.

Network failures while already playing use restart-style recovery too, but crash recovery can restore only the last committed checkpoint. Ban, kick and unsafe-state rejections are not converted into restart recovery. Failure of an initial connection retains its Join Queue classification.

Both queues are separate LimboAPI virtual rooms. Existing wait-limit, polling and capacity settings remain in queue.properties; the fixed deadline still applies. Disconnecting and making a new network connection intentionally starts a fresh Join Queue, rather than retaining the previous Restart Queue entitlement.

## Verification

Automated queue tests cover mode-specific messages, hidden `/leave` gating, stable admission and cleanup. Proxy tests cover restart waiting without invoking RTP, exact destination return, explicit restart escape, new-login mode and active-session fencing. Real PostgreSQL, MySQL and MariaDB tests verify a restart queue preserves a Nether snapshot and inventory byte-for-byte across maintenance and return. No live server installation or two-player play test was performed by the build.
