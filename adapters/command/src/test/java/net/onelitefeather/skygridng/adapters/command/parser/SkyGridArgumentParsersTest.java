package net.onelitefeather.skygridng.adapters.command.parser;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.BlockWeight;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.SkyGridConfig;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Black-box tests for {@link SkyGridArgumentParsers}, the Cloud suggestion providers backing
 * {@code /skygrid} tab completion. Each {@code @Suggestions} method is a plain
 * {@code (String input) -> List<String>} method (no Cloud context object involved), so it is called
 * directly here the same way {@link net.onelitefeather.skygridng.adapters.command.SkyGridCommandsTest}
 * calls {@code SkyGridCommands}'s subcommand methods directly: no real Cloud parsing/dispatch.
 *
 * Prefix filtering (case-insensitive {@code startsWith}) is the old hand-rolled
 * {@code SkyGridCommand.filter} behavior, now private to this class; it is exercised indirectly
 * through every suggestion method below rather than tested in isolation, since it is not exposed.
 */
class SkyGridArgumentParsersTest {

    private RuleAdminUseCase admin;
    private Presets presets;
    private ChallengeAdminUseCase challenges;
    private SkyGridArgumentParsers subject;

    @BeforeEach
    void setUp() {
        admin = mock(RuleAdminUseCase.class);
        presets = mock(Presets.class);
        challenges = mock(ChallengeAdminUseCase.class);
        subject = new SkyGridArgumentParsers(admin, presets, challenges);
    }

    private static GeneratorConfig minimalConfig(BlockWeight... blocks) {
        SkyGridConfig sky = new SkyGridConfig(4, 0, 64, 0.03, List.of(blocks), List.of(), List.of());
        return new GeneratorConfig("skygrid", sky);
    }

    @Test
    @DisplayName("help subcommand suggestions list every subcommand for an empty prefix")
    void helpSubcommandsListsEveryEntryForEmptyPrefix() {
        assertThat(subject.helpSubcommands(""))
                .containsExactly("reload", "algorithm", "presets", "worlds", "undo", "save", "preset",
                        "skyweight", "challenge");
    }

    @Test
    @DisplayName("help subcommand suggestions filter by prefix, case-insensitively")
    void helpSubcommandsFiltersByPrefixCaseInsensitively() {
        assertThat(subject.helpSubcommands("RE")).containsExactly("reload");
    }

    @Test
    @DisplayName("help subcommand suggestions preserve declaration order among multiple matches")
    void helpSubcommandsPreservesOrderAmongMultipleMatches() {
        assertThat(subject.helpSubcommands("s")).containsExactly("save", "skyweight");
    }

    @Test
    @DisplayName("preset name suggestions come from Presets.names, filtered by prefix")
    void presetNamesFiltersByPrefix() {
        when(presets.names()).thenReturn(List.of("classic", "nether", "noise"));

        assertThat(subject.presetNames("no")).containsExactly("noise");
    }

    @Test
    @DisplayName("preset name suggestions with no prefix return every preset")
    void presetNamesWithEmptyPrefixReturnsAll() {
        when(presets.names()).thenReturn(List.of("classic", "nether"));

        assertThat(subject.presetNames("")).containsExactly("classic", "nether");
    }

    @Test
    @DisplayName("algorithm id suggestions come from RuleAdminUseCase.algorithmIds, filtered by prefix")
    void algorithmIdsFiltersByPrefix() {
        when(admin.algorithmIds()).thenReturn(Set.of("skygrid", "noise_skygrid"));

        assertThat(subject.algorithmIds("noise")).containsExactly("noise_skygrid");
    }

    @Test
    @DisplayName("algorithm id suggestions with no prefix return every registered algorithm id")
    void algorithmIdsWithEmptyPrefixReturnsAll() {
        when(admin.algorithmIds()).thenReturn(Set.of("skygrid", "noise_skygrid"));

        assertThat(subject.algorithmIds("")).containsExactlyInAnyOrder("skygrid", "noise_skygrid");
    }

    @Test
    @DisplayName("material id suggestions come from the current SkyGrid block table, filtered by prefix")
    void materialIdsFiltersByPrefix() {
        when(admin.current()).thenReturn(minimalConfig(
                new BlockWeight(BlockId.of("minecraft:stone"), 30),
                new BlockWeight(BlockId.of("minecraft:dirt"), 20)));

        assertThat(subject.materialIds("minecraft:s")).containsExactly("minecraft:stone");
    }

    @Test
    @DisplayName("material id suggestions with no prefix return every configured material")
    void materialIdsWithEmptyPrefixReturnsAll() {
        when(admin.current()).thenReturn(minimalConfig(
                new BlockWeight(BlockId.of("minecraft:stone"), 30),
                new BlockWeight(BlockId.of("minecraft:dirt"), 20)));

        assertThat(subject.materialIds("")).containsExactly("minecraft:stone", "minecraft:dirt");
    }

    @Test
    @DisplayName("challenge id suggestions come from ChallengeAdminUseCase.list, filtered by prefix")
    void challengeIdsFiltersByPrefix() {
        when(challenges.list()).thenReturn(List.of(
                new ChallengeRule("no_crafting_table", "no tables", false),
                new ChallengeRule("no_nether", "no nether travel", true)));

        assertThat(subject.challengeIds("no_c")).containsExactly("no_crafting_table");
    }

    @Test
    @DisplayName("challenge id suggestions with no prefix return every known challenge id")
    void challengeIdsWithEmptyPrefixReturnsAll() {
        when(challenges.list()).thenReturn(List.of(
                new ChallengeRule("no_crafting_table", "no tables", false),
                new ChallengeRule("no_nether", "no nether travel", true)));

        assertThat(subject.challengeIds("")).containsExactly("no_crafting_table", "no_nether");
    }
}
