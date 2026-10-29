package net.onelitefeather.skygridng.adapters.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter-internal Jackson DTO for the per-season files ({@code seasons/<id>.<ext>}). It mirrors the on-disk
 * shape one to one, with the instant and the duration kept as ISO-8601 strings, so the api and core never
 * see Jackson. The id inside the file is authoritative; the file name is only there for humans.
 */
final class SeasonDtos {

    /** A duration in whole weeks, for example {@code P8W}, which {@link Duration#parse} does not accept. */
    private static final Pattern WEEKS = Pattern.compile("P(\\d+)W");

    private SeasonDtos() {
    }

    /**
     * One season: its id, the display name (MiniMessage), the start instant, the fixed duration, the preset,
     * the challenge ids, and an optional fixed seed ({@code null} means a random seed at prepare time).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SeasonDto(
            String id,
            String displayName,
            String start,
            String duration,
            String preset,
            List<String> challenges,
            Long seed
    ) {
    }

    /**
     * Parses an ISO-8601 duration. A whole number of weeks ({@code P8W}) is accepted in addition to the
     * forms {@link Duration#parse} understands.
     */
    static Duration parseDuration(String text) {
        Matcher weeks = WEEKS.matcher(text);
        if (weeks.matches()) {
            return Duration.ofDays(7L * Long.parseLong(weeks.group(1)));
        }
        return Duration.parse(text);
    }
}
