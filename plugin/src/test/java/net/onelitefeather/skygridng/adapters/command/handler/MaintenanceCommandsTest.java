package net.onelitefeather.skygridng.adapters.command.handler;

import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MaintenanceCommands}: {@code /skygrid reload}, {@code /skygrid reload
 * confirm}, {@code /skygrid save}, and {@code /skygrid undo}, ported from the legacy
 * {@code SkyGridCommandTest#reloadRequiresConfirmation}, {@code #reloadWithConfirmation}, and
 * {@code #undoDelegates}.
 *
 * {@code reload} and {@code reload confirm} are two distinct annotated methods now (the literal
 * {@code confirm} token is a separate command tree branch, see the class Javadoc on
 * {@link MaintenanceCommands}), so each gets its own test instead of one branching test.
 */
class MaintenanceCommandsTest {

    private RuleAdminUseCase admin;
    private MaintenanceCommands subject;
    private RecordingCommandSource source;

    @BeforeEach
    void setUp() {
        admin = mock(RuleAdminUseCase.class);
        subject = new MaintenanceCommands(admin);
        source = new RecordingCommandSource();
    }

    @Test
    @DisplayName("'reload' without 'confirm' asks for confirmation and never reloads")
    void reloadWithoutConfirmNeverReloads() {
        subject.reload(source);

        verify(admin, never()).reload();
        assertThat(source.messages()).containsExactly(Messages.reloadConfirm());
    }

    @Test
    @DisplayName("'reload confirm' reloads exactly once")
    void reloadConfirmReloadsExactlyOnce() {
        subject.reloadConfirm(source);

        verify(admin, times(1)).reload();
        assertThat(source.messages()).containsExactly(Messages.reloaded());
    }

    @Test
    @DisplayName("'save' delegates and confirms")
    void saveDelegates() {
        subject.save(source);

        verify(admin).save();
        assertThat(source.messages()).containsExactly(Messages.saved());
    }

    @Test
    @DisplayName("'undo' reports success when there was a change to revert")
    void undoDelegatesWhenPresent() {
        when(admin.undo()).thenReturn(true);

        subject.undo(source);

        verify(admin).undo();
        assertThat(source.messages()).containsExactly(Messages.undoDone());
    }

    @Test
    @DisplayName("'undo' handles the empty case without throwing")
    void undoHandlesEmptyCase() {
        when(admin.undo()).thenReturn(false);

        subject.undo(source);

        verify(admin).undo();
        assertThat(source.messages()).containsExactly(Messages.undoEmpty());
    }
}
