-- One row per season run: the persisted runtime fact of a season (its world, seed and phase). Season
-- definitions stay in config. "version" backs optimistic locking in the JPA mapping (@Version).
CREATE TABLE season_run (
    season_id  TEXT PRIMARY KEY CHECK (char_length(season_id) <= 64),
    world_key  TEXT NOT NULL CHECK (char_length(world_key) <= 128),
    seed       BIGINT NOT NULL,
    phase      TEXT NOT NULL CHECK (phase IN ('PLANNED', 'PREPARING', 'PREPARED', 'ACTIVE', 'CLOSING',
               'UNLOADING', 'ARCHIVING', 'ARCHIVED', 'PREPARE_FAILED', 'ARCHIVE_FAILED')),
    attempts   INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    started_at TIMESTAMPTZ,
    ended_at   TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT NOT NULL DEFAULT 0
);
-- The one guarantee that lives only in the database: at most one ACTIVE row can exist at any time.
CREATE UNIQUE INDEX season_run_single_active ON season_run ((phase)) WHERE phase = 'ACTIVE';
