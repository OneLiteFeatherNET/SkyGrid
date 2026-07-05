package net.onelitefeather.skygridng.api.model;

/**
 * A single toggleable challenge: a player/session facing rule that restricts allowed actions
 * (for example "no crafting table"), completely separate from the per-block {@link Rule} model.
 *
 * @param id          unique id, referenced by admin commands and the season scheduler
 * @param description a short, human readable summary of what the challenge restricts
 * @param active      whether the challenge is currently enforced
 */
public record ChallengeRule(String id, String description, boolean active) {
    public ChallengeRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ChallengeRule.id must not be blank");
        }
        description = description == null ? "" : description;
    }

    /** Returns a copy with a changed active state, all other fields unchanged. */
    public ChallengeRule withActive(boolean newActive) {
        return new ChallengeRule(id, description, newActive);
    }
}
