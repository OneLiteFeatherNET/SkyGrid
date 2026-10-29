-- Per-player, per-challenge, per-task completion. One row per (player, challenge, task) triple; the
-- whole point of this table is that row existence alone is the fact needed (no name, no
-- timestamps, no history, no status column), per the data minimization requirement on this schema.
CREATE TABLE task_completion (
    player_id UUID NOT NULL,
    challenge_id TEXT NOT NULL CHECK (char_length(challenge_id) <= 64),
    task_id TEXT NOT NULL CHECK (char_length(task_id) <= 64),
    PRIMARY KEY (player_id, challenge_id, task_id)
);
