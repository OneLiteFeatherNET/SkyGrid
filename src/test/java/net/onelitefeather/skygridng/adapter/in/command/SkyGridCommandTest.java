package net.onelitefeather.skygridng.adapter.in.command;

import net.onelitefeather.skygridng.core.port.in.RuleAdminUseCase;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Black-box tests for the command adapter against the {@link RuleAdminUseCase} port. Mockito
 * mocks the sender and the port; we assert the command delegates correctly and guards permission.
 */
class SkyGridCommandTest {

    private RuleAdminUseCase admin;
    private CommandSender sender;
    private Command command;
    private SkyGridCommand subject;

    @BeforeEach
    void setUp() {
        admin = mock(RuleAdminUseCase.class);
        sender = mock(CommandSender.class);
        command = mock(Command.class);
        subject = new SkyGridCommand(admin);
    }

    @Test
    @DisplayName("without permission the command replies and never touches the use case")
    void deniedWithoutPermission() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(false);

        boolean handled = subject.onCommand(sender, command, "skygrid", new String[]{"list"});

        assertThat(handled).isTrue();
        verify(sender).sendMessage(any(Component.class));
        verifyNoInteractions(admin);
    }

    @Test
    @DisplayName("'algorithm <id>' delegates to setAlgorithm")
    void algorithmDelegates() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        when(admin.setAlgorithm("wfc")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"algorithm", "wfc"});

        verify(admin).setAlgorithm("wfc");
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'list' reads the tile ids from the use case")
    void listDelegates() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        when(admin.tileIds()).thenReturn(List.of("water", "sand"));

        subject.onCommand(sender, command, "skygrid", new String[]{"list"});

        verify(admin).tileIds();
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'undo' is forwarded and the empty case is handled")
    void undoDelegates() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        when(admin.undo()).thenReturn(false);

        subject.onCommand(sender, command, "skygrid", new String[]{"undo"});

        verify(admin).undo();
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("an unknown sub command shows help and does not mutate state")
    void helpForUnknown() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"help"});

        verify(sender).sendMessage(any(Component.class));
        verify(admin, never()).setAlgorithm(any());
    }

    @Test
    @DisplayName("tab completion offers the sub commands and algorithm ids")
    void tabCompletion() {
        assertThat(subject.onTabComplete(sender, command, "skygrid", new String[]{""}))
                .contains("list", "algorithm", "reload", "help");
        assertThat(subject.onTabComplete(sender, command, "skygrid", new String[]{"algorithm", ""}))
                .containsExactlyInAnyOrder("wfc", "skygrid", "noise_skygrid");
    }
}
