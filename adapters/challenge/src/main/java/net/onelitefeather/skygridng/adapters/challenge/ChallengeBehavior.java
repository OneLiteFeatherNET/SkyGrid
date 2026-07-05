package net.onelitefeather.skygridng.adapters.challenge;

/**
 * A self-registering challenge enforcement unit: the counterpart to {@code OlfFeature} at the
 * challenge level. Each implementation owns exactly one challenge id and knows how to hook itself
 * into (and unhook itself from) the Bukkit server, so adding a new challenge means adding a new
 * class plus one binding, never touching the generic composition code that enables/disables all of
 * them.
 */
public interface ChallengeBehavior {

    /** The stable id this behavior enforces, matching the corresponding {@code ChallengeRule.id()}. */
    String challengeId();

    /** Called once after the base generator is wired. Register listeners, commands, or tasks here. */
    void enable();

    /** Called once on shutdown. Release anything {@link #enable()} acquired. */
    void disable();
}
