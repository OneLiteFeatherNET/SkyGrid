package net.onelitefeather.skygridng.adapters.command;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.BlockWeight;
import net.onelitefeather.skygridng.api.model.GenerationSettings;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.RuleSet;
import net.onelitefeather.skygridng.api.model.SkyGridConfig;
import net.onelitefeather.skygridng.api.model.TileType;
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
import java.util.Set;

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
        subject = new SkyGridCommand(admin, presets);
    }

    private static GeneratorConfig minimalConfig(BlockWeight... blocks) {
        TileType tile = new TileType("grass", 1.0, true,
                BlockId.of("minecraft:grass_block"), BlockId.of("minecraft:dirt"), false, List.of());
        RuleSet ruleSet = new RuleSet(List.of(tile), Set.of(),
                new GenerationSettings(4, 8, 64, 8, "grass", true));
        SkyGridConfig sky = new SkyGridConfig(4, 0, 64, 0.03, List.of(blocks), List.of(), List.of());
        return new GeneratorConfig("skygrid", ruleSet, sky);
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
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);

        assertThat(subject.onTabComplete(sender, command, "skygrid", new String[]{""}))
                .contains("list", "algorithm", "reload", "help");

        when(admin.algorithmIds()).thenReturn(Set.of("wfc", "skygrid", "noise_skygrid"));
        assertThat(subject.onTabComplete(sender, command, "skygrid", new String[]{"algorithm", ""}))
                .containsExactlyInAnyOrder("wfc", "skygrid", "noise_skygrid");
    }

    @Test
    @DisplayName("tab completion without permission returns nothing and never touches the ports")
    void tabCompletionDeniedWithoutPermission() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(false);

        List<String> result = subject.onTabComplete(sender, command, "skygrid", new String[]{"algorithm", ""});

        assertThat(result).isEmpty();
        verifyNoInteractions(admin);
        verifyNoInteractions(presets);
    }

    @Test
    @DisplayName("'skyweight' tab completion offers the configured material ids")
    void skyWeightTabCompletion() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        when(admin.current()).thenReturn(minimalConfig(
                new BlockWeight(BlockId.of("minecraft:stone"), 30),
                new BlockWeight(BlockId.of("minecraft:dirt"), 20)));

        List<String> result = subject.onTabComplete(sender, command, "skygrid", new String[]{"skyweight", ""});

        assertThat(result).containsExactlyInAnyOrder("minecraft:stone", "minecraft:dirt");
    }

    @Test
    @DisplayName("'reload' tab completion offers only 'confirm'")
    void reloadTabCompletion() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);

        List<String> result = subject.onTabComplete(sender, command, "skygrid", new String[]{"reload", ""});

        assertThat(result).containsExactly("confirm");
    }

    @Test
    @DisplayName("'weight' with an unknown tile does not call setWeight")
    void weightUnknownTile() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        when(admin.tile("nosuchtile")).thenReturn(Optional.empty());

        subject.onCommand(sender, command, "skygrid", new String[]{"weight", "nosuchtile", "5"});

        verify(sender).sendMessage(any(Component.class));
        verify(admin, never()).setWeight(any(), anyDouble());
    }

    @Test
    @DisplayName("'weight' with a non numeric value does not call setWeight")
    void weightNotANumber() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        TileType water = new TileType("water", 6.0, true,
                BlockId.of("minecraft:water"), BlockId.of("minecraft:sand"), true, List.of());
        when(admin.tile("water")).thenReturn(Optional.of(water));

        subject.onCommand(sender, command, "skygrid", new String[]{"weight", "water", "notanumber"});

        verify(sender).sendMessage(any(Component.class));
        verify(admin, never()).setWeight(any(), anyDouble());
    }

    @Test
    @DisplayName("'weight' with a valid value reports failure when setWeight refuses")
    void weightSetFails() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        TileType water = new TileType("water", 6.0, true,
                BlockId.of("minecraft:water"), BlockId.of("minecraft:sand"), true, List.of());
        when(admin.tile("water")).thenReturn(Optional.of(water));
        when(admin.setWeight("water", 5.0)).thenReturn(false);

        subject.onCommand(sender, command, "skygrid", new String[]{"weight", "water", "5"});

        verify(admin).setWeight("water", 5.0);
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'skyweight' with an invalid material id does not call setSkyGridWeight")
    void skyWeightInvalidMaterial() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"skyweight", "invalid!id", "5"});

        verify(sender).sendMessage(any(Component.class));
        verify(admin, never()).setSkyGridWeight(any(), anyDouble());
    }

    @Test
    @DisplayName("'allow' with one unknown tile does not call allow")
    void allowUnknownTile() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        TileType a = new TileType("a", 1.0, true,
                BlockId.of("minecraft:stone"), BlockId.of("minecraft:stone"), false, List.of());
        when(admin.tile("a")).thenReturn(Optional.of(a));
        when(admin.tile("b")).thenReturn(Optional.empty());

        subject.onCommand(sender, command, "skygrid", new String[]{"allow", "a", "b"});

        verify(sender).sendMessage(any(Component.class));
        verify(admin, never()).allow(any(), any());
    }

    @Test
    @DisplayName("'allow' with both tiles known calls allow exactly once")
    void allowBothKnown() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        TileType a = new TileType("a", 1.0, true,
                BlockId.of("minecraft:stone"), BlockId.of("minecraft:stone"), false, List.of());
        TileType b = new TileType("b", 1.0, true,
                BlockId.of("minecraft:dirt"), BlockId.of("minecraft:dirt"), false, List.of());
        when(admin.tile("a")).thenReturn(Optional.of(a));
        when(admin.tile("b")).thenReturn(Optional.of(b));

        subject.onCommand(sender, command, "skygrid", new String[]{"allow", "a", "b"});

        verify(admin).allow("a", "b");
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'disallow' with one unknown tile does not call disallow")
    void disallowUnknownTile() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        TileType a = new TileType("a", 1.0, true,
                BlockId.of("minecraft:stone"), BlockId.of("minecraft:stone"), false, List.of());
        when(admin.tile("a")).thenReturn(Optional.of(a));
        when(admin.tile("b")).thenReturn(Optional.empty());

        subject.onCommand(sender, command, "skygrid", new String[]{"disallow", "a", "b"});

        verify(sender).sendMessage(any(Component.class));
        verify(admin, never()).disallow(any(), any());
    }

    @Test
    @DisplayName("'disallow' with both tiles known calls disallow exactly once")
    void disallowBothKnown() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);
        TileType a = new TileType("a", 1.0, true,
                BlockId.of("minecraft:stone"), BlockId.of("minecraft:stone"), false, List.of());
        TileType b = new TileType("b", 1.0, true,
                BlockId.of("minecraft:dirt"), BlockId.of("minecraft:dirt"), false, List.of());
        when(admin.tile("a")).thenReturn(Optional.of(a));
        when(admin.tile("b")).thenReturn(Optional.of(b));

        subject.onCommand(sender, command, "skygrid", new String[]{"disallow", "a", "b"});

        verify(admin).disallow("a", "b");
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'reload' without 'confirm' asks for confirmation and does not reload")
    void reloadRequiresConfirmation() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"reload"});

        verify(admin, never()).reload();
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'reload confirm' reloads exactly once")
    void reloadWithConfirmation() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"reload", "confirm"});

        verify(admin, times(1)).reload();
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'help weight' shows the focused usage for a known sub command")
    void helpForKnownSubcommand() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"help", "weight"});

        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("'help notasubcommand' falls back to the general help")
    void helpForUnknownSubcommand() {
        when(sender.hasPermission("skygridng.admin")).thenReturn(true);

        subject.onCommand(sender, command, "skygrid", new String[]{"help", "notasubcommand"});

        verify(sender).sendMessage(any(Component.class));
    }
}
