# UltimateTempGrants 1.0.0

Redeemable, custom-named PAPER items for timed UltimateRanks roles or exact permission nodes. Paper 1.21.11 / Java 21. Timers start on redemption, continue offline, survive restarts, and expire without removing permanent ranks/permissions. Redeeming another voucher for the same grant extends its remaining duration. Timers accept one positive unit: `s`, `m`, `h`, `d`, `w`, with a maximum total remaining time of 3650 days.

## Installation

Stop both Paper backends. Back up plugin data. Install UltimateTempGrants-1.0.0.jar and replace UltimateRanks with UltimateRanks-1.3.2.jar on BOTH backends, removing older duplicate JARs but keeping their data folders. Replace UltimateCrates with UltimateCrates-1.0.1.jar if using voucher rewards in crates. Fully restart both backends; do not /reload. No Velocity update is needed.

The rank update adds a temporary overlay API and preserves player files, rank config, permanent grants and existing network profile synchronization. Temporary overlays are never exported as permanent ranks. Display weights and staff authority use the currently active overlay; expiry removes only that overlay. Permission attachments are removed rather than replaced with false permissions. Existing plugins must actually check a permission node for it to have an effect; giving a permission node does not install another plugin or bypass rank-only staff checks.

## Commands

Run give commands on the backend the recipient is currently using. Console, OP, OWNER or players with `ultimatetempgrants.admin` can issue vouchers. The user needs `ultimatetempgrants.use` to redeem, which defaults to true.

```
/tempgrant give RealJR_25736 rank Ultimate+ 7d 1 &6Ultimate+ &e7-Day Voucher
/tempgrant give RealJR_25736 rank Ultimate+++ 30d 1 &dUltimate+++ Monthly Pass
/tempgrant give RealJR_25736 perm maxhomes.9 1d 1 &bNine Homes for One Day
/tempgrant give RealJR_25736 perm edit.spawn 30m 1 &6Temporary Spawn Builder
/tempgrant list
/tempgrant list RealJR_25736
```

Syntax: `/tempgrant give <online-player> <rank|perm> <target> <timer> [amount] [custom name...]`. Amount defaults to one, maximum 36. Each voucher occupies one inventory slot. `/voucher` and `/tempvoucher` are aliases. Right-click the item in either hand to redeem it. Names accept `&` color codes; default names are configurable in config.yml. Supported rank IDs are the existing UltimateRanks roles; use `TRIAL_MOD` as one word. Permissions must be exact nodes; wildcards are rejected.

The timer does not run while a voucher is unused. Expiry enforcement and displayed tags refresh once per second on online players. Other backend instances discover changes on the configured refresh interval, five seconds by default. Offline players load only unexpired grants after reconnecting. `/tempgrant list` shows active grants from the local backend's latest refresh.

## Network and persistence

Default storage `auto` uses BlinkNetwork's existing MySQL/MariaDB credentials, including its environment overrides. It creates only `ultimatesmp_temp_vouchers_v1` and `ultimatesmp_temp_grants_v1`; it does not alter inventory, homes, rank profile or routing tables. Install on every backend for network-wide expiry and redemption. Without BlinkNetwork, auto uses local data.yml. Explicit `storage: local` does not synchronize between servers. Do not switch modes expecting data migration.

Voucher IDs are recorded at issue time. Redemption marks the ID used and updates the timed grant in one database transaction (or one atomic local file replacement). Copies of the same item share the ID and cannot grant extra time. A confirmed grant is durable before the spent physical item is removed. If a player disconnects/transfers during confirmation, a spent copy may remain; it cannot be used again. Check `/tempgrant list` before assuming a retry failed. Database failure pauses new redemptions; already loaded timers still expire. Permanent grants managed by other plugins remain untouched.

## UltimateCrates rewards

With UltimateCrates **1.0.1** and this plugin installed, copy an issued voucher into the crate reward editor and SAVE. Each awarded reward gets a fresh, persisted voucher ID before the crate consumes its key. Different winners can redeem their own prizes. Keep the original template item if desired; it is not consumed by copying it into the editor. Crates 1.0.0 lacks this integration and must be updated. Copying the same voucher manually into a different giveaway system does not create independent IDs.

A crate click waits up to ten seconds for voucher issuance. The player's native inventory readiness, actual key and available space are checked again before applying the reward. Failed/disconnected claims keep the key. Unclaimed issued IDs can remain harmlessly in storage; they provide no grant without an item carrying their random ID.

## Verification

CI checks timer parsing and caps, offline/expired extension, one-use local persistence, duplicate redemption races against real MariaDB, independent crate-copy issuance, and preservation of permanent rank assignments after overlay expiry. Existing rank and crate verification also runs. Live client interactions and production installation have not been performed.
