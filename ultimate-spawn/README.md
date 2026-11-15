# UltimateSpawn 1.0.0

Paper 1.21.11 / Java 21. Creates a separate protected flat world named `ultimate_spawn` on BlinkNetwork's bootstrap backend (currently `BlinkSMP-Overworld-02`). Players on either backend can use `/spawn`. Only players explicitly granted `edit.spawn` can build, change containers, drop/pick up items, or use `/setspawn` there. OP status alone does not bypass protection. Trusted console commands and other plugins retain server authority.

## Coordinated installation

Back up plugin configs, player files, worlds and the shared database first. Stop both Paper backends and Velocity. Replace older copies of the matching JARs; keep existing plugin data folders.

| Destination | JARs |
| --- | --- |
| Both Paper backends, `plugins/` | UltimateSpawn-1.0.0.jar, UltimateRanks-1.3.1.jar, BlinkNetwork-Paper-0.5.3.jar |
| Both Paper backends, if UltimateNetwork is already installed | UltimateNetwork-Paper-1.0.1.jar |
| Velocity proxy, `plugins/` | BlinkNetwork-Velocity-0.5.3.jar |

Never put a Paper JAR on Velocity. Keep BlinkQueue 1.2.0, LimboAPI and UltimateNetwork-Velocity 1.0.0 if already installed. They do not need replacing for this release. Use full restarts, not `/reload`.

The existing BlinkNetwork database must use shared MySQL/MariaDB. UltimateSpawn reads its JDBC credentials from BlinkNetwork and creates only `ultimatesmp_spawn_v1`. Network schema remains version 3. This update does not migrate old Neon data or reset player ranks/inventories.

Start backend 02, backend 01 and the proxy after all matching files have been replaced. Confirm `Shared network spawn is ready.` on both backends. `ultimate_spawn` is generated only on backend 02. Keep the same `plugins/UltimateSpawn/config.yml` on both backends; blank `spawn-server` follows the native `bootstrap-server` value. Do not rename an established spawn world or move its host without migrating the world and stored point together.

## Commands and permissions

- `/spawn`: all players; three-second warmup, cancelled by movement or received damage. Five-second cooldown by default.
- `/setspawn`: stand on solid ground inside `ultimate_spawn`; requires `edit.spawn`. Saves the shared arrival point.
- `/overworld`: existing BlinkNetwork command to return to the gameplay Overworld.
- `ultimatespawn.use`: defaults to true.
- `edit.spawn`: defaults to false; granted to OWNER and DEV by the included ranks update.

Spawn has no PvP, natural mob spawning, weather or daylight progression. Its default border is 256 blocks wide. Existing survival inventories travel with the player through the fenced network transfer protocol; spawn is not a separate inventory profile. `/sethome` is blocked inside spawn because it is an auxiliary world. The optional UltimateNetwork Paper update enables TP/TPA and player-location tools to target players there.

## Rank configuration

Edit `plugins/UltimateRanks/config.yml` on both backends. The existing rank names remain fixed. Weights choose the visible chat/TAB tag; permissions from every assigned rank combine. Weights do not change staff authority.

```yaml
extra-permissions:
  OWNER: [ultimateoptimize.admin, edit.spawn]
  DEV: [ultimateoptimize.admin, edit.spawn]
```

Merge changes into your existing section; do not replace unrelated rank entries. `weights` and `prefixes` are also editable. Use `/rank reload` on each backend after editing. The first startup adds `edit.spawn` to OWNER/DEV without removing other grants or saved player ranks. After the migration marker is saved, removing the permission is respected. Config files are local and must be kept consistent across backends.

## Validation

Built with GitHub Actions. Automated checks cover spawn edit permissions (including OP without permission), warmup rules, input validation, shared MariaDB spawn persistence, rank permission migration, and auxiliary-world inventory handoff/restart recovery on PostgreSQL, MySQL and MariaDB. The database handoff tests exercise the native schema; UltimateSpawn itself requires MySQL/MariaDB.

No changes have been installed on live servers. Actual two-client Java/Bedrock travel and client rendering still need an in-game check after coordinated installation.
