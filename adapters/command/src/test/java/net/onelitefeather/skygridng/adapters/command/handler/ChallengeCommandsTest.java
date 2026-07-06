package net.onelitefeather.skygridng.adapters.command.handler;

import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChallengeCommands}: {@code /skygrid challenge list} and
 * {@code /skygrid challenge toggle <id>}, ported from the legacy
 * {@code SkyGridCommandTest#challengeListDelegatesWhenPresent},
 * {@code #challengeToggleActivatesWhenInactive}, {@code #challengeToggleDeactivatesWhenActive}, and
 * {@code #challengeToggleUnknownId}.
 */
class ChallengeCommandsTest {

    private ChallengeAdminUseCase challenges;
    private ChallengeCommands subject;
    private RecordingCommandSource source;

    @BeforeEach
    void setUp() {
        challenges = mock(ChallengeAdminUseCase.class);
        subject = new ChallengeCommands(challenges);
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
}
