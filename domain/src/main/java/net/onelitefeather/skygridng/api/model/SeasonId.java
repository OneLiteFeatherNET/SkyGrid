package net.onelitefeather.skygridng.api.model;

import java.util.regex.Pattern;

/**
 * The id of a season, for example {@code s01_overgrowth}. It is referenced by season files, the season
 * run table and every season scoped progress row, so it is kept short and lower case.
 *
 * @param value the id, matching {@code [a-z0-9_]{1,64}}
 */
public record SeasonId(String value) {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9_]{1,"
            + ChallengeRule.MAX_ID_LENGTH + "}");

    /** The id of the implicit season that runs when no season is configured. */
    public static final SeasonId LEGACY = new SeasonId("legacy");

    public SeasonId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("SeasonId must match [a-z0-9_]{1,"
                    + ChallengeRule.MAX_ID_LENGTH + "}: " + value);
        }
    }
}
