package net.onelitefeather.skygridng.adapters.season;

import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonRun;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data Mapper between the {@link SeasonRunEntity} row and the immutable {@link SeasonRun} record. The record is
 * what {@code api}, {@code ports} and {@code core} see; the entity never leaves this package.
 */
final class SeasonRunMapper {

    private SeasonRunMapper() {
    }

    static SeasonRun toRecord(SeasonRunEntity entity) {
        return new SeasonRun(
                new SeasonId(entity.getSeasonId()),
                entity.getWorldKey(),
                entity.getSeed(),
                entity.getPhase(),
                entity.getAttempts(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getEndsAt(),
                decode(entity.getAnnounced()));
    }

    static SeasonRunEntity toEntity(SeasonRun run, Instant updatedAt) {
        var entity = new SeasonRunEntity();
        copyInto(run, entity, updatedAt);
        return entity;
    }

    static void copyInto(SeasonRun run, SeasonRunEntity entity, Instant updatedAt) {
        entity.setSeasonId(run.seasonId().value());
        entity.setWorldKey(run.worldKey());
        entity.setSeed(run.seed());
        entity.setPhase(run.phase());
        entity.setAttempts(run.attempts());
        entity.setStartedAt(run.startedAt());
        entity.setEndedAt(run.endedAt());
        entity.setEndsAt(run.endsAt());
        entity.setAnnounced(encode(run.announced()));
        entity.setUpdatedAt(updatedAt);
    }

    /** Announcement keys hold no comma (season ids, kinds and ISO instants do not), so a comma separates them. */
    static String encode(Set<String> keys) {
        return keys.isEmpty() ? null : keys.stream().sorted().collect(Collectors.joining(","));
    }

    static Set<String> decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(stored.split(",")).collect(Collectors.toUnmodifiableSet());
    }
}
