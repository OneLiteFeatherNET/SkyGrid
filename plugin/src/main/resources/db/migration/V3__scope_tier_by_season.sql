-- Season scoping. Every existing row belongs to the implicit season "legacy": the DEFAULT assigns it to
-- the rows that already exist, and the default is dropped again so new writes must name their season.
ALTER TABLE challenge_tier
    ADD COLUMN season_id TEXT NOT NULL DEFAULT 'legacy' CHECK (char_length(season_id) <= 64);
ALTER TABLE challenge_tier
    ALTER COLUMN season_id DROP DEFAULT;
ALTER TABLE challenge_tier
    DROP CONSTRAINT challenge_tier_pkey;
ALTER TABLE challenge_tier
    ADD PRIMARY KEY (season_id, player_id, challenge_id);
