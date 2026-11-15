# UltimateCrates 1.0.0

Paper 1.21.11 / Java 21. Install the JAR in `plugins/` on both Paper backends and fully restart them. Do not install on Velocity. The plugin recognizes UltimateRanks OWNER/DEV and OP as crate admins; other staff need `ultimatecrates.admin`. Players have `ultimatecrates.use` by default. Placing/registering in `ultimate_spawn` also requires `edit.spawn`.

## Make your first crate

1. `/crates create common Common Crate`
2. Wait for the save confirmation, then `/crates edit common` or `/crates`.
3. Click an item in your player inventory to COPY its full stack, name, lore and enchantments into a reward slot. You keep the original. Alternatively select an item onto your cursor, then left-click a reward slot to copy it there. Dragging is disabled.
4. Left-click a reward with an empty cursor to adjust its weight. Right-click removes it. Weights are relative: 1, 3 and 6 produce 10%, 30% and 60%. Every entry starts at weight 1.
5. Click the green SAVE block. Closing/cancelling without Save discards edits. Preview/editor items cannot be taken out as rewards.
6. Empty an existing chest/barrel, look at it and `/crates set common`. This works with the eight empty chest slots in the supplied spawn world.
7. `/crates give RealJR_25736 common 10` gives ten keys. Right-click a registered crate to consume one matching key and receive one randomly selected reward. Keys may be anywhere in storage/hotbar or offhand.

## Commands

| Command | Purpose |
| --- | --- |
| `/crates` | Admin GUI; player usage help |
| `/crates create <id> [display name]` | Create an empty crate |
| `/crates edit <id>` | Reward and weight editor |
| `/crates rename <id> <display name>` | Change crate/key display name |
| `/crates give <online-player> <id> [1-64]` | Give KEYS |
| `/crates givecrate <online-player> <id> [1-64]` | Give placeable crate blocks |
| `/crates set <id>` | Register the chest/barrel you look at |
| `/crates remove` | Unregister the container you look at |

`/crate` and `/ucrates` are aliases. Create, edit and rename require an in-game admin. Give commands also work from console. The recipient must be on that same backend; run the command on their backend. IDs use lowercase letters, digits, `_` or `-`, up to 32 characters. Up to 45 item rewards per crate. There is no economy, command reward, animation or purchase system in this release.

## Players

Left-click a registered crate to preview reward items and chances. Right-click to open with one matching key. If a complete reward will not fit, the key is kept and nothing is dropped. Inventory mutation is planned before applying it on the server thread. BlinkNetwork loading, saving and transfer sessions are checked before giving or claiming rewards. Crate opening has a one-second click cooldown. Crates are protected from normal container access, hoppers, piston movement, explosions and breaking; `/crates remove` unregisters them first.

## Shared storage

With `storage: auto` and BlinkNetwork enabled, crate names, item templates and weights use the existing BlinkNetwork MySQL/MariaDB JDBC configuration or environment credentials. No credentials are copied into this plugin's config. It creates only `ultimatesmp_crates_v1`; existing player/network tables remain untouched. Other backends refresh within the configured interval (10 seconds by default). Concurrent editor saves use revision checks; a stale editor cannot overwrite another admin's changes.

Keys retain their crate ID across the existing inventory synchronization system. Crate block locations are local in `plugins/UltimateCrates/bindings.yml`, because worlds and block placements belong to a specific backend. Register spawn crates on backend 02 only. Do not copy bindings to another backend. Rename crates without changing their ID to keep existing keys valid.

Without BlinkNetwork, `auto` uses local `plugins/UltimateCrates/crates.yml`. Explicit `storage: local` also uses that file. It does not synchronize to a database. Do not switch storage modes expecting automatic migration. Back up the selected data store first. Database failures pause openings after the next refresh; do not edit bindings/reward files while the plugin is running.

## Verification

CI compiles against Paper 1.21.11 and checks weighted selection boundaries, safe inventory plans, unchanged input stacks, metadata-sensitive merging, template copies, atomic local saves and shared MariaDB revision conflicts. Live Java/Bedrock GUI and right-click gameplay have not been tested. Plugin installation does not automatically register the decorative spawn-world chests.
