package net.onelitefeather.skygridng.adapters.command;

import net.onelitefeather.skygridng.adapters.command.handler.AlgorithmCommands;
import net.onelitefeather.skygridng.adapters.command.handler.ChallengeCommands;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import net.onelitefeather.skygridng.adapters.command.handler.HelpCommands;
import net.onelitefeather.skygridng.adapters.command.handler.MaintenanceCommands;
import net.onelitefeather.skygridng.adapters.command.handler.PresetCommands;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Confidence check that the {@code @Command}/{@code @Argument}/{@code @Permission} annotations on
 * all five migrated handler classes are internally consistent (no duplicate or conflicting command
 * trees, no malformed argument syntax): {@link AnnotationParser#parse} must not throw when fed
 * every handler instance.
 *
 * This deliberately does NOT exercise {@code CloudCommandRegistrar#register} (now in
 * platforms.common.command, since it needs a real Bukkit-bound Paper manager), which only accepts a
 * real {@code PaperCommandManager}. That manager's constructor eagerly reflects into the server's
 * actual Brigadier argument type registry and throws a {@code LinkageError} outside a real Paper
 * server or under MockBukkit ({@code SkyGridNgPlugin#onEnable} guards exactly this, a known,
 * deliberate stage-1 limitation; it is not something to route around here).
 *
 * Instead this builds the lightest possible manager: cloud-core's own abstract {@link CommandManager}
 * base class, with a synchronous {@link ExecutionCoordinator} and a no-op
 * {@link CommandRegistrationHandler}, neither of which touches Paper or Brigadier at all. That is
 * enough for {@link AnnotationParser} to walk the annotated methods and assemble the command tree.
 * It does not dispatch a single command through it, so it stays a lean parse-only check, not a
 * roundtrip test; real end to end confidence (a command genuinely reaching a handler through the
 * live Cloud/Brigadier pipeline on a running server) comes from the mandatory boot test against a
 * real Paper server, not from this class.
 */
class CloudCommandRegistrarTest {

    @Test
    void allFiveHandlersParseWithoutConflict() {
        CommandManager<CommandSource> manager = new CommandManager<>(
                ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSource sender, String permission) {
                return true;
            }
        };
        // The annotation parser resolves each @Argument(suggestions = "...") name against the
        // manager's parser registry while assembling the command tree (it throws
        // IllegalArgumentException for an unregistered name), so the five dynamic providers
        // CloudCommandRegistrar registers in production must exist here too, even though their
        // content is irrelevant to this annotation-consistency check.
        SuggestionProvider<CommandSource> empty = SuggestionProvider.blockingStrings((ctx, input) -> List.of());
        manager.parserRegistry().registerSuggestionProvider("preset-names", empty);
        manager.parserRegistry().registerSuggestionProvider("algorithm-ids", empty);
        manager.parserRegistry().registerSuggestionProvider("material-ids", empty);
        manager.parserRegistry().registerSuggestionProvider("challenge-ids", empty);
        manager.parserRegistry().registerSuggestionProvider("online-players", empty);

        AnnotationParser<CommandSource> parser = new AnnotationParser<>(manager, CommandSource.class);

        assertThatCode(() -> parser.parse(
                new PresetCommands(mock(Presets.class)),
                new AlgorithmCommands(mock(RuleAdminUseCase.class)),
                new MaintenanceCommands(mock(RuleAdminUseCase.class)),
                new ChallengeCommands(mock(ChallengeAdminUseCase.class), mock(ChallengeProgressUseCase.class)),
                new HelpCommands()))
                .doesNotThrowAnyException();
    }
}
