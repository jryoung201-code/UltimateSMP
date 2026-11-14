# BlinkQueue 1.1.0 — entirely virtual queue

The waiting room IS the queue now. It lives inside Velocity using LimboAPI. No separate Paper queue server, queue IP/port, host-start endpoint or paid host API is needed. It only works while the Velocity proxy itself is running.

## Install

1. Stop Velocity and back up its plugin/config files. Replace `BlinkQueue-Velocity-1.0.0.jar` with `BlinkQueue-Velocity-1.1.0.jar`, and replace its BlinkNetwork proxy JAR with `BlinkNetwork-Velocity-0.4.0.jar`. Do not keep duplicate versions.
2. Keep **LimboAPI 1.2.0** installed on Velocity and run the proxy with **Java 25**. Its own configuration may remain unchanged.
3. On each gameplay backend install `BlinkNetwork-Paper-0.4.0.jar` during a normal coordinated restart. Paper 0.3.0's saved-ticket drain protocol remains compatible, but the new Paper JAR has updated queue configuration/status wording. Paper remains on Java 21. Database schema remains **3**, with no new migration.
4. Remove the physical queue entry from Velocity's `[servers]` and from its `try` list. Keep the real Overworld/Nether/End backend entries and bootstrap ID. You can stop/decommission the former dedicated queue process after upgrading; no world files or host resources are automatically deleted.
5. Use the small `plugins/blinkqueue/queue.properties` below. Old queue-server, default-server, queue-start-url, ping and host retry keys are ignored. The plugin sends no HTTP startup requests.

```properties
enabled=true
max-wait-seconds=600
poll-seconds=3
max-waiting-players=100
```

Existing files retain their configured timeout (including 180 seconds from 1.0.0) unless you change it. The maximum supported timeout is 3600 seconds.

BlinkNetwork's existing Neon JDBC credentials, enabled flag and bootstrap-server stay unchanged. Its internal `queue-id=BlinkSMP-Queue-01` is a protocol/status key, NOT a backend address. The old `queue-server` key is accepted as an alias for this key, so old Paper 0.3.0 configs still match. Do not register that key in `velocity.toml`. Use the same internal ID on proxy and gameplay backends.

Example Velocity entries (keep your actual world addresses):

```toml
[servers]
BlinkSMP-Overworld-01 = "37.72.174.58:50110"
BlinkSMP-Overworld-02 = "104.243.40.149:50012"
try = ["BlinkSMP-Overworld-01"]
```

Keep the rest of your `velocity.toml`, especially forwarding security and bind settings. For abrupt socket failures, enable `failover-on-unexpected-server-disconnect = true` in Velocity.

## Behavior

Every login gets a short route check in limbo. Healthy saved worlds connect directly; their availability does not depend on a physical queue. If no world can safely accept the player, the player stays on the virtual glass platform with a waiting title/countdown. Route availability is rechecked while waiting, so the player can leave limbo when a world becomes ready.

For planned restarts, Paper saves inventory/profiles and releases only its exact gameplay session before sending a verified drain ticket. Velocity acknowledges virtual queue readiness through the existing schema-3 queue-status record. Once the player has left the gameplay process, Paper dispatches `restart`, never `stop`. In virtual queue, a planned restart prefers its original world if ready; otherwise safe RTP can use another healthy backend.

Unexpected world disconnects use the same virtual queue. A live active player session is never bypassed. Stale sessions are recovered using BlinkNetwork's existing old-instance fencing, including the case where a new process is healthy on the SAME server ID. Last committed inventory data is preserved; gameplay after the last checkpoint can still roll back after a crash. Correct region ownership and a safe loaded landing are still required.

Type **`/leave`** in virtual queue to request safe RTP on a DIFFERENT gameplay backend, excluding the original source. The command is handled through LimboAPI's command/chat callback and is never registered in a command tree or autocomplete. New players need their initial bootstrap import before they can use cross-server inventory recovery. With no eligible destination, stay queued. World selection never claims an active inventory or manufactures a fresh empty one.

A world admission has a short exact-target reservation, revalidated against Neon before connecting. Changed routes, live session locks and stale targets fail closed. Network failures racing a handoff can re-enter the queue without resetting the original fixed wait budget. Explicit moderation kicks and unsafe restore rejections are preserved. Queue capacity is released once, and an old timeout callback cannot kick a player who has already connected successfully.

## Validation and limits

CI builds the Java 25 queue plugin and the Java 21 network artifacts, runs queue/bridge/command/admission tests and PostgreSQL integration tests, including stale-session recovery followed by direct rejoin of a healthy original backend without a physical queue.

LimboAPI is compiled against its official checksum-pinned release JAR because its 1.2.0 Maven artifact is not published. It remains a separate runtime dependency and is not bundled. Test Java 1.21.11 and Geyser/Floodgate clients with your exact proxy build before opening the network. LimboAPI's required profile hooks and any authentication-plugin kick callbacks still need to be configured correctly. The build does not install plugins on live hosts or delete a hosting server.
