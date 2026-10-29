-- The effective end of the active season after a postponement or an advance (NULL: the configured end applies),
-- and the comma separated keys of the notices already sent for its boundary.
ALTER TABLE season_run ADD COLUMN ends_at TIMESTAMPTZ;
ALTER TABLE season_run ADD COLUMN announced TEXT;
