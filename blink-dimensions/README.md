# BlinkDimensions 0.1.0 — separate dimension portal routing

A **separate Paper plugin** for moving players through Nether and End portals between your existing BlinkSMP dimension servers:

- Overworld -> Nether; Nether -> Overworld
- Overworld -> End; End -> Overworld
- \`/blinkdimensions status\`, \`/blinkdimensions check Nether\`, \`/blinkdimensions travel Nether\` (operator only), and \`/blinkdimensions reload\`
- Safe, manually configured landing hubs per dimension. No automatic unsafe portal placement, coordinate scaling, or world modification.
- No own inventory files, Neon credentials, region-grid handling, or unverified Velocity \`/server\` switch.
- Uses BlinkNetwork 0.2.4+ for **the same atomic player data snapshot, validated region ownership and fenced Velocity handoff** as existing Overworld transfers.

## Important compatibility requirement

**BlinkDimensions is separate, but relies on BlinkNetwork 0.2.4+ on every Paper backend and BlinkNetwork-Velocity on the proxy.** Install both Paper plugins on Overworld 01, Overworld 02 and Nether 01. Install on End 01 after you create it. Keep the BlinkNetwork region map in place; do not uninstall BlinkNetwork from Nether or your existing Overworld servers. Do not install another independent inventory-sync or server-switcher plugin alongside this path.

This dependency is intentional: removing BlinkNetwork while keeping the Overworld region network would allow independent inventory writers and risk loss/duplication. BlinkDimensions only owns the *portal decision*; BlinkNetwork owns the data handoff.

## Installation

1. Back up server world and player data. Install updated **BlinkNetwork-Paper-0.2.4.jar** and **BlinkDimensions-0.1.0.jar** in every backend's \`plugins/\` directory. Install **BlinkNetwork-Velocity-0.2.4.jar** on the proxy. Fully restart; never use \`/reload\`.
2. Keep all existing **Neon** BlinkNetwork settings, assignments and server IDs unchanged.
3. Fix Velocity modern forwarding independently; Paper \`online-mode=false\`, \`proxies.velocity.enabled=true\` and matching secret. Unless your hosting proxy actually sends HAProxy headers, set Paper \`proxies.proxy-protocol=false\`. Restrict direct access to offline-mode backends.
4. Make a safe arrival pad on each destination world, e.g. \`world_nether\` on Nether 01, away from lava, portals and walls. Ensure its exact X/Z region is assigned to that backend in Neon, and Y is safe.
5. Edit \`plugins/BlinkDimensions/config.yml\` on **every** backend. Set \`arrivals.Nether.x/y/z\`, \`arrivals.Overworld.x/y/z\`, and eventually \`arrivals.End.x/y/z\` to those verified pads; set \`enabled: true\` for each completed destination. The default examples are **placeholders and disabled**, NOT guaranteed safe blocks.
6. Run \`/blinkdimensions check Nether\` as an operator on Overworld 01. It must report an available remote assigned hub before you try a portal. Run \`/blinkdimensions travel Nether\` as an operator for the first controlled test. Check inventory and successful return before admitting other players.
7. Nether / End portal events are cancelled locally and replaced with a BlinkNetwork handoff only when the corresponding arrival hub is enabled. If the destination is offline or unassigned, no transfer occurs and the player stays at the source.
8. Set \`allow-nether=true\` and keep destination worlds available for portal events. Your Velocity \`[servers]\` names must match \`BlinkSMP-Nether-01\`, etc. **Do not** change \`try\` to force Nether; BlinkNetwork overrides initial routing based on saved coordinates.

## Important limitations

- First release routes all travelers to **configured fixed arrival hubs**, not vanilla 1:8 Nether portal linkage. It does not create exit portals or guarantee safe terrain. Staff must build safe arrival pads.
- End gateways within End are not cross-server portals and retain normal handling.
- Backends still need a valid Velocity address, matching modern forwarding secret, and healthy BlinkNetwork / Neon connections. This plugin cannot fix forwarding or port misconfiguration.
- There is no new Velocity plugin. Only BlinkNetwork-Velocity handles ticket-verified server changes. Ordinary \`/server\` is still denied by design.
- To avoid duplicate handoffs, the matching BlinkNetwork Paper build skips its own Nether/End portal handler when BlinkDimensions is installed and active.

## Build

\`\`\`sh
mvn -B -ntp -f blink-dimensions/pom.xml verify
mvn -B -ntp -f blink-network/pom.xml verify
\`\`\`

Both JARs are produced by GitHub Actions under **Build BlinkDimensions** and **Build BlinkNetwork**. Install matching JAR versions together.
