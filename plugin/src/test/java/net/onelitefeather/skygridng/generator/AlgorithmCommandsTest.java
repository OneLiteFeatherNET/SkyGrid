package net.onelitefeather.skygridng.generator;

import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.adapters.command.handler.RecordingCommandSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlgorithmCommands}: {@code /skygrid algorithm [id]} and
 * {@code /skygrid skyweight <material> <value>}, ported from the legacy
 * {@code SkyGridCommandTest#algorithmDelegates} and {@code #skyWeightInvalidMaterial}.
 */
class AlgorithmCommandsTest {

    private RuleAdminUseCase admin;
    private AlgorithmCommands subject;
    private RecordingCommandSource source;

    @BeforeEach
    void setUp() {
        admin = mock(RuleAdminUseCase.class);
        subject = new AlgorithmCommands(admin);
        source = new RecordingCommandSource();
    }

    @Test
    @DisplayName("'algorithm' with no id shows the current algorithm")
    void algorithmWithoutIdShowsCurrent() {
        when(admin.algorithm()).thenReturn("skygrid");

        subject.algorithm(source, null);

        verify(admin, never()).setAlgorithm(any());
        assertThat(source.messages()).containsExactly(Messages.algorithmCurrent("skygrid"));
    }

    @Test
    @DisplayName("'algorithm <id>' delegates to setAlgorithm, lowercased")
    void algorithmDelegates() {
        when(admin.setAlgorithm("noise_skygrid")).thenReturn(true);

        subject.algorithm(source, "NOISE_SKYGRID");

        verify(admin).setAlgorithm("noise_skygrid");
        assertThat(source.messages()).containsExactly(Messages.algorithmSet("noise_skygrid"));
    }

    @Test
    @DisplayName("'algorithm <id>' reports an unknown id without throwing")
    void algorithmReportsUnknownId() {
        when(admin.setAlgorithm("bogus")).thenReturn(false);

        subject.algorithm(source, "bogus");

        assertThat(source.messages()).containsExactly(Messages.algorithmUnknown("bogus"));
    }

    @Test
    @DisplayName("'skyweight' with an invalid material id never calls setSkyGridWeight")
    void skyweightInvalidMaterial() {
        subject.skyweight(source, "invalid!id", "5");

        verify(admin, never()).setSkyGridWeight(any(), anyDouble());
        assertThat(source.messages()).containsExactly(Messages.invalidMaterial("invalid!id"));
    }

    @Test
    @DisplayName("'skyweight' with a non numeric value never calls setSkyGridWeight")
    void skyweightNonNumericValue() {
        subject.skyweight(source, "minecraft:stone", "not-a-number");

        verify(admin, never()).setSkyGridWeight(any(), anyDouble());
        assertThat(source.messages()).containsExactly(Messages.notANumber("not-a-number"));
    }

    @Test
    @DisplayName("'skyweight' with a valid material and value delegates to setSkyGridWeight")
    void skyweightDelegates() {
        when(admin.setSkyGridWeight("minecraft:stone", 30.0)).thenReturn(true);

        subject.skyweight(source, "minecraft:stone", "30");

        verify(admin).setSkyGridWeight("minecraft:stone", 30.0);
        assertThat(source.messages()).containsExactly(Messages.skyWeightSet("minecraft:stone", 30.0));
    }

    @Test
    @DisplayName("'skyweight' reports a failure from the use case without throwing")
    void skyweightReportsFailure() {
        when(admin.setSkyGridWeight("minecraft:stone", 30.0)).thenReturn(false);

        subject.skyweight(source, "minecraft:stone", "30");

        assertThat(source.messages()).containsExactly(Messages.weightFail("30"));
    }
}
