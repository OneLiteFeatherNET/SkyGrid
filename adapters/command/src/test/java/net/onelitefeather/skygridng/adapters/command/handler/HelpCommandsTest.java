package net.onelitefeather.skygridng.adapters.command.handler;

import net.onelitefeather.skygridng.adapters.command.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HelpCommands}: bare {@code /skygrid}, {@code /skygrid help}, and
 * {@code /skygrid help <subcommand>}, ported from the legacy
 * {@code SkyGridCommandTest#helpForUnknown}, {@code #helpForKnownSubcommand}, and
 * {@code #helpForUnknownSubcommand}. The old test asserted the fallback by counting
 * {@code Messages.help().size()} sent messages; {@link RecordingCommandSource} lets these tests
 * assert the same fact more precisely, by comparing the exact message list.
 */
class HelpCommandsTest {

    private HelpCommands subject;
    private RecordingCommandSource source;

    @BeforeEach
    void setUp() {
        subject = new HelpCommands();
        source = new RecordingCommandSource();
    }

    @Test
    @DisplayName("bare '/skygrid' shows the full help list")
    void bareShowsFullHelp() {
        subject.bare(source);

        assertThat(source.messages()).containsExactlyElementsOf(Messages.help());
    }

    @Test
    @DisplayName("'/skygrid help' shows the full help list")
    void helpShowsFullHelp() {
        subject.help(source);

        assertThat(source.messages()).containsExactlyElementsOf(Messages.help());
    }

    @Test
    @DisplayName("'help skyweight' shows the focused usage for a known sub command")
    void helpForKnownSubcommandShowsFocusedUsage() {
        subject.help(source, "skyweight");

        assertThat(source.messages()).containsExactly(Messages.usage("/skygrid skyweight <material> <value>"));
    }

    @Test
    @DisplayName("the sub command lookup is case insensitive")
    void helpLooksUpCaseInsensitively() {
        subject.help(source, "SKYWEIGHT");

        assertThat(source.messages()).containsExactly(Messages.usage("/skygrid skyweight <material> <value>"));
    }

    @Test
    @DisplayName("'help notasubcommand' falls back to the general help")
    void helpForUnknownSubcommandFallsBack() {
        subject.help(source, "notasubcommand");

        assertThat(source.messages()).containsExactlyElementsOf(Messages.help());
    }
}
