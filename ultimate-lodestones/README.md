# UltimateLodestones 1.0.0

Paper 1.21.11 / Java 21+. Install the same JAR in plugins on **every world backend** and restart. Nothing goes on Velocity or on players' clients. No database, commands on join, shared secret or extra dependency is needed. Geyser can translate the standard compass item; live Java and Bedrock pointing must still be checked with the installed Geyser version.

Use a compass on a lodestone normally. The plugin adopts its dimension and block coordinates, preserves those on the item as persistent data, and disables vanilla's local-only lodestone existence check. When the inventory moves to another backend, it resolves the dimension against that backend's configured world. BlinkNetwork's normal item metadata serialization carries the target. This does not transfer inventories by itself; the existing inventory-sync plugin must be working.

```yaml
worlds:
  Overworld: world
  Nether: world_nether
  End: world_the_end
adopt-vanilla-compasses: true
```

Match world names to each backend. Custom/missing/unloaded worlds are not created or force-loaded. Extra custom worlds are not collapsed into the three standard dimensions. Preserve the `ultimatelodestones:target` item tag in inventory synchronization.

Commands for all players, only affecting the compass in their main hand:
- `/lodestonecompass` or `/lcompass status`: show saved target.
- `/lcompass forget`: clear the target and return that item to normal compass behavior.

Rebinding to a new lodestone updates the target. Normal unbound and recovery compasses, stack amounts, enchantments, names and lore are left intact. Existing still-linked lodestone compasses are adopted automatically. If an old compass already lost its target before installation, rebind it once; the previous coordinates cannot be reconstructed.

This is a **portable coordinate anchor**: destroying the original lodestone does not invalidate remote compasses. That is necessary here because another backend may have no actual lodestone at the same coordinates. There is no cross-server block-existence registry or teleport action. Vanilla compass needles work within the target dimension and still spin when held in another dimension; no Nether coordinate conversion is applied. World seeds do not affect pointing, but servers must share the same coordinate layout for travel to be meaningful.

Main inventories/cursors are checked each tick so restored or picked-up tracked compasses can be protected before normal inventory ticking; Ender chests are checked once per second and on relevant inventory/join events. Only compasses are inspected deeply and unchanged items are not rewritten. No chunks, terrain, SQL or network requests are loaded for pointing.

CI builds the real Paper API JAR and runs portable-target parsing and API-level compass tests, including backend remapping, PDC restoration, rebinding, missing/custom worlds, ordinary items, explicit forgetting and idempotence. Live inventory transfer and Java/Bedrock needle checks remain required.

Paper API: https://jd.papermc.io/paper/1.21.11/org/bukkit/inventory/meta/CompassMeta.html
