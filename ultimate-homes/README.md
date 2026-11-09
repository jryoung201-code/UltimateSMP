# UltimateHomes 1.0.0

Paper 1.21.11 / Java 21. Standalone UUID-based homes with a chest GUI compatible
with normal Java inventory clicks and Geyser Bedrock taps. No dependencies.

## Install and commands

Put UltimateHomes-1.0.0.jar in plugins/ while the server is stopped and start it.

- `/homes` or `/home`: open your homes GUI.
- `/home <name>`: teleport to a saved home.
- `/sethome <name>`: save/update a home at your current position.
- `/sethome`: save/update the home named `home`.
- `/delhome <name>` (alias `/deletehome`): immediately delete the named home.
- `/homecancel`: cancel a warming-up or loading teleport.

All commands require `ultimatehomes.use`, granted to all players by default.
The GUI lists 45 homes per page. Click a home for teleport/delete actions. GUI
home deletion requires a second confirmation; `/delhome` is an explicit direct
delete command. The Create button saves here using home1, home2, etc. Use
`/sethome <name>` to choose a custom name. Clicks/taps work without right-click.
Home names use 1-24 letters, digits, underscores or hyphens, ignoring case.

## Home limits: maxhomes.#

Grant numeric permissions through your permissions provider or Bukkit attachment:

| Permission | Limit |
| --- | --- |
| `maxhomes.1` | 1 home (default) |
| `maxhomes.3` | 3 homes |
| `maxhomes.5` | 5 homes |
| `maxhomes.10` | 10 homes |
| `maxhomes.25` | 25 homes |

Any non-negative integer node works, not only the examples. The highest granted
numeric node wins; false/denied nodes are ignored. The default node does not add
to a higher limit. To allow zero new homes, deny `maxhomes.1` and any other higher
numeric grants. `maxhomes.0` may be explicitly granted. Wildcard `maxhomes.*` and
`maxhomes.unlimited` are not treated as numeric limits; use an explicit number.
OP status is not an unlimited bypass. Effective permissions are consulted each
time; no home-limit cache needs reloading when ranks change. Existing custom rank
plugins must actually grant these nodes; a rank name alone does not grant them.

`server-max-homes` caps numeric permissions (default 1000, configurable 1-10000).
The GUI shows the effective cap. Lowering permissions never deletes saved homes
or blocks teleporting/deleting them. Updating an existing home is allowed when
at/above the new creation limit. Only adding new names requires spare capacity.

## Storage and teleport behavior

Player files live in plugins/UltimateHomes/players/<UUID>.yml. Every set/delete
is saved with a forced temporary-file write and atomic replacement before the
cache changes. Corrupt files are reported and preserved. If the filesystem does
not support atomic replacement, writes fail rather than replacing the old file
unsafely. Files have a 2 MB bound and are cached only while players use the plugin;
quit unloads their cache. Normal restarts need no special save command. Back up
this folder with your server data. Do not manually edit files while players are
online. Worlds are identified by UUID; a different world with the same name is
not silently substituted.

Default warmup: 3 seconds. Movement, actual damage, death, disconnect or another
teleport cancels it. Looking around is allowed. `/homecancel` also cancels while
chunks load. Existing destination chunks load asynchronously (no new terrain is
generated). Loading times out after 30 seconds. The final teleport rechecks the
saved home's exact contents, world, border, solid ground, hazards and room for a
standard player. Unavailable worlds or unsafe landings are rejected, with no
search for a different spot. Unusual block shapes/slabs may be conservatively
rejected. Set homes on clear, solid ground. Ordinary Bukkit teleport events let
other plugins cancel teleports. No bypass of their event-based restrictions is
provided; command-only blockers must include these home command aliases.

Configuration is loaded on plugin startup; restart after editing config.yml.
No economy, public warps, staff editing of others' homes, PlaceholderAPI or
cross-server database is included.

## Verification

Build: `mvn -f ultimate-homes/pom.xml package`.

CI tests numeric permission resolution/denials, names, exact persisted world and
coordinates, restart/deletion, ownership isolation, downgraded limits, failed
writes and preservation of corrupt files. Live Paper/Geyser GUI, permissions
provider integration and teleport interaction testing remain necessary; no live
Minecraft server/client was available here.
