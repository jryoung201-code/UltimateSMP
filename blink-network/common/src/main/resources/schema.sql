-- BlinkNetwork protocol v2. Apply with the direct Neon URL using a migration role.
-- Deliberately separate from the website's schema until its contract is connected.
CREATE SCHEMA IF NOT EXISTS blinksmp_network_v1;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.meta (id integer PRIMARY KEY CHECK(id=1), version integer NOT NULL);
INSERT INTO blinksmp_network_v1.meta VALUES (1,1) ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.servers (
 id text PRIMARY KEY CHECK(id ~ '^BlinkSMP-(Overworld|Nether|End)-(0[1-9]|[1-9][0-9])$'),
 dimension text NOT NULL CHECK(dimension IN ('Overworld','Nether','End')),
 instance uuid, heartbeat timestamptz, enabled boolean NOT NULL DEFAULT false,
 CHECK(split_part(id,'-',2)=dimension), UNIQUE(id,dimension)
);
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.regions (
 dimension text NOT NULL CHECK(dimension IN ('Overworld','Nether','End')),
 region_x integer NOT NULL, region_z integer NOT NULL,
 server_id text NOT NULL REFERENCES blinksmp_network_v1.servers(id),
 revision bigint NOT NULL DEFAULT 1,
 PRIMARY KEY(dimension,region_x,region_z),
 FOREIGN KEY(server_id,dimension) REFERENCES blinksmp_network_v1.servers(id,dimension)
);
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.players (
 player_id uuid PRIMARY KEY,
 last_server text NOT NULL REFERENCES blinksmp_network_v1.servers(id),
 state text NOT NULL CHECK(state IN ('offline','active','handoff')),
 owner text REFERENCES blinksmp_network_v1.servers(id),
 session uuid, target text REFERENCES blinksmp_network_v1.servers(id), ticket uuid,
 payload bytea, rollback_payload bytea,
 updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(payload IS NULL OR octet_length(payload)<=2097152),
 CHECK((state='offline' AND owner IS NULL AND session IS NULL AND target IS NULL AND ticket IS NULL)
    OR (state='active' AND owner IS NOT NULL AND session IS NOT NULL AND target IS NULL AND ticket IS NULL)
    OR (state='handoff' AND owner IS NOT NULL AND session IS NOT NULL AND target IS NOT NULL AND ticket IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.audit (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 at timestamptz NOT NULL DEFAULT now(), action text NOT NULL, detail text NOT NULL
);

-- Additive profile synchronization table. Run this migration while the network is stopped.
-- Sanctions are deliberately separate from rank assignment for selective updates.
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.player_modules (
 player_id uuid NOT NULL,
 category text NOT NULL CHECK(category IN ('ranks','sanctions','homes','settings','advancements','statistics')),
 revision bigint NOT NULL CHECK(revision>=1),
 payload bytea NOT NULL CHECK(octet_length(payload)<=2097152),
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(player_id,category)
);
-- Additive network world border settings. Existing v2 plugins continue to run.
-- Every dimension has one border shared by its Minecraft backends.
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.world_borders (
 dimension text PRIMARY KEY CHECK(dimension IN ('Overworld','Nether','End')),
 center_x integer NOT NULL DEFAULT 0,
 center_z integer NOT NULL DEFAULT 0,
 size_blocks integer NOT NULL DEFAULT 1000 CHECK(size_blocks BETWEEN 144 AND 59999968 AND size_blocks % 2=0),
 enabled boolean NOT NULL DEFAULT false,
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>=1),
 updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(abs(center_x)::bigint+size_blocks/2<=29999984),
 CHECK(abs(center_z)::bigint+size_blocks/2<=29999984)
);

UPDATE blinksmp_network_v1.meta SET version=2 WHERE id=1 AND version=1;


-- v3: physical backends may host all three dimensions, restart draining and safe RTP.
ALTER TABLE blinksmp_network_v1.servers ADD COLUMN IF NOT EXISTS all_dimensions boolean NOT NULL DEFAULT false;
ALTER TABLE blinksmp_network_v1.servers ADD COLUMN IF NOT EXISTS maintenance boolean NOT NULL DEFAULT false;
ALTER TABLE blinksmp_network_v1.regions DROP CONSTRAINT IF EXISTS regions_server_id_dimension_fkey;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.retired_instances (
 server_id text NOT NULL REFERENCES blinksmp_network_v1.servers(id), instance uuid NOT NULL,
 PRIMARY KEY(server_id,instance)
);
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.queue_requests (
 player_id uuid PRIMARY KEY REFERENCES blinksmp_network_v1.players(player_id),
 origin text NOT NULL REFERENCES blinksmp_network_v1.servers(id), ticket uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS blinksmp_network_v1.rtp_points (
 server_id text NOT NULL REFERENCES blinksmp_network_v1.servers(id), dimension text NOT NULL CHECK(dimension IN ('Overworld','Nether','End')),
 x double precision NOT NULL, y double precision NOT NULL, z double precision NOT NULL,
 heartbeat timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(server_id,dimension)
);
UPDATE blinksmp_network_v1.meta SET version=3 WHERE id=1 AND version=2;

CREATE TABLE IF NOT EXISTS blinksmp_network_v1.queue_status (
 id text PRIMARY KEY, heartbeat timestamptz NOT NULL DEFAULT now(), ready boolean NOT NULL DEFAULT false
);

ALTER TABLE blinksmp_network_v1.players ADD COLUMN IF NOT EXISTS owner_instance uuid;

