package net.onelitefeather.skygridng.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

/**
 * JPA entity mapping of the {@code challenge_tier} table (schema owned by the Flyway migrations, never
 * altered by Hibernate: see {@code persistence.xml}'s {@code hibernate.hbm2ddl.auto=validate}).
 *
 * <p>Deliberately not a record: Hibernate needs a mutable, proxyable class for its dirty-checking. The
 * composite id ({@link ChallengeTierId}) stays an immutable record. The protected no-arg constructor
 * exists for Hibernate only.
 */
@Entity
@Table(name = "challenge_tier")
@NamedQueries({
        @NamedQuery(
                name = "ChallengeTierEntity.historyOf",
                query = "select e from ChallengeTierEntity e where e.id.playerId = :playerId"),
        @NamedQuery(
                name = "ChallengeTierEntity.deleteBySeason",
                query = "delete from ChallengeTierEntity e where e.id.seasonId = :seasonId")
})
public class ChallengeTierEntity {

    @EmbeddedId
    private ChallengeTierId id;

    @Column(name = "tier", nullable = false)
    private int tier;

    /** For Hibernate's proxy and reflection machinery only; never call this from application code. */
    protected ChallengeTierEntity() {
    }

    public ChallengeTierEntity(ChallengeTierId id, int tier) {
        this.id = id;
        this.tier = tier;
    }

    public ChallengeTierId getId() {
        return id;
    }

    public int getTier() {
        return tier;
    }

    /** Increments the stored tier by one; relies on Hibernate's dirty checking to flush on commit. */
    public void incrementTier() {
        this.tier = this.tier + 1;
    }
}
