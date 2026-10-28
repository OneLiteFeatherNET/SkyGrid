package net.onelitefeather.skygridng.api.model;

import java.time.Duration;

/**
 * The optional {@code season} block of {@code config}: how far ahead the next world is prepared, where
 * finished worlds are archived, and the minimum notice an admin must give before a scheduled end.
 *
 * @param prepareLead      how long before a boundary the next season's world is created, never negative
 * @param archiveDirectory the archive directory, relative to the server root or absolute, never blank
 * @param archiveCompress  whether an archived world is written as a compressed {@code .tar.zst} file
 * @param advanceMinNotice the minimum notice of {@code season advance confirm}, never negative
 */
public record SeasonSettings(
        Duration prepareLead,
        String archiveDirectory,
        boolean archiveCompress,
        Duration advanceMinNotice
) {

    /** The settings used when the {@code season} block is absent or leaves a key out. */
    public static SeasonSettings defaults() {
        return new SeasonSettings(Duration.ofMinutes(15), "archive", false, Duration.ofHours(24));
    }

    public SeasonSettings {
        if (prepareLead == null || prepareLead.isNegative()) {
            throw new IllegalArgumentException("SeasonSettings.prepareLead must not be negative");
        }
        if (archiveDirectory == null || archiveDirectory.isBlank()) {
            throw new IllegalArgumentException("SeasonSettings.archiveDirectory must not be blank");
        }
        if (advanceMinNotice == null || advanceMinNotice.isNegative()) {
            throw new IllegalArgumentException("SeasonSettings.advanceMinNotice must not be negative");
        }
    }
}
