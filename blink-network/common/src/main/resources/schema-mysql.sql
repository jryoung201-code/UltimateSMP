-- Run only in the selected existing MySQL/MariaDB database. No CREATE DATABASE privilege is needed.
-- New installs remain at version 0 until every required table has been created.
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_meta (id INTEGER PRIMARY KEY CHECK(id=1),version INTEGER NOT NULL) ENGINE=InnoDB;
INSERT INTO blinksmp_network_v1_meta VALUES(1,0) ON DUPLICATE KEY UPDATE id=id;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_servers (
 id VARCHAR(64) PRIMARY KEY,dimension VARCHAR(16) NOT NULL CHECK(dimension IN ('Overworld','Nether','End')),
 instance CHAR(36),heartbeat DATETIME(6),enabled BOOLEAN NOT NULL DEFAULT false,
 all_dimensions BOOLEAN NOT NULL DEFAULT false,maintenance BOOLEAN NOT NULL DEFAULT false,
 UNIQUE(id,dimension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_regions (
 dimension VARCHAR(16) NOT NULL CHECK(dimension IN ('Overworld','Nether','End')),
 region_x INTEGER NOT NULL,region_z INTEGER NOT NULL,server_id VARCHAR(64) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 1 CHECK(revision>=1),PRIMARY KEY(dimension,region_x,region_z),
 FOREIGN KEY(server_id) REFERENCES blinksmp_network_v1_servers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_players (
 player_id CHAR(36) PRIMARY KEY,last_server VARCHAR(64) NOT NULL,state VARCHAR(16) NOT NULL CHECK(state IN ('offline','active','handoff')),
 owner VARCHAR(64),session CHAR(36),target VARCHAR(64),ticket CHAR(36),owner_instance CHAR(36),
 payload MEDIUMBLOB,rollback_payload MEDIUMBLOB,updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(last_server) REFERENCES blinksmp_network_v1_servers(id),
 FOREIGN KEY(owner) REFERENCES blinksmp_network_v1_servers(id),
 FOREIGN KEY(target) REFERENCES blinksmp_network_v1_servers(id),
 CHECK(payload IS NULL OR OCTET_LENGTH(payload)<=2097152),
 CHECK(rollback_payload IS NULL OR OCTET_LENGTH(rollback_payload)<=2097152),
 CHECK((state='offline' AND owner IS NULL AND session IS NULL AND target IS NULL AND ticket IS NULL)
    OR (state='active' AND owner IS NOT NULL AND session IS NOT NULL AND target IS NULL AND ticket IS NULL)
    OR (state='handoff' AND owner IS NOT NULL AND session IS NOT NULL AND target IS NOT NULL AND ticket IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),action VARCHAR(128) NOT NULL,detail TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_player_modules (
 player_id CHAR(36) NOT NULL,category VARCHAR(32) NOT NULL CHECK(category IN ('ranks','sanctions','homes','settings','advancements','statistics')),
 revision BIGINT NOT NULL CHECK(revision>=1),payload MEDIUMBLOB NOT NULL CHECK(OCTET_LENGTH(payload)<=2097152),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(player_id,category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_world_borders (
 dimension VARCHAR(16) PRIMARY KEY CHECK(dimension IN ('Overworld','Nether','End')),
 center_x INTEGER NOT NULL DEFAULT 0,center_z INTEGER NOT NULL DEFAULT 0,
 size_blocks INTEGER NOT NULL DEFAULT 1000 CHECK(size_blocks BETWEEN 144 AND 59999968 AND MOD(size_blocks,2)=0),
 enabled BOOLEAN NOT NULL DEFAULT false,revision BIGINT NOT NULL DEFAULT 1 CHECK(revision>=1),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK(ABS(CAST(center_x AS SIGNED))+size_blocks/2<=29999984),
 CHECK(ABS(CAST(center_z AS SIGNED))+size_blocks/2<=29999984)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_retired_instances (
 server_id VARCHAR(64) NOT NULL,instance CHAR(36) NOT NULL,PRIMARY KEY(server_id,instance),
 FOREIGN KEY(server_id) REFERENCES blinksmp_network_v1_servers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_queue_requests (
 player_id CHAR(36) PRIMARY KEY,origin VARCHAR(64) NOT NULL,ticket CHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(player_id) REFERENCES blinksmp_network_v1_players(player_id),
 FOREIGN KEY(origin) REFERENCES blinksmp_network_v1_servers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_rtp_points (
 server_id VARCHAR(64) NOT NULL,dimension VARCHAR(16) NOT NULL CHECK(dimension IN ('Overworld','Nether','End')),
 x DOUBLE NOT NULL,y DOUBLE NOT NULL,z DOUBLE NOT NULL,heartbeat DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(server_id,dimension),FOREIGN KEY(server_id) REFERENCES blinksmp_network_v1_servers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS blinksmp_network_v1_queue_status (
 id VARCHAR(64) PRIMARY KEY,heartbeat DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),ready BOOLEAN NOT NULL DEFAULT false
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
UPDATE blinksmp_network_v1_meta SET version=3 WHERE id=1 AND version=0;
