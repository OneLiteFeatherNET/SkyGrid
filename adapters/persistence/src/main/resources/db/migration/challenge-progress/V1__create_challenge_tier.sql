-- Per-player, per-challenge tier progression. One row per (player, challenge) pair; the whole
-- point of this table is that it holds nothing but the tier count itself (no name, no timestamps,
-- no history), per the data minimization requirement on this schema.
CREATE TABLE challenge_tier (
    player_id UUID NOT NULL,
    challenge_id TEXT NOT NULL,
    tier INT NOT NULL CHECK (tier >= 0),
    PRIMARY KEY (player_id, challenge_id)
);
