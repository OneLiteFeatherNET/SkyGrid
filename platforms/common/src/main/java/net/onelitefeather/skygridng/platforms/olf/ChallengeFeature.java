package net.onelitefeather.skygridng.platforms.olf;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.adapters.challenge.ChallengeBehavior;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * OLF-only feature: enables every bound {@link ChallengeBehavior} on enable and disables all of them
 * on shutdown. This class deliberately knows no concrete challenge class; adding a fifth challenge
 * only means adding a new {@link ChallengeBehavior} implementation plus one
 * {@code Multibinder.addBinding()} line in {@link ChallengeModule}, never a change here.
 */
final class ChallengeFeature implements OlfFeature {

    private final JavaPlugin plugin;
    private final ChallengeAdminUseCase challenges;
    private final Set<ChallengeBehavior> behaviors;

    @Inject
    ChallengeFeature(JavaPlugin plugin, ChallengeAdminUseCase challenges, Set<ChallengeBehavior> behaviors) {
        this.plugin = plugin;
        this.challenges = challenges;
        this.behaviors = behaviors;
    }

    @Override
    public String id() {
        return "challenges";
    }

    @Override
    public void enable() {
        logConfigBehaviorMismatches();
        behaviors.forEach(ChallengeBehavior::enable);
    }

    @Override
    public void disable() {
        behaviors.forEach(ChallengeBehavior::disable);
    }

    /**
     * Startup diagnostic only: warns about a config-defined challenge id with no matching
     * {@link ChallengeBehavior} and about a behavior whose id has no matching config entry. Never
     * throws and never prevents {@link #enable()} from calling through to every behavior.
     */
    private void logConfigBehaviorMismatches() {
        Set<String> configuredIds = challenges.list().stream()
                .map(ChallengeRule::id)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> behaviorIds = behaviors.stream()
                .map(ChallengeBehavior::challengeId)
                .collect(Collectors.toUnmodifiableSet());

        configuredIds.stream()
                .filter(id -> !behaviorIds.contains(id))
                .forEach(id -> plugin.getLogger().warning(
                        "Configured challenge '" + id + "' has no matching ChallengeBehavior; it will never take effect."));

        behaviorIds.stream()
                .filter(id -> !configuredIds.contains(id))
                .forEach(id -> plugin.getLogger().warning(
                        "ChallengeBehavior '" + id + "' has no matching configured challenge entry."));
    }
}
