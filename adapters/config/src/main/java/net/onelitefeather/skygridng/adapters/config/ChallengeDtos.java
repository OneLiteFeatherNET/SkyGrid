package net.onelitefeather.skygridng.adapters.config;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Adapter-internal Jackson DTO for the per-challenge files ({@code challenges/<id>.<ext>}). Mirrors
 * the on-disk shape one to one, so the api and core never see Jackson. The id inside the file is
 * authoritative; the file name is only there for humans (see {@link JacksonChallengeRepository}).
 */
final class ChallengeDtos {

    private ChallengeDtos() {
    }

    /** One challenge rule: its id, a short human readable description, and whether it is active. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChallengeDto(String id, String description, Boolean active) {
    }
}
