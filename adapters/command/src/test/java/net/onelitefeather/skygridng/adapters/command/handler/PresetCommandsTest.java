package net.onelitefeather.skygridng.adapters.command.handler;

import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.ports.in.Presets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PresetCommands}: {@code /skygrid preset <name>}, {@code /skygrid presets},
 * and {@code /skygrid worlds}. The legacy {@code SkyGridCommandTest} never covered preset/world
 * administration (the old monolithic command's tab completion tests touched skyweight and reload
 * only), so this coverage is new rather than ported.
 */
class PresetCommandsTest {

    private Presets presets;
    private PresetCommands subject;
    private RecordingCommandSource source;

    @BeforeEach
    void setUp() {
        presets = mock(Presets.class);
        subject = new PresetCommands(presets);
        source = new RecordingCommandSource();
    }

    @Test
    @DisplayName("'preset <name>' activates a known preset")
    void presetActivatesKnownName() {
        when(presets.activate("nether")).thenReturn(true);

        subject.preset(source, "nether");

        verify(presets).activate("nether");
        assertThat(source.messages()).containsExactly(Messages.presetSet("nether"));
    }

    @Test
    @DisplayName("'preset <name>' reports an unknown preset without throwing")
    void presetReportsUnknownName() {
        when(presets.activate("bogus")).thenReturn(false);

        subject.preset(source, "bogus");

        assertThat(source.messages()).containsExactly(Messages.presetUnknown("bogus"));
    }

    @Test
    @DisplayName("'presets' lists every preset name and the active one")
    void presetsListsNamesAndActive() {
        when(presets.names()).thenReturn(List.of("classic", "standard", "nether"));
        when(presets.active()).thenReturn("standard");

        subject.presets(source);

        assertThat(source.messages())
                .containsExactly(Messages.presetList("classic, standard, nether", "standard"));
    }

    @Test
    @DisplayName("'worlds' reports '(none yet)' when no world has picked a preset")
    void worldsReportsNoneYetWhenEmpty() {
        when(presets.worlds()).thenReturn(Map.of());

        subject.worlds(source);

        assertThat(source.messages()).containsExactly(Messages.worldList("(none yet)"));
    }

    @Test
    @DisplayName("'worlds' joins each world=preset pair")
    void worldsJoinsPairs() {
        Map<String, String> worlds = new LinkedHashMap<>();
        worlds.put("world", "classic");
        worlds.put("world_nether", "nether");
        when(presets.worlds()).thenReturn(worlds);

        subject.worlds(source);

        assertThat(source.messages()).containsExactly(Messages.worldList("world=classic, world_nether=nether"));
    }
}
