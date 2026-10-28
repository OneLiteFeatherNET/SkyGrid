package net.onelitefeather.skygridng.adapters.season;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.SeasonPhase;

import java.time.Instant;

/**
 * JPA entity mapping of the {@code season_run} table (schema owned by {@code V1__create_season_run.sql},
 * never altered by Hibernate). The only JPA class of the season run: {@link SeasonRunMapper} converts it
 * to and from the immutable {@code SeasonRun} record, so no {@code jakarta.persistence} type leaves this package.
 *
 * <p>Mutable on purpose (Hibernate dirty checking and {@code @Version}). Only the mapper and the repository
 * change it, through the package private methods below.
 */
@Entity
@Table(name = "season_run")
@NamedQueries({
        @NamedQuery(
                name = "SeasonRunEntity.active",
                query = "select r from SeasonRunEntity r where r.phase = :phase"),
        @NamedQuery(
                name = "SeasonRunEntity.inFlight",
                query = "select r from SeasonRunEntity r where r.phase not in :excluded")
})
public class SeasonRunEntity {

    // The id is the column text, not SeasonId: Hibernate 6.6 did not apply SeasonIdConverter to a
    // @Id attribute, so the mapper converts to and from SeasonId at this boundary.
    @Id
    @Column(name = "season_id", nullable = false, length = ChallengeRule.MAX_ID_LENGTH)
    private String seasonId;

    @Column(name = "world_key", nullable = false, length = 128)
    private String worldKey;

    @Column(name = "seed", nullable = false)
    private long seed;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false)
    private SeasonPhase phase;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** For Hibernate's proxy and reflection machinery only; never call this from application code. */
    protected SeasonRunEntity() {
    }

    String getSeasonId() {
        return seasonId;
    }

    void setSeasonId(String seasonId) {
        this.seasonId = seasonId;
    }

    String getWorldKey() {
        return worldKey;
    }

    void setWorldKey(String worldKey) {
        this.worldKey = worldKey;
    }

    long getSeed() {
        return seed;
    }

    void setSeed(long seed) {
        this.seed = seed;
    }

    SeasonPhase getPhase() {
        return phase;
    }

    void setPhase(SeasonPhase phase) {
        this.phase = phase;
    }

    int getAttempts() {
        return attempts;
    }

    void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    Instant getEndedAt() {
        return endedAt;
    }

    void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** The optimistic lock version: 0 after the insert, incremented by every flushed update. */
    long getVersion() {
        return version;
    }

    /** The outgoing side of a switch: the run leaves {@code ACTIVE} at {@code endedAt}. */
    void close(Instant endedAt) {
        this.phase = SeasonPhase.CLOSING;
        this.endedAt = endedAt;
        this.updatedAt = endedAt;
    }

    /** The incoming side of a switch: the run becomes {@code ACTIVE} at {@code startedAt}. */
    void activate(Instant startedAt) {
        this.phase = SeasonPhase.ACTIVE;
        this.startedAt = startedAt;
        this.updatedAt = startedAt;
    }
}
