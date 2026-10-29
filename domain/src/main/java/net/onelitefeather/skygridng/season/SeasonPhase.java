package net.onelitefeather.skygridng.season;

/**
 * The persisted phase of a season run. A transition moves the next season through {@code PREPARING} and
 * {@code PREPARED}, then the switch makes it {@code ACTIVE}, and the old season passes through
 * {@code CLOSING}, {@code UNLOADING} and {@code ARCHIVING} to {@code ARCHIVED}. The two failure phases
 * keep the run recoverable from its persisted state.
 */
public enum SeasonPhase {
    PLANNED,
    PREPARING,
    PREPARED,
    ACTIVE,
    CLOSING,
    UNLOADING,
    ARCHIVING,
    ARCHIVED,
    PREPARE_FAILED,
    ARCHIVE_FAILED
}
