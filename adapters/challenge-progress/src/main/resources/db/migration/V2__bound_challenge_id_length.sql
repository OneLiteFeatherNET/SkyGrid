-- challenge_id was unbounded TEXT: only trusted literal ids exist today (e.g. "one_life"), but
-- nothing enforced that. Bounds it to a generous length for any admin- or config-defined id
-- without ever needing to hold a real block of text.
ALTER TABLE challenge_tier
    ADD CONSTRAINT challenge_id_length CHECK (char_length(challenge_id) <= 64);
