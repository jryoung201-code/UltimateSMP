# UltimateShop 1.0.0 — Paper 1.21.11 / Java 21

Install **UltimateShop-1.0.0.jar** and replace UltimateRanks with **UltimateRanks-1.3.3.jar** on **every Paper gameplay backend**, not Velocity. Preserve existing plugin folders. Fully restart; no /reload. Shared MariaDB/MySQL is required. The plugin inherits BlinkNetwork's JDBC URL, username and password, including its BLINK_NETWORK_* environment overrides. Existing network tables and inventory snapshots are not modified.

The website integration lives in `jryoung201-code/BlinkSMP-Eco-Plugin`, `server-switcher/transfer-api/shop/`. Its schema.sql matches the plugin's schema.sql. UltimateShop initializes the separate `ultimate_shop_*` tables. The website must use this same database. No new public Minecraft ports or RCON are needed.

## Commands

* `/shop` — private 10-minute website link/code for the authenticated Minecraft UUID. Works with Java and Floodgate Bedrock identities. Codes are stored as SHA-256 hashes; never share a code or shop link. Requesting a new link invalidates the previous one. One request per 30 seconds.
* `/ultimateshop give <player|uuid> <package> [days]` — grant a package through shared records, permanently by default. Days: 1–36500. Repeating give sets a minimum expiry, it does not stack time. Unlimited grants remain unlimited until revoked.
* `/ultimateshop revoke <player|uuid> <package>` — remove its manual grant and **suspend that package from all purchase sources**. This does not cancel a Stripe subscription or issue a refund. Use give to lift the suspension.
* `/ultimateshop status <player|uuid>` — show active shared package IDs.
* `/ultimateshop reload` — reload package-to-rank mapping/website origin. Database and polling changes need restart.

Management requires `ultimateshop.admin` (console/OP by default; included for OWNER/DEV in the new default rank config). Existing rank configs retain user edits: if needed add `ultimateshop.admin` to OWNER and DEV under extra-permissions, then `/rank reload`. A target name must have been recorded by this plugin; exact UUIDs also work offline. Use the leading dot for Floodgate names. Changes appear on other backends within 10 seconds by default.

Examples (console omits slash):

```text
ultimateshop give RealJR_25736 ultimate-plus-plus 30
ultimateshop give 00000000-0000-0000-0009-01fb5b0f0d11 ultimate-plus-plus-plus 30
ultimateshop revoke RealJR_25736 ultimate-plus-plus
ultimateshop status RealJR_25736
```

## Packages and rank behavior

Website paid memberships remain Ultimate++ ($9.99 USD monthly, five homes) and Ultimate+++ ($14.99 USD monthly, nine homes). Ultimate+ remains a staff-verified Discord boost reward; the console can grant `ultimate-plus`. No automatic Discord integration is added.

One-time contributions of $5/$10/$25/$50/$100/$150/$250/$500 accumulate toward the existing eight Supporter milestones. The highest reached milestone is granted; refunded amounts and disputed payments do not count. Contributions do not include gameplay benefits. Shop package ranks never grant staff authority or OP. This version delivers **rank packages and supporter recognition**, not physical crate keys/items or arbitrary website-supplied commands.

UltimateRanks 1.3.3 has an independent shop overlay alongside permanent ranks and temporary voucher ranks. Permissions combine, and the highest configured weight controls the chat/TAB tag. Shop ranks are not exported into permanent network rank assignments. Membership expiry is checked every second even if the shop database is temporarily unavailable. Removing shop access does not remove a separately granted permanent/voucher copy of the same rank. Recognition defaults below membership and staff weights; configure prefixes/weights in UltimateRanks. Every backend must run matching package mappings and rank configs.

## Website / Stripe setup

Use your parent/guardian-owned, verified Stripe account if you are under 18. Keep all credentials in the website host's environment settings, never HTML, GitHub, Minecraft commands or chat.

Set on the existing Render website service:

```text
SHOP_ORIGIN=https://www.blinksmp.shop
SHOP_DATABASE_URL=<same MariaDB URL as MAP_DATABASE_URL; may omit to reuse MAP_DATABASE_URL>
STRIPE_SECRET_KEY=<Stripe test secret key>
STRIPE_WEBHOOK_SECRET=<webhook signing secret>
SHOP_CHECKOUT_ENABLED=true
SHOP_ALLOW_LIVE=false
```

Existing MAP_DATABASE_TLS_CERT pinning remains supported by the shared database connection helper; do not disable certificate checks. The website requires a recent UltimateShop backend heartbeat before opening checkout. No Stripe secret belongs in the Minecraft config. Payment amounts, recipient UUID and package selection are validated by the backend; public success/cancel URLs never grant a rank.

Create a **snapshot** Stripe webhook at:

```text
https://www.blinksmp.shop/shop/api/webhook
```

Requests to Stripe pin API version **2025-02-24.acacia**. Configure this webhook version too. Subscribe to:

```text
checkout.session.completed
checkout.session.async_payment_succeeded
invoice.paid
invoice.payment_failed
invoice.voided
invoice.marked_uncollectible
customer.subscription.updated
customer.subscription.deleted
customer.subscription.paused
customer.subscription.resumed
charge.refunded
charge.dispute.created
charge.dispute.closed
```

Configure Stripe's customer portal: allow cancel-at-period-end and billing/payment updates. **Disable subscription price/quantity changes and coupons**: packages are fixed USD monthly amounts. Shop checkout currently accepts cards (including eligible debit cards), not Cash App Pay as a separate payment method. Do not enable extra methods in this integration without testing.

On `https://www.blinksmp.shop/#store`, open a fresh `/shop` link, buy in test mode using Stripe's test card 4242 4242 4242 4242, verify the rank on both backends, test cancellation/refunds, and check your webhook deliveries. The page clearly labels test mode. Cancel-at-period-end retains the paid period; failed renewal never extends it. Immediate cancellation, full current-period refund and disputes remove that subscription overlay. Partial subscription refunds do not remove access. Pending checkout blocks a second membership purchase for up to 31 minutes; an active paid membership directs the player to the portal instead.

After successful end-to-end testing and Stripe account activation, replace the test key and signing secret with the corresponding live values, register the live webhook, and set `SHOP_ALLOW_LIVE=true`. Never use test purchases as evidence of live payment. Stripe fees, payout schedule, taxes, account verification, and refund handling are configured in Stripe; this plugin does not set them up or send payouts to Cash App.

## Delivery guarantees and recovery

Signed raw-body webhooks use a five-minute timestamp tolerance and exact-byte HMAC verification. Database transactions serialize entitlement changes and deduplicate event IDs and payment Session IDs. Subscriptions are retrieved from Stripe inside the shared transaction lock so delayed or concurrent events cannot replace newer state with older webhook content. Pending/unpaid checkouts never grant access. Tokens authorize only one Minecraft account; clients cannot submit executable commands or override price, identity or redirect URLs. Website return pages show only that payment was submitted, not that it was verified.

Offline purchases persist and are applied after the player joins any installed backend. If the database is down, new checkouts close and no access is invented. Previously loaded membership access expires at its existing deadline. If a webhook fails, Stripe retries it; inspect Stripe's delivery log and resend failed events after fixing configuration. Never delete/reset player inventory/network state to recover a purchase.

Local automated tests and CI cover signature failures/replay, unpaid/tampered payments, Bedrock UUIDs, cancellation/expiry, refunds, client parameter rejection, duplicate delivery, concurrent checkout reservation, shared MariaDB grants and manual suspension. Actual live Stripe charges and multiplayer playtests require the configured account and installed plugins.
