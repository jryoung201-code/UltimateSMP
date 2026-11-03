# UltimateTrades 1.0.0

Player-to-player item trades for Paper 1.21.11 / Java 21. No required plugins.
Works with Java players and Geyser/Floodgate Bedrock players using the standard
chest GUI; no custom resource pack required. Money trading is not included.

## Install

Stop the server, put UltimateTrades-1.0.0.jar in plugins/, then start it.
Configuration is generated at plugins/UltimateTrades/config.yml. Restart after
config changes. `ultimatetrades.use` is granted to all players by default.

## Commands

- `/trade <player>` sends a request. Use the exact online name, including a
  Bedrock player's Floodgate prefix (normally a dot).
- `/trade accept [player]` accepts; specify the name if there are multiple requests.
- `/trade deny [player]` declines a request.
- `/trade cancel` cancels the current trade or your outgoing request.
- `/trade toggle` disables/enables requests for your UUID until server restart.
- `/trade help` shows commands. `/trading` is an alias.

Requests expire after 30 seconds with a 5-second sending cooldown. Both players
must remain in survival/adventure, in the same world, within 16 blocks, and visible
to each other. Creative/spectator mode, teleport, death, disconnect, closing the
menu, plugin shutdown or a 180-second trade timeout cancels the trade.

## Trading

The requester offers on the LEFT; the accepting player offers on the RIGHT.
Player labels and confirmation buttons identify both sides.

1. Click/tap a stack in your inventory to add the whole stack to your offer.
   Items stay in your inventory while the GUI shows a preview. Up to 12 stacks
   per person can be offered. Armor/offhand slots are not offered.
2. Click your offered stack (or the same inventory stack again) to remove it.
3. Each player clicks their own confirmation button. Any offer change removes
   BOTH confirmations. Clicking the button again unconfirms.
4. Once both confirm, a minimum 3-second safety delay begins. You can still
   change/cancel. The final swap rechecks the items and both inventories' space.

Only normal left-click/tap is used. Shift-click, dragging, number-key swaps,
double-click collection and dropping/swapping items while trading are blocked.
Items are not taken into escrow. Closing/cancelling therefore needs no item-return
operation. Preview items are cleared immediately on closure, before deferred GUI
cleanup. Requesting a trade while holding a cursor item is rejected.

## Inventory checks and limits

Before swapping, the plugin compares exact offered stacks against their original
inventory slots (including amount and metadata). Invalid offers are removed and
both players must confirm again. It calculates both resulting storage inventories
before writing either. Incoming stacks respect item and inventory stack limits.
Insufficient space changes neither inventory and resets confirmation. Gifts with
an empty offer on one side are allowed; completely empty trades cannot confirm.
No items are dropped on the ground to resolve capacity problems.

Both writes run together on Paper's main thread. Runtime setter errors trigger
best-effort rollback and a server log entry. This is not a crash-atomic database
transaction: a forced process kill or a different plugin modifying/saving player
inventories outside normal main-thread rules can still break guarantees. Normal
server shutdown cancels pending trades; completed trades use Paper's normal player
saving. Back up player data as usual. This plugin does not change other plugins'
commands or provide combat/economy integrations. Completed trades are logged with
player UUIDs and offered stack counts, not full item contents.

## Build and verification

`mvn -f ultimate-trades/pom.xml package`

GitHub Actions builds with Java 21 and runs tests for confirmation reset, stale
clicks, safety delays, one-shot closure, capacity, custom stack limits and 1,000
randomized item-conservation scenarios. Inventory planner tests use simulated
ItemStack equality/limits and do not replace live Paper item-metadata testing.
Live Java/Bedrock GUI and interactions with installed inventory plugins still
need testing on the real server. No live Minecraft server/client was available.
