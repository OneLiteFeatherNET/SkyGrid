-- Throwaway two-column table used only by AbstractJpaRepositoryTest to exercise the shared
-- AbstractJpaRepository read/inTransaction lifecycle in isolation. Never referenced by any real
-- adapter and never shares a persistence unit or table name with challenge_tier.
CREATE TABLE test_item (
    id TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
