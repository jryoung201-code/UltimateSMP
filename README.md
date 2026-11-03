# UltimateOptimize 1.0.0

Paper 1.21.11 plugin, requires Java 21. No additional plugin dependencies.
Uses public Paper APIs. Can run alongside Geyser, Floodgate and ViaVersion.

## Install

1. Stop the server and put UltimateOptimize-1.0.0.jar in plugins/.
2. Start the server. Its config is created at plugins/UltimateOptimize/config.yml.
3. Run `ultimateperf status` in the console, or `/uperf status` in game as OP.
4. Test joining and exploring from both Java and Bedrock. Compare performance
   under a similar player count and workload before and after installing.

## Behavior

Normal world view distance is capped at 6 chunks; simulation distance at 4.
If a world's existing distances are lower, those lower values remain the ceiling.
Samples run on the server thread every 200 ticks (about 10 seconds at 20 TPS).
After a 60-second startup warmup, three consecutive high-heap OR high-tick-time
samples select the pressure profile: view 4 and simulation 3. Twelve consecutive
samples with BOTH heap and tick time below the recovery thresholds select normal.
Each world changes at most one chunk per 30 real-time seconds, checked on samples.
At low TPS, sampling and changes take longer. Memory is used heap divided by max
heap; it is not the process/container RAM shown in your hosting panel.

Smaller view distances can reduce chunk data and Geyser translation/network load.
Paper handles ordinary chunk unloading and saving. Player-specific distance
overrides, force-loaded chunks, plugin chunk tickets, other plugins' caches and
Geyser's base memory are outside this plugin's control. It does not scan entities
or chunks, remove items/mobs, force garbage collection, edit server files,
pregenerate terrain, or change Geyser settings.

Lower simulation distance affects the distance at which mobs, redstone and farms
run. Reduced view distance means less visible terrain. Memory savings depend on
your starting settings, player spread and workload. An empty server may see little
benefit. Java may keep already committed heap even when live data shrinks, so the
host panel's RAM figure may not fall immediately. No fixed savings or lag-free
Bedrock experience is guaranteed: connection latency, client performance, chunk
generation and other plugins can still cause stutter.

## Commands

- `/uperf status`: mode, used/max heap, average tick time and world distances.
- `/uperf pause`: stop optimization and restore captured world distances.
- `/uperf resume`: re-enable gradual management with a new pressure warmup.
- `/uperf reload`: validate/reload this plugin's config without a server reload.

Console uses the commands without `/`. Permission: `ultimateoptimize.admin`, OP by
default. The full command is `/ultimateperf`; `/uperf` is an alias.
On disable, world distances are restored if they still match the last values
applied by this plugin. On server restart the original server/world config still
applies. Exclude worlds managed by another distance plugin with `excluded-worlds`.
Do not run two automatic distance managers on the same world.

## Build and verification

Source: https://github.com/jryoung201-code/UltimateSMP
Run `mvn package` with JDK 21 in the repository to rebuild. Maven compiles the
dependency-free PolicyTest, but run its `main` method explicitly to execute it.
The delivered plugin was compiled against a cached Paper 1.21.11 API. The policy
tests cover pressure spikes, interrupted recovery, hysteresis, resume, tick pressure,
invalid telemetry, bounds and one-chunk steps. It has not been live-tested on a
Minecraft server or Bedrock client in this environment.

GitHub Actions builds the JAR and runs PolicyTest on pushes and pull requests.
The downloadable Actions artifact is named UltimateOptimize-1.0.0.

Paper API: https://jd.papermc.io/paper/1.21.11/org/bukkit/World.html
