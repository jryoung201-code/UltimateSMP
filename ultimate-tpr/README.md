# UltimateTPR 1.0.0

Paper 1.21.11 / Java 21. Standalone player teleport requests. Java and Geyser
Bedrock players use the same commands; no additional plugin is required.

## Install

Place UltimateTPR-1.0.0.jar in plugins/ while the server is stopped, then start it.
Configuration: plugins/UltimateTPR/config.yml. Restart after changing settings.
All players receive `ultimatetpr.use` by default. No OP bypass accepts requests
on someone else's behalf.

## Commands (spelling corrected)

| Short command | Long alias | Action |
| --- | --- | --- |
| `/tpr <player>` | `/teleportRequest <player>` | Ask to teleport to that player |
| `/tprhere <player>` | `/teleportRequestHere <player>` | Ask that player to teleport to you |
| `/tprAccept [player]` | `/teleportRequestAccept [player]` | Accept a request sent to you |
| `/tprCancel` | `/teleportRequestCancel` | Cancel your outgoing request or a warmup you are part of |
| `/tprDeny [player]` | `/teleportRequestDeny [player]` | Decline a request sent to you |

Commands are case-insensitive: `/tprAccept` and `/tpraccept` work alike. The
misspellings `telaport` and `cancle` are corrected to `teleport` and `cancel`.
Use the full player name, including a Bedrock player's Floodgate prefix.
Tab completion only shows visible players. Java clients can also use clickable
accept/deny messages; Bedrock users can always type the displayed commands.

If there is one incoming request, `/tprAccept` needs no name. If there are several,
name the sender to choose. A sender can have one outstanding request: sending
another replaces it. Defaults are 30-second expiry, 5-second sending cooldown,
a maximum of 5 incoming requests per recipient, and a 3-second warmup.

## Teleport behavior

Only the request's recipient can accept it. `/tprhere` never moves the recipient
until they explicitly accept. Either participant can cancel the accepted warmup.
Accepting a request clears the pair's other pending requests to avoid overlap.

The destination is captured from the other player's position at acceptance;
ordinary walking by that player does not move this destination. Looking around
is allowed. Actual movement or damage to the teleporting player cancels warmup.
Teleporting either participant elsewhere, death, disconnect, missing permission,
sleeping or entering a vehicle also prevents completion. Requests and warmups
clear on shutdown; no teleport is resumed after reboot.

Cross-world requests are allowed by default; `allow-cross-world: false` restricts
them to the same world. Safety checks at acceptance and completion require loaded
chunks, room for a standard player, solid ground, no immediate fire/lava or listed
damage blocks, and a position inside the world border. Unsafe/unloaded positions
are rejected rather than searching for a different landing point or generating
terrain. Slabs, fluids or unusual block shapes can be conservatively rejected.
These checks do not judge nearby enemies, traps, or another plugin changing the
teleport destination. Standard Bukkit teleport events let region/combat plugins
cancel the teleport; this plugin does not bypass their event handlers. Blocking
only commands called `/tpa` in another plugin does not automatically block these
new command names; add these aliases to that plugin's command rules if needed.

One main-thread task manages expiry and warmups. Teleports use Paper's standard
player teleport API only after destination chunks are confirmed loaded. Server
lag can delay a warmup. No economy, home/warp storage or random teleport feature
is included.

## Build and checks

`mvn -f ultimate-tpr/pom.xml package`

GitHub Actions builds with Java 21 and tests request direction, recipient-only
acceptance, cancellation, expiry boundaries, replacement, capacity limits, and
correct command aliases/permissions from plugin.yml. Live world safety, Java/
Bedrock command suggestions and interactions with installed teleport plugins
still need testing on the real server; no live server/client was available here.
