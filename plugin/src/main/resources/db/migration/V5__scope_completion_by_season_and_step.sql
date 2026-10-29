-- Season and prestige step scoping. Every existing completion becomes season "legacy" at prestige step 0,
-- so no player loses a completion. The defaults only fill the existing rows and are dropped afterwards.
ALTER TABLE task_completion
    ADD COLUMN season_id TEXT NOT NULL DEFAULT 'legacy' CHECK (char_length(season_id) <= 64),
    ADD COLUMN prestige_step SMALLINT NOT NULL DEFAULT 0 CHECK (prestige_step BETWEEN 0 AND 10);
ALTER TABLE task_completion
    ALTER COLUMN season_id DROP DEFAULT,
    ALTER COLUMN prestige_step DROP DEFAULT;
ALTER TABLE task_completion
    DROP CONSTRAINT task_completion_pkey;
ALTER TABLE task_completion
    ADD PRIMARY KEY (season_id, player_id, challenge_id, prestige_step, task_id);
