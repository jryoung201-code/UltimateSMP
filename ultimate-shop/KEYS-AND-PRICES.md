# UltimateShop 1.1.0: keys and website price editor

Replace UltimateShop with **UltimateShop-1.1.0.jar** and UltimateCrates with **UltimateCrates-1.2.2.jar** on every Paper gameplay server. Keep plugin folders and fully restart. UltimateRanks 1.3.3+ is still required; no new ranks update is needed. Do not install these on Velocity.

## Website

* Public store: https://www.blinksmp.shop/#store
* Price editor: https://www.blinksmp.shop/#store/admin

Sign in with the existing Discord login. Only users in the website's existing `STAFF_DISCORD_IDS` can read or edit prices. Price updates require the existing session CSRF token, are recorded in an audit table, and reject concurrent stale edits. If your Discord login is not configured as staff, configure your Discord user ID in Render's existing STAFF_DISCORD_IDS setting. Minecraft OP/ranks do not grant website access.

Set USD prices for Common, Uncommon, Rare and Legendary keys, then enable each product. Keys start unpriced/disabled. You can also edit membership prices and one-time contribution amounts. Existing memberships and checkouts keep their original order price; new subscription prices apply only to new subscriptions. Discord-boost Ultimate+ is not a cash product. Supporter milestone thresholds remain the existing $5/$10/$25/$50/$100/$150/$250/$500 totals; changing a contribution amount changes how much that button contributes, not the recognition thresholds. Key purchases do not count as contributions.

Prices accept $0.50–$10,000.00. Key quantities are 1–64 per purchase. Prices/quantities/UUIDs are verified on the website backend; the browser cannot submit its own price or executable commands. Stripe setup remains as described in README.md. Live payments remain disabled until your Stripe credentials/webhook and live enable flag are configured.

## Matching crates

Create and configure the four shared crates, with rewards, in UltimateCrates:

```text
/crates create common Common
/crates create uncommon Uncommon
/crates create rare Rare
/crates create legendary Legendary
```

Configure rewards with `/crates edit <id>`, then place/register their crate blocks. Do not overwrite existing crates; if yours use different IDs, map them in `plugins/UltimateShop/config.yml`:

```yaml
key-packages:
  key-common: common
  key-uncommon: uncommon
  key-rare: rare
  key-legendary: legendary
```

Run `/ultimateshop reload` after mapping changes. A key product only opens checkout while a recent compatible backend reports its matching configured crate. Players use `/shop` for a private account link. Verified purchases queue physical UltimateCrates keys for their exact Java/Floodgate UUID, then deliver when they join an installed backend, their network inventory is ready, and they have room. Delivery does not interrupt transfer/restore. The existing crate spin still gives the reward after the animation.

Console/admin commands:

```text
ultimateshop givekey <player|uuid> <common|uncommon|rare|legendary> [1-64]
ultimateshop deliveries <player|uuid>
ultimateshop retry <delivery-uuid> confirm
```

Management uses existing `ultimateshop.admin`. Names must have joined with UltimateShop; exact UUID works offline. `givekey` queues keys; rank `give`/`revoke` commands remain separate.

## Delivery recovery

The shared ledger allows only one backend to claim a delivery. Full inventories/offline players remain pending. Known failed grants return to pending. An interrupted or ambiguous claim is **not automatically replayed**: check `/ultimateshop deliveries`, verify the player's inventory/network saves, then use the explicit retry command only when the keys were not received. Retry can duplicate items if staff confirm incorrectly. A hard crash around delivery or the normal network inventory save can require manual recovery; this is not an atomic transaction with Minecraft world/player saves.

Refunds/disputes cancel unclaimed key deliveries and flag claimed deliveries for review. Already delivered keys, traded keys, or opened crate rewards cannot safely be removed automatically; handle those cases with staff review. Never reset network inventories to resolve a shop delivery.

Automated checks cover authenticated/CSRF-protected price edits, validation, price snapshots, concurrent checkout and delivery claims, quantity verification, webhook replay, crate API readiness, existing crate spin tests, and MariaDB persistence. No live charge or multiplayer installation is performed by the build.
