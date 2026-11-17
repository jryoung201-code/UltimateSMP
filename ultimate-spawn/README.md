# UltimateSpawn 1.1.0 — spawn-only duels

Install this JAR in place of UltimateSpawn 1.0.0 on both Paper backends. Keep plugins/UltimateSpawn and the shared database configuration. Requires the existing BlinkNetwork 0.5.3+ auxiliary-world API (0.5.5 recommended). Do not install on Velocity or keep duplicate UltimateSpawn JARs. Fully stop before replacing and start after. No database migration or new payment/rank permissions are required.

## Commands

- `/duel <player>`: send a 30-second request while both players are in Survival inside the actual protected spawn world on the spawn host. Bedrock names use the Floodgate prefix shown in TAB.
- `/duel accept [player]`: accept an incoming request. Both players must still be at spawn with network data ready and no pending spawn teleport/return.
- `/duel deny` or `/duel cancel`: cancel a pending request at spawn.
- `/duel leave`: surrender during a duel or request another attempt at a pending return. This exit is available in the arena.
- Existing `/spawn` and `/setspawn` remain unchanged.

`ultimatespawn.duel` defaults to true. Spawn editing still requires `edit.spawn`.

## Arena and outcome

The spawn host automatically creates a NEW flat world named `ultimate_duel_arena`, builds a contained 51x51 stone/glass arena at Y80, and registers it as a BlinkNetwork auxiliary world. Existing unrelated worlds with the same name are refused; choose a new `duels.world-name` in that case. It does not edit the spawn build or gameplay worlds. One duel runs at a time. Both players travel through the network API before a three-second countdown arms combat. The maximum fight time defaults to five minutes.

Use your own gear: durability, food, ammunition, potions and other consumables are spent normally. There is no item wager, kit replacement, payout or inventory cloning. Pre-duel health, hunger, absorption and potion effects are recorded; timed effects continue to expire with elapsed time. Fight health/hunger start full. A lethal hit is cancelled and counted as defeat before normal death. Environmental lethal damage, leaving the fighting area, surrender and disconnect also end the duel; timeout is a draw. Both players return to the existing `/setspawn` point. If that point is unavailable or unsafe, the return remains pending rather than sending them to unsafe terrain.

The arena blocks building, container GUIs, drops/pickups, outside damage and teleport escapes. Commands other than `/duel leave` are blocked until return completes. Native spawn PvP protection is never disabled. Requests use normal Bukkit commands and support both Java and Bedrock.

## Restart safety

`plugins/UltimateSpawn/duel-returns.yml` stores only durable return markers and pre-duel vitals, never inventories. It is atomically saved BEFORE arena travel. After a return, the network checkpoint and restored-vitals checkpoint must finish before removing the marker. On restart or reconnect the pending player returns to spawn as soon as their authoritative network data is ready. Keep this file and the plugin's arena ownership marker. Stopping the plugin does not overwrite a corrupt/unreadable return file. Avoid `/reload`.

The existing config keeps working without edits. Optional new settings (also included in a newly generated config):

```yaml
duels:
  enabled: true
  world-name: ultimate_duel_arena
  request-seconds: 30
  countdown-seconds: 3
  max-seconds: 300
```

Validation includes consent, competing requests, expiry, countdown/outsider damage rules, match completion, atomic restart markers and the existing spawn protection / shared MariaDB tests. Live multiplayer combat and other installed combat plugins still need checking after installation. Test `/duel`, acceptance, defeat, surrender, disconnect/reconnect and a restart with two test players before general use.

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

