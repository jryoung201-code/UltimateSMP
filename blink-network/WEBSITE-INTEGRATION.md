# Contract for /map/ and /map/admin/

This release reads `blinksmp_network_v1`, not the earlier illustrative `blinksmp_network` schema. The website developer must explicitly integrate this contract. Do not create a second independent source of active ownership.

Coordinates: `dimension` is `Overworld`, `Nether`, or `End`. Region x/z are floor(block coordinate /144). IDs match `BlinkSMP-(Overworld|Nether|End)-(0[1-9]|[1-9][0-9])`.

## Public reads

Server-side query `regions(dimension,region_x,region_z,server_id,revision)` joined to `servers(id,dimension,heartbeat,enabled)`. A server is reported available only when enabled and heartbeat > now()-15 seconds. A server can still be unreachable despite a recent heartbeat; transfers recheck and may fail. Never expose the `players` table or private database credentials. Use pagination/bounds, caching and indexes for large maps.

`servers.instance` is a process identity used to detect duplicate IDs; it is not an admin-editable setting. Backend network addresses stay in Velocity's private configuration and are not stored here.

## Initial ownership

The admin API may INSERT a previously unassigned region only after validating server dimension and confirming the region's world data has been prepared. Existing assignments are immutable in 0.1.0. Never run an UPSERT/UPDATE/DELETE to move ownership: the plugin has no chunk migration service yet. Offer reassignment as an unavailable/pending feature, never silently activate it.

Protect edits with the site's existing administrator authorization and an audit trail; the Minecraft runtime does not provide website login. Website DB roles should get only SELECT on server/region tables and, where the admin backend requires it, INSERT on regions plus its own audit table. They must not update player/session rows. Runtime identities should get only privileges needed for their server/session operations; this release's SQL API trusts the operated backend plugins, not arbitrary third-party server operators.

## Player handoff internals

The `players` table is private runtime state. `offline` has no session owner; `active` has a random UUID session; `handoff` retains the source session plus target and one-time ticket. Source updates require their exact session and active state. Target claims use row locks, replace the session and clear the ticket. Never let website admin assignment operations rewrite these rows.

No JWT/HTTP interface is implemented in this release. Plugins use PostgreSQL over TLS; proxy switches use a handled backend-only plugin-message channel plus DB verification. If the website already exposes an authenticated ownership API, send that API specification to the plugin developer before switching transport. Do not invent matching endpoints and assume they exist.

## Terrain and migration

The runtime does not yet produce tile imagery or process migration jobs. Render a coordinate grid/ownership overlay until terrain tiles are connected. A 9x9-chunk ownership region is not a 32x32-chunk .mca region file. Do not copy whole .mca files to implement reassignment, edit loaded world files, or imply the map editor has moved builds.


## Version 0.3.0 / schema 3 changes

Physical server IDs retain their existing naming contract. A gameplay backend with `servers.all_dimensions=true` supplies effective ownership for a missing Nether/End region from its Overworld region at the SAME numeric region_x/region_z. Explicit dimension assignments take precedence. Both `/map/` and `/map/admin/` must show this effective ownership, not only raw rows. Suggested read query:

```sql
WITH effective AS (
 SELECT dimension,region_x,region_z,server_id,0 AS priority FROM blinksmp_network_v1.regions
 UNION ALL
 SELECT d.dimension,r.region_x,r.region_z,r.server_id,1
 FROM blinksmp_network_v1.regions r
 JOIN blinksmp_network_v1.servers s ON s.id=r.server_id AND s.all_dimensions
 CROSS JOIN (VALUES ('Nether'),('End')) d(dimension)
 WHERE r.dimension='Overworld'
)
SELECT DISTINCT ON(dimension,region_x,region_z) dimension,region_x,region_z,server_id
FROM effective ORDER BY dimension,region_x,region_z,priority;
```

The old composite dimension FK is removed by the migration. Validate new explicit owners against `all_dimensions OR servers.dimension=region.dimension` in the authenticated admin API. Do not silently turn a mirror into a reassignment or overwrite existing owned terrain; ownership migration remains a separate offline operation. Display `servers.maintenance` as maintenance even when its heartbeat is fresh. Queue is registered in Velocity, not as a terrain owner in the servers table. Never let the browser mutate player locks, queue tickets, retired instances, RTP points or queue health; those are trusted plugin-only state. Back up and migrate with all old plugin processes stopped.
