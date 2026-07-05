package net.onelitefeather.skygridng.adapters.command;

import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
    private Presets presets;
    private CommandSender sender;
    private Command command;
    private SkyGridCommand subject;

    @BeforeEach
    void setUp() {
        admin = mock(RuleAdminUseCase.class);
        presets = mock(Presets.class);
        sender = mock(CommandSender.class);
        command = mock(Command.class);
        subject = new SkyGridCommand(admin, presets, Optional.empty());
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

    // ----- challenge sub command -----

    @Test
    @DisplayName("'challenge list' delegates to the ChallengeAdminUseCase when the port is present")
    void challengeListDelegatesWhenPresent() {
        ChallengeAdminUseCase challenges = mock(ChallengeAdminUseCase.class);
        subject = new SkyGridCommand(admin, presets, Optional.of(challenges));
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        when(challenges.list()).thenReturn(List.of(new ChallengeRule("no_crafting_table", "no tables", false)));

        subject.onCommand(sender, command, "skygrid", new String[]{"challenge", "list"});

        verify(challenges).list();
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'challenge toggle <id>' activates an inactive challenge")
    void challengeToggleActivatesWhenInactive() {
        ChallengeAdminUseCase challenges = mock(ChallengeAdminUseCase.class);
        subject = new SkyGridCommand(admin, presets, Optional.of(challenges));
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        when(challenges.isActive("no_crafting_table")).thenReturn(false);
        when(challenges.activate("no_crafting_table")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"challenge", "toggle", "no_crafting_table"});

        verify(challenges).isActive("no_crafting_table");
        verify(challenges).activate("no_crafting_table");
        verify(challenges, never()).deactivate(any());
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'challenge toggle <id>' deactivates an active challenge")
    void challengeToggleDeactivatesWhenActive() {
        ChallengeAdminUseCase challenges = mock(ChallengeAdminUseCase.class);
        subject = new SkyGridCommand(admin, presets, Optional.of(challenges));
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        when(challenges.isActive("no_crafting_table")).thenReturn(true);
        when(challenges.deactivate("no_crafting_table")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"challenge", "toggle", "no_crafting_table"});

        verify(challenges).isActive("no_crafting_table");
        verify(challenges).deactivate("no_crafting_table");
        verify(challenges, never()).activate(any());
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'challenge toggle <unknown id>' is handled without throwing")
    void challengeToggleUnknownId() {
        ChallengeAdminUseCase challenges = mock(ChallengeAdminUseCase.class);
        subject = new SkyGridCommand(admin, presets, Optional.of(challenges));
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        when(challenges.isActive("bogus")).thenReturn(false);
        when(challenges.activate("bogus")).thenReturn(false);

        boolean handled = subject.onCommand(sender, command, "skygrid", new String[]{"challenge", "toggle", "bogus"});

        assertThat(handled).isTrue();
        verify(challenges).activate("bogus");
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'challenge' replies with the unavailable message when the port is absent")
    void challengeUnavailableWhenPortAbsent() {
        // subject was built in setUp() with Optional.empty()
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"challenge", "list"});

        verify(sender).sendMessage(any(Component.class));
    }
}
