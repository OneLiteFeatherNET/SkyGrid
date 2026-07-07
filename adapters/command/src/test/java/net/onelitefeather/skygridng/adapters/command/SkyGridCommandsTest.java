package net.onelitefeather.skygridng.adapters.command;

import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Black-box tests for {@link SkyGridCommands} against the three in ports it takes
 * ({@link RuleAdminUseCase}, {@link Presets}, {@link ChallengeAdminUseCase}), Mockito-mocked, and a
 * hand-written {@link FakeCommandSource} instead of a Bukkit {@code CommandSender}.
 *
 * Every subcommand is now a plain method called directly (no real Cloud parsing/dispatch); the
 * former manual permission check moved to Cloud's own gate and is exercised here by calling
 * {@link SkyGridCommands#handleNoPermission} directly, the way a real dispatch invokes it when Cloud
 * throws {@code NoPermissionException} instead of a subcommand method.
 * {@link SkyGridCommands#handleInvalidSyntax} is exercised the same way for completeness, but on a
 * real, running Paper server that exception handler never actually fires for an unrecognized
 * subcommand (see its javadoc); {@link SkyGridCommands#unmatched} is the method a live dispatch
 * really reaches for that case, and is exercised directly here too.
 */
class SkyGridCommandsTest {

    private RuleAdminUseCase admin;
    private Presets presets;
    private ChallengeAdminUseCase challenges;
    private SkyGridCommands subject;

    @BeforeEach
    void setUp() {
        admin = mock(RuleAdminUseCase.class);
        presets = mock(Presets.class);
        challenges = mock(ChallengeAdminUseCase.class);
        subject = new SkyGridCommands(admin, presets, challenges);
    }

    @Test
    @DisplayName("Cloud's no-permission exception handler sends the no permission message")
    void handleNoPermissionSendsNoPermissionMessage() {
        FakeCommandSource source = new FakeCommandSource();

        subject.handleNoPermission(source);

        assertThat(source.sent()).containsExactly(Messages.noPermission());
        verifyNoInteractions(admin);
    }

    @Test
    @DisplayName("'algorithm <id>' delegates to setAlgorithm")
    void algorithmWithIdDelegatesToSetAlgorithm() {
        when(admin.setAlgorithm("noise_skygrid")).thenReturn(true);
        FakeCommandSource source = new FakeCommandSource();

        subject.algorithm(source, "noise_skygrid");

        verify(admin).setAlgorithm("noise_skygrid");
        assertThat(source.sent()).containsExactly(Messages.algorithmSet("noise_skygrid"));
    }

    @Test
    @DisplayName("'algorithm' without an id reports the current algorithm instead")
    void algorithmWithoutIdReportsCurrentAlgorithm() {
        when(admin.algorithm()).thenReturn("skygrid");
        FakeCommandSource source = new FakeCommandSource();

        subject.algorithm(source, null);

        assertThat(source.sent()).containsExactly(Messages.algorithmCurrent("skygrid"));
        verify(admin, never()).setAlgorithm(any());
    }

    @Test
    @DisplayName("'undo' is forwarded and a successful undo is reported")
    void undoDelegatesWhenThereIsSomethingToUndo() {
        when(admin.undo()).thenReturn(true);
        FakeCommandSource source = new FakeCommandSource();

        subject.undo(source);

        verify(admin).undo();
        assertThat(source.sent()).containsExactly(Messages.undoDone());
    }

    @Test
    @DisplayName("'undo' is forwarded and the empty case is handled")
    void undoDelegatesWhenThereIsNothingToUndo() {
        when(admin.undo()).thenReturn(false);
        FakeCommandSource source = new FakeCommandSource();

        subject.undo(source);

        verify(admin).undo();
        assertThat(source.sent()).containsExactly(Messages.undoEmpty());
    }

    @Test
    @DisplayName("Cloud's invalid-syntax exception handler shows the full help and does not mutate state")
    void handleInvalidSyntaxShowsFullHelp() {
        FakeCommandSource source = new FakeCommandSource();

        subject.handleInvalidSyntax(source);

        assertThat(source.sent()).hasSize(Messages.help().size());
        verify(admin, never()).setAlgorithm(any());
    }

    @Test
    @DisplayName("An unrecognized first token falls back to the full help through the real 'unmatched' subcommand")
    void unmatchedShowsFullHelp() {
        FakeCommandSource source = new FakeCommandSource();

        subject.unmatched(source, new String[] {"nonsense"});

        assertThat(source.sent()).hasSize(Messages.help().size());
        verify(admin, never()).setAlgorithm(any());
    }

    @Test
    @DisplayName("'preset' without a name shows the usage and never touches the port")
    void presetWithoutNameShowsUsage() {
        FakeCommandSource source = new FakeCommandSource();

        subject.preset(source, null);

        assertThat(source.sent()).containsExactly(Messages.usage("/skygrid preset <name>"));
        verifyNoInteractions(presets);
    }

    // ----- challenge sub command -----

    @Test
    @DisplayName("'challenge list' with no challenges sends the empty message")
    void challengeListEmptySendsEmptyMessage() {
        when(challenges.list()).thenReturn(List.of());
        FakeCommandSource source = new FakeCommandSource();

        subject.challengeList(source);

        verify(challenges).list();
        assertThat(source.sent()).containsExactly(Messages.challengeListEmpty());
    }

    @Test
    @DisplayName("'challenge list' with entries sends one message per entry")
    void challengeListWithEntriesSendsOneMessagePerEntry() {
        when(challenges.list()).thenReturn(List.of(new ChallengeRule("no_crafting_table", "no tables", false)));
        FakeCommandSource source = new FakeCommandSource();

        subject.challengeList(source);

        verify(challenges).list();
        assertThat(source.sent()).containsExactly(Messages.challengeListEntry("no_crafting_table", false));
    }

    @Test
    @DisplayName("'challenge toggle <id>' activates an inactive challenge")
    void challengeToggleActivatesWhenInactive() {
        when(challenges.isActive("no_crafting_table")).thenReturn(false);
        when(challenges.activate("no_crafting_table")).thenReturn(true);
        FakeCommandSource source = new FakeCommandSource();

        subject.challengeToggle(source, "no_crafting_table");

        verify(challenges).isActive("no_crafting_table");
        verify(challenges).activate("no_crafting_table");
        verify(challenges, never()).deactivate(any());
        assertThat(source.sent()).containsExactly(Messages.challengeToggled("no_crafting_table", true));
    }

    @Test
    @DisplayName("'challenge toggle <id>' deactivates an active challenge")
    void challengeToggleDeactivatesWhenActive() {
        when(challenges.isActive("no_crafting_table")).thenReturn(true);
        when(challenges.deactivate("no_crafting_table")).thenReturn(true);
        FakeCommandSource source = new FakeCommandSource();

        subject.challengeToggle(source, "no_crafting_table");

        verify(challenges).isActive("no_crafting_table");
        verify(challenges).deactivate("no_crafting_table");
        verify(challenges, never()).activate(any());
        assertThat(source.sent()).containsExactly(Messages.challengeToggled("no_crafting_table", false));
    }

    @Test
    @DisplayName("'challenge toggle <unknown id>' is handled without throwing")
    void challengeToggleUnknownId() {
        when(challenges.isActive("bogus")).thenReturn(false);
        when(challenges.activate("bogus")).thenReturn(false);
        FakeCommandSource source = new FakeCommandSource();

        subject.challengeToggle(source, "bogus");

        verify(challenges).activate("bogus");
        assertThat(source.sent()).containsExactly(Messages.challengeUnknown("bogus"));
    }

    @Test
    @DisplayName("'skyweight' with an invalid material id does not call setSkyGridWeight")
    void skyWeightInvalidMaterialNeverSetsWeight() {
        FakeCommandSource source = new FakeCommandSource();

        subject.skyWeight(source, "invalid!id", "5");

        verify(admin, never()).setSkyGridWeight(any(), anyDouble());
        assertThat(source.sent()).containsExactly(Messages.invalidMaterial("invalid!id"));
    }

    @Test
    @DisplayName("'reload' without 'confirm' asks for confirmation and does not reload")
    void reloadWithoutConfirmationAsksAndDoesNotReload() {
        FakeCommandSource source = new FakeCommandSource();

        subject.reload(source);

        verify(admin, never()).reload();
        assertThat(source.sent()).containsExactly(Messages.reloadConfirm());
    }

    @Test
    @DisplayName("'reload confirm' reloads exactly once")
    void reloadConfirmReloadsExactlyOnce() {
        FakeCommandSource source = new FakeCommandSource();

        subject.reloadConfirm(source);

        verify(admin, times(1)).reload();
        assertThat(source.sent()).containsExactly(Messages.reloaded());
    }

    @Test
    @DisplayName("'help skyweight' shows the focused usage for a known sub command")
    void helpForKnownSubcommandShowsFocusedUsage() {
        FakeCommandSource source = new FakeCommandSource();

        subject.help(source, "skyweight");

        assertThat(source.sent()).containsExactly(Messages.usage("/skygrid skyweight <material> <value>"));
    }

    @Test
    @DisplayName("'help notasubcommand' falls back to the general help")
    void helpForUnknownSubcommandFallsBackToFullHelp() {
        FakeCommandSource source = new FakeCommandSource();

        subject.help(source, "notasubcommand");

        assertThat(source.sent()).hasSize(Messages.help().size());
    }
}
