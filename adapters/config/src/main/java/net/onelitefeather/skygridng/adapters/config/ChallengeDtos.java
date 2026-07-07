package net.onelitefeather.skygridng.adapters.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Adapter-internal Jackson DTO for the per-challenge files ({@code challenges/<id>.<ext>}). Mirrors
 * the on-disk shape one to one, so the api and core never see Jackson. The id inside the file is
 * authoritative; the file name is only there for humans (see {@link JacksonChallengeRepository}).
 */
final class ChallengeDtos {

    private ChallengeDtos() {
    }

    /**
     * One challenge rule: its id, a short human readable description, whether it is active, its
     * ordered quest steps ({@code tasks}, empty or absent for a challenge with no quest), and whether
     * those steps must be completed in list order ({@code sequential}).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChallengeDto(String id, String description, Boolean active, List<TaskDto> tasks, Boolean sequential) {
    }

    /** One quest step of a challenge: its id, a short human readable description, and an optional reward. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TaskDto(String id, String description, RewardDto reward) {
    }

    /**
     * A reward granted on completing a task, as a flat DTO with a {@code type} discriminator
     * ({@code "item"}, {@code "command"} or {@code "message"}), mirroring this project's existing
     * {@code Rule}/{@code PlacementRule} DTO convention: manually switched in
     * {@link JacksonChallengeRepository}, not by a Jackson polymorphism annotation. Only the fields
     * relevant to {@code type} are populated; the others are {@code null}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RewardDto(String type, String itemId, Integer quantity, String command, String message) {
    }
}
