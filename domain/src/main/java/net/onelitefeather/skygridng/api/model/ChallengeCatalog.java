package net.onelitefeather.skygridng.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * All challenge rules, keyed by their id. A challenge rule is a player/session facing switch
 * (allowed actions), a different axis from the per-block {@link RuleCatalog}. An empty catalog
 * means "no challenges defined".
 *
 * @param challenges the rules by id, an immutable ordered map (never {@code null})
 */
public record ChallengeCatalog(Map<String, ChallengeRule> challenges) {
    public ChallengeCatalog {
        challenges = challenges == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(challenges));
    }

    /** An empty catalog (no challenges). */
    public static ChallengeCatalog empty() {
        return new ChallengeCatalog(Map.of());
    }

    /** The challenge rule with {@code id}, if any. */
    public Optional<ChallengeRule> forId(String id) {
        return Optional.ofNullable(challenges.get(id));
    }

    /** Returns a copy with {@code rule} added or replaced. */
    public ChallengeCatalog with(ChallengeRule rule) {
        Map<String, ChallengeRule> copy = new LinkedHashMap<>(challenges);
        copy.put(rule.id(), rule);
        return new ChallengeCatalog(copy);
    }
}
