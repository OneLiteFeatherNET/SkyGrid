package net.onelitefeather.skygridng.challenge;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito-only tests for {@link ChallengeFeature}. It knows only the generic
 * {@link ChallengeBehavior} set (never a concrete challenge class), so its enable/disable
 * delegation and its mismatch diagnostic are both verified purely through interactions with mocked
 * behaviors and a mocked {@link ChallengeAdminUseCase}.
 */
class ChallengeFeatureTest {

    private JavaPlugin plugin;
    private Logger logger;
    private ChallengeAdminUseCase challenges;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        challenges = mock(ChallengeAdminUseCase.class);
    }

    private static ChallengeBehavior behavior(String id) {
        ChallengeBehavior behavior = mock(ChallengeBehavior.class);
        when(behavior.challengeId()).thenReturn(id);
        return behavior;
    }

    @Test
    @DisplayName("id returns the stable feature id")
    void idReturnsChallenges() {
        ChallengeFeature feature = new ChallengeFeature(plugin, challenges, Set.of());

        assertThat(feature.id()).isEqualTo("challenges");
    }

    @Test
    @DisplayName("enable calls enable on every bound behavior")
    void enableCallsEnableOnEveryBehavior() {
        when(challenges.list()).thenReturn(List.of(
                new ChallengeRule("no_crafting_table", "", false),
                new ChallengeRule("one_life", "", false)));
        ChallengeBehavior first = behavior("no_crafting_table");
        ChallengeBehavior second = behavior("one_life");
        ChallengeFeature feature = new ChallengeFeature(plugin, challenges, Set.of(first, second));

        feature.enable();

        verify(first).enable();
        verify(second).enable();
    }

    @Test
    @DisplayName("disable calls disable on every bound behavior")
    void disableCallsDisableOnEveryBehavior() {
        ChallengeBehavior first = behavior("no_crafting_table");
        ChallengeBehavior second = behavior("one_life");
        ChallengeFeature feature = new ChallengeFeature(plugin, challenges, Set.of(first, second));

        feature.disable();

        verify(first).disable();
        verify(second).disable();
    }

    @Test
    @DisplayName("a configured challenge with no matching behavior still enables every behavior and logs a warning")
    void configuredChallengeWithNoBehaviorStillEnablesEveryBehavior() {
        when(challenges.list()).thenReturn(List.of(new ChallengeRule("ghost_challenge", "", false)));
        ChallengeBehavior onlyBehavior = behavior("one_life");
        ChallengeFeature feature = new ChallengeFeature(plugin, challenges, Set.of(onlyBehavior));

        feature.enable();

        verify(onlyBehavior).enable();
        verify(logger).warning(
                "Configured challenge 'ghost_challenge' has no matching ChallengeBehavior; it will never take effect.");
    }

    @Test
    @DisplayName("a behavior with no matching configured challenge still enables every behavior and logs a warning")
    void behaviorWithNoConfiguredChallengeStillEnablesEveryBehavior() {
        when(challenges.list()).thenReturn(List.of());
        ChallengeBehavior orphanBehavior = behavior("orphan_behavior");
        ChallengeFeature feature = new ChallengeFeature(plugin, challenges, Set.of(orphanBehavior));

        feature.enable();

        verify(orphanBehavior).enable();
        verify(logger).warning(
                "ChallengeBehavior 'orphan_behavior' has no matching configured challenge entry.");
    }

    @Test
    @DisplayName("matching configured challenges and behaviors enable cleanly without any warning")
    void matchingConfigAndBehaviorsLogsNothing() {
        when(challenges.list()).thenReturn(List.of(new ChallengeRule("one_life", "", false)));
        ChallengeBehavior onlyBehavior = behavior("one_life");
        ChallengeFeature feature = new ChallengeFeature(plugin, challenges, Set.of(onlyBehavior));

        feature.enable();

        verify(onlyBehavior).enable();
        verify(logger, never()).warning(anyString());
    }
}
