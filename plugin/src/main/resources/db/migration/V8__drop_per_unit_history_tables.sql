-- The per-adapter Flyway history tables of the previous layout. Their migrations are recorded in
-- flyway_schema_history now: an existing database is baselined at version 7 before this runs, so only
-- this script runs against it, and a new database never had these tables at all.
DROP TABLE IF EXISTS flyway_history_challenge_progress;
DROP TABLE IF EXISTS flyway_history_task_progress;
DROP TABLE IF EXISTS flyway_history_season;
