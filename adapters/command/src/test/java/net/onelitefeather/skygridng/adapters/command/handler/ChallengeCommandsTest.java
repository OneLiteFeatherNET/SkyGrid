package net.onelitefeather.skygridng.adapters.command.handler;

import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.ChallengeProgressUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChallengeCommands}: {@code /skygrid challenge list},
 * {@code /skygrid challenge toggle <id>}, and {@code /skygrid challenge tier [player]}/
 * {@code tier reset [confirm]}, ported from the legacy {@code SkyGridCommandTest}'s equivalent
 * cases (including its {@code challengeTier*} methods).
 */
class ChallengeCommandsTest {

    private ChallengeAdminUseCase challenges;
    private ChallengeProgressUseCase progress;
    private ChallengeCommands subject;
    private RecordingCommandSource source;

    @BeforeEach
    void setUp() {
        challenges = mock(ChallengeAdminUseCase.class);
        progress = mock(ChallengeProgressUseCase.class);
        subject = new ChallengeCommands(challenges, progress);
        source = new RecordingCommandSource();
    }

    @Test
    @DisplayName("'challenge list' reports the empty case")
    void listReportsEmptyCase() {
        when(challenges.list()).thenReturn(List.of());

        subject.list(source);

        assertThat(source.messages()).containsExactly(Messages.challengeListEmpty());
    }

    @Test
    @DisplayName("'challenge list' sends one entry per known challenge")
    void listSendsOneEntryPerChallenge() {
        when(challenges.list()).thenReturn(List.of(
                new ChallengeRule("no_crafting_table", "no tables", false),
                new ChallengeRule("hardcore", "one life", true)));

        subject.list(source);

        assertThat(source.messages()).containsExactly(
                Messages.challengeListEntry("no_crafting_table", false),
                Messages.challengeListEntry("hardcore", true));
    }

    @Test
    @DisplayName("'challenge toggle <id>' activates an inactive challenge")
    void toggleActivatesWhenInactive() {
        when(challenges.isActive("no_crafting_table")).thenReturn(false);
        when(challenges.activate("no_crafting_table")).thenReturn(true);

        subject.toggle(source, "no_crafting_table");

        verify(challenges).activate("no_crafting_table");
        verify(challenges, never()).deactivate(any());
        assertThat(source.messages()).containsExactly(Messages.challengeToggled("no_crafting_table", true));
    }

    @Test
    @DisplayName("'challenge toggle <id>' deactivates an active challenge")
    void toggleDeactivatesWhenActive() {
        when(challenges.isActive("no_crafting_table")).thenReturn(true);
        when(challenges.deactivate("no_crafting_table")).thenReturn(true);

        subject.toggle(source, "no_crafting_table");

        verify(challenges).deactivate("no_crafting_table");
        verify(challenges, never()).activate(any());
        assertThat(source.messages()).containsExactly(Messages.challengeToggled("no_crafting_table", false));
    }

    @Test
    @DisplayName("'challenge toggle <unknown id>' is handled without throwing")
    void toggleUnknownIdIsHandled() {
        when(challenges.isActive("bogus")).thenReturn(false);
        when(challenges.activate("bogus")).thenReturn(false);

        subject.toggle(source, "bogus");

        verify(challenges).activate("bogus");
        assertThat(source.messages()).containsExactly(Messages.challengeUnknown("bogus"));
    }

    @Test
    @DisplayName("'challenge tier' with no player name reports the invoking player's own tier")
    void tierSelfReportsOwnTier() {
        UUID selfId = source.selfId();
        when(progress.tierOf(selfId, "one_life")).thenReturn(2);

        subject.tier(source, null);

        verify(progress).tierOf(selfId, "one_life");
        assertThat(source.messages()).containsExactly(Messages.challengeTierSelf(2));
    }

    @Test
    @DisplayName("'challenge tier' invoked from the console (not a player) never queries a tier")
    void tierFromConsoleNeverQueriesTier() {
        source.asConsole();

        subject.tier(source, null);

        verify(progress, never()).tierOf(any(), any());
        assertThat(source.messages()).containsExactly(Messages.challengeTierConsole());
    }

    @Test
    @DisplayName("'challenge tier <player>' reports the named player's tier")
    void tierNamedPlayerReportsTheirTier() {
        UUID targetId = source.resolvePlayerId("Steve").orElseThrow();
        when(progress.tierOf(targetId, "one_life")).thenReturn(5);

        subject.tier(source, "Steve");

        verify(progress).tierOf(targetId, "one_life");
        assertThat(source.messages()).containsExactly(Messages.challengeTierOther("Steve", 5));
    }

    @Test
    @DisplayName("'challenge tier reset' without 'confirm' asks for confirmation and does not reset")
    void tierResetRequiresConfirmation() {
        subject.tierReset(source);

        verify(progress, never()).resetAll();
        assertThat(source.messages()).containsExactly(Messages.challengeTierResetConfirm());
    }

    @Test
    @DisplayName("'challenge tier reset confirm' calls resetAll exactly once and responds with the confirmation message")
    void tierResetConfirmCallsResetAllOnce() {
        subject.tierResetConfirm(source);

        verify(progress).resetAll();
        assertThat(source.messages()).containsExactly(Messages.challengeTierReset());
    }
}
