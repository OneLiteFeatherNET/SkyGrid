package net.onelitefeather.skygridng.adapters.challengeprogress;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity mapping of the {@code challenge_tier} table (schema owned by
 * {@code V1__create_challenge_tier.sql}, never altered by Hibernate: see {@code persistence.xml}'s
 * {@code hibernate.hbm2ddl.auto=validate}).
 *
 * <p>Deliberately not a record, unlike every other value type in this project: Hibernate needs a
 * mutable, proxyable class for its dirty-checking and (potentially) lazy-loading machinery, which a
 * record's all-final-fields, no-arg-constructor-free shape cannot provide. This is a documented,
 * narrow exception to the project's "everything is a record" house style, confined to this one JPA
 * entity class; the composite id ({@link ChallengeTierId}) and every other type in this module stay
 * immutable records. The protected no-arg constructor exists for Hibernate only and must never be
 * called from application code, which always uses the public constructor.
 */
@Entity
@Table(name = "challenge_tier")
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
