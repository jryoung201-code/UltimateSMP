# UltimateCrates 1.2.1 — deliver after the spin

Replace the older UltimateCrates JAR on Paper backends; keep plugins/UltimateCrates and your database. Fully stop/restart the backend, not /reload. No schema migration or config changes are needed.

The reward is selected internally when opening begins, but no item is delivered and no key is consumed until the configured spin finishes. At that point the plugin rechecks the current key, inventory capacity and BlinkNetwork readiness, then exchanges exactly one key for the selected reward. TempGrants voucher rewards follow the same delayed delivery path.

Closing the GUI early lets the same spin finish in the background; it never gives an early reward or starts a second spin. Disconnecting, changing worlds, shutting down, losing network readiness, moving away the key or running out of space cancels the opening without consuming a key. Failed openings never show the selected prize as a win. Another key is not accepted while a spin is pending. GUI display items cannot be removed.

The opening-animation-seconds setting remains 0–10 (default 3); zero intentionally disables the delay. Crate definitions, rarity chances, keys, labels and /keyall are unchanged. Tests cover no early delivery, early close, shutdown/disconnect/world changes, network fences, single delivery, failed delivery and GUI item protection. Live Java/Bedrock opening checks remain necessary after installation.

# UltimateCrates usage

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

Left-click a registered crate to preview reward items and chances. Right-click to open with one matching key. Before drawing a reward, every possible reward is checked for available space. This prevents using a full inventory to filter unwanted outcomes. If any possible reward will not fit, the key is kept and nothing is dropped. Inventory mutation is planned before applying it on the server thread. BlinkNetwork loading, saving and transfer sessions are checked before giving or claiming rewards. Crate opening has a one-second click cooldown. Crates are protected from normal container access, hoppers, piston movement, explosions and breaking; `/crates remove` unregisters them first.

## Shared storage

With `storage: auto` and BlinkNetwork enabled, crate names, item templates and weights use the existing BlinkNetwork MySQL/MariaDB JDBC configuration or environment credentials. No credentials are copied into this plugin's config. It creates only `ultimatesmp_crates_v1`; existing player/network tables remain untouched. Other backends refresh within the configured interval (10 seconds by default). Concurrent editor saves use revision checks; a stale editor cannot overwrite another admin's changes.

Keys retain their crate ID across the existing inventory synchronization system. Crate block locations are local in `plugins/UltimateCrates/bindings.yml`, because worlds and block placements belong to a specific backend. Register spawn crates on backend 02 only. Do not copy bindings to another backend. Rename crates without changing their ID to keep existing keys valid.

Without BlinkNetwork, `auto` uses local `plugins/UltimateCrates/crates.yml`. Explicit `storage: local` also uses that file. It does not synchronize to a database. Do not switch storage modes expecting automatic migration. Back up the selected data store first. Database failures pause openings after the next refresh; do not edit bindings/reward files while the plugin is running.

## Verification

CI compiles against Paper 1.21.11 and checks weighted selection boundaries, safe inventory plans, unchanged input stacks, metadata-sensitive merging, template copies, atomic local saves and shared MariaDB revision conflicts. Live Java/Bedrock GUI and right-click gameplay have not been tested. Plugin installation does not automatically register the decorative spawn-world chests.

## Timed voucher rewards
UltimateTempGrants 1.0.0 is detected dynamically. Its reward templates are materialized into fresh one-use voucher IDs before key consumption. The integration rechecks inventory readiness, the actual key and capacity after issuance. All inventory exchanges occur on the main thread after the spin.

## Floating text, rarity and key-all (1.1.0)

Registered crate chests now show floating text automatically. Labels use temporary TextDisplay entities, do not force chunks to load, and are removed on disable/unregister/chunk unload. They are reconstructed from the saved crate definition. Java text scaling uses the display transformation; Bedrock/Geyser rendering has not been tested.

```
/crates hologram common text &6Common Crate\n&fRight-click to open
/crates hologram common color gold
/crates hologram common color #aa55ff
/crates hologram common size 1.5
/crates hologram common height 1.7
/crates hologram common hide
/crates hologram common show
```

Text supports `&` colors, literal `\n` newlines and `{name}`. Inline colors override the label's base color. Size is 0.25-4; height is 1.1-6 blocks measured from the crate block's base. In `/crates edit`, the name-tag button opens size/color/height/visibility controls. Return to rewards and SAVE. Commands save immediately. Label settings synchronize with shared crate definitions; physical label entities exist only on backends with a registered, loaded crate block. Update both backends so an older editor cannot drop newer metadata.

Left-click a reward in the editor, then choose Common, Uncommon, Rare or Legendary. Present groups receive relative shares 60/25/12/3, divided among their automatic rewards. For example, two Common items and one of each other rarity give each Common 30%, Uncommon 25%, Rare 12%, Legendary 3%. Missing groups redistribute their share. Weight controls change the relative chances *within* a group. Existing rewards retain their original weight-only probabilities until rarity presets are introduced; old entries then join Common.

Manual overrides use percentage points. The editor has +/-1% and Reset-to-auto controls. For exact values:

```
/crates rarity common 1 rare
/crates chance common 1 5
/crates chance common 1 auto
```

Reward numbers match slots 1-45 in a freshly opened editor. Fixed percentages are reserved first; automatic entries share the remainder using rarity budgets. Totals above 100% are rejected. If every reward is manual, their percentages must total 100%. Setting one to 100% makes others zero until adjusted. Previews show actual calculated probabilities. New copied rewards default to Common. Chance/rarity changes preserve labels and full item metadata.

```
/crates keyall common 5
/keyall common 5
```

Key-all gives five Common keys to every ready online player on the EXECUTING BACKEND, with an admin summary of given/full/loading recipients. No keys are dropped for full inventories. It does not broadcast across Velocity; run on each backend to cover both servers. Uses the existing admin authorization. The amount defaults to one and is capped at 64 per player per invocation.

## Player opening GUI (1.2.1)
Right-clicking a registered crate with a matching key opens a 27-slot rolling reward display. The final prize is shown in the center under the yellow marker. The weighted draw is selected before the reel starts, but the inventory grant and key consumption happen only after the spin finishes and current inventory/key checks pass. Display items cannot be taken, shift-clicked, hotbar-swapped or dragged out. Closing early lets the same animation finish in the background. Disconnects, world changes, server stops and invalid inventory/key state cancel without consuming the key.

`opening-animation-seconds` defaults to 3 and accepts 0-10. Zero shows the prize immediately in the GUI. Update the Crates JAR on both Paper backends; no proxy, Ranks or TempGrants update is needed. Existing config files use the default automatically. Java/Bedrock gameplay rendering still needs a live check; the GUI uses normal chest inventory screens.

