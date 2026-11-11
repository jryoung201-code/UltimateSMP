-- BlinkNetwork protocol v1. Apply with the direct Neon URL using a migration role.
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
