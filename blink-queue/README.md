# BlinkQueue 1.0.0

A separate Velocity plugin that displays a real virtual waiting room while the dedicated Paper queue backend starts. It does not fake player counts, keep free servers awake, or replace host power controls.

## Install on the proxy

1. Back up the proxy plugins/config and fully stop Velocity.
2. Use Java **25** for the Velocity process. Paper servers may remain on Java 21. This release uses LimboAPI **1.2.0**, whose API and plugin require Java 25. Use a Velocity build compatible with that LimboAPI release (its declared API target is 3.4.0); test your exact build first because LimboAPI also hooks proxy internals.
3. Install `BlinkQueue-Velocity-1.0.0.jar` AND the official [LimboAPI 1.2.0 JAR](https://github.com/Elytrium/LimboAPI/releases/tag/1.2.0) in Velocity's `plugins/` folder. LimboAPI is a separate runtime dependency, not bundled into BlinkQueue.
4. If using BlinkNetwork, replace ONLY its proxy JAR with `BlinkNetwork-Velocity-0.3.1.jar`. Existing Paper **0.3.0** is compatible; schema remains **3**, with no new migration. Never run duplicate BlinkNetwork proxy JARs. Keep all existing database and forwarding settings.
5. Keep the real queue registered in `velocity.toml` under `BlinkSMP-Queue-01`, with its actual host address. The dedicated Paper queue still needs BlinkNetwork Paper configured with `role: queue` and secure Velocity forwarding.
6. Start Velocity once to generate `plugins/blinkqueue/queue.properties`. Set the queue ID and optional start endpoint, then restart again. No hot reload is supported.

```properties
enabled=true
queue-server=BlinkSMP-Queue-01
default-server=BlinkSMP-Overworld-01
max-wait-seconds=180
poll-seconds=3
max-waiting-players=100
queue-start-url=
```

`default-server` is only used in standalone mode without BlinkNetwork. In BlinkNetwork mode, its existing player database route decides whether an arriving player needs the queue; healthy gameplay logins continue normally after a short virtual route check.

## Startup controls

Set `BLINK_QUEUE_START_URL` or `queue-start-url` to an operator-owned **HTTPS** endpoint that starts this exact queue server through your host's supported API. BlinkQueue sends POST with no body and follows no redirects. Optional `BLINK_QUEUE_START_TOKEN` adds an Authorization Bearer header. Configure the endpoint securely outside Minecraft; no API path, credentials or hosting permissions are invented by this plugin.

BlinkNetwork 0.3.1 delegates its startup requests to BlinkQueue when available, avoiding duplicate host calls. This also warms the queue when maintenance is scheduled, before players need it. If the endpoint is blank, players can still wait while an administrator starts the queue manually. The plugin itself does not provision servers or bypass sleep rules.

## Player flow

- At login, LimboAPI provides a small glass platform on the proxy while the network route is checked. Its normal authentication/plugin callback sequence is preserved. This room has no gameplay inventory and never claims or modifies Neon player records.
- If the player needs a sleeping queue, a title says **Starting queue server...** and the countdown is shown. The proxy issues a startup request and performs one shared ping probe, not one per player.
- Planned restart transfers use BlinkNetwork's existing exact-session save and verified queue ticket before leaving the source. They can wait virtually without the Paper queue running yet, then connect when ready.
- Unexpected network disconnects also use the room. Bans, kicks with supplied moderation reasons and unsafe player-data restore rejections retain their original result. Failed transfers to a gameplay backend retain BlinkNetwork's source rollback behavior.
- After a successful queue ping, LimboAPI resumes the connection to the registered Paper queue through normal Velocity/BlinkNetwork routing. BlinkNetwork then handles inventory recovery and safe RTP as before. A successful ping is a readiness hint; final backend login checks still apply.
- A network failure racing that handoff can re-enter limbo, but the original fixed deadline is retained. Repeated failures cannot create an endless wait. Default limit: 180 seconds; capacity: 100 virtual players; startup requests at most once every 60 seconds.
- `/leave` remains the hidden BlinkNetwork command on the **real Paper queue**. The virtual startup room does not promise an inventory-safe early exit before that queue is reachable.

For abrupt socket failures, enable `failover-on-unexpected-server-disconnect = true` in Velocity. The proxy itself must stay running: if the proxy stops, this plugin and its virtual players stop with it.

## Compatibility and testing

Builds target Java 25 and use the published LimboAPI API, not hand-built Minecraft packets. CI compiles and runs policy tests for bounded deadlines, startup configuration and moderation handling, plus the existing BlinkNetwork/PostgreSQL regression suite. No live host endpoint, production inventory, proxy session or backend is changed by the build.

Stage a Java 1.21.11 join and a Geyser/Floodgate Bedrock join using your exact Velocity/LimboAPI versions, including a planned restart, sudden backend loss, failed queue boot, a ban, and a client disconnect during limbo. Configure Floodgate's required pre-limbo profile hooks according to LimboAPI/Floodgate documentation before opening Bedrock access. Packet-level compatibility and host startup behavior are not established merely by compilation.

Other plugins that own LimboAPI's kick callback must be tested together: BlinkQueue preserves a previously installed callback, but cannot control callbacks replaced later by another plugin. Install only one reconnect/queue manager for this flow.
