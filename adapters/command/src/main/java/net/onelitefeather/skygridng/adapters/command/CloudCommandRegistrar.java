package net.onelitefeather.skygridng.adapters.command;

import net.onelitefeather.skygridng.adapters.command.handler.AlgorithmCommands;
import net.onelitefeather.skygridng.adapters.command.handler.ChallengeCommands;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import net.onelitefeather.skygridng.adapters.command.handler.HelpCommands;
import net.onelitefeather.skygridng.adapters.command.handler.MaintenanceCommands;
import net.onelitefeather.skygridng.adapters.command.handler.PresetCommands;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Registers stage 2's Cloud annotated subcommand handlers on the manager built by
 * {@link CloudCommandManagerFactory}: the dynamic suggestion providers ported from the legacy
 * {@code SkyGridCommand#onTabComplete}, the {@code NoPermissionException} handler that replaces
 * Cloud's default text with {@link Messages#noPermission()}, and finally the annotation parsing
 * of every handler instance.
 *
 * The literal subcommand names (preset, presets, worlds, algorithm, skyweight, reload, save, undo,
 * challenge, help), the literal {@code confirm} token, and the literal {@code list}/{@code toggle}
 * challenge subcommands are all Cloud command tree literals now, not free-form arguments, so Cloud
 * suggests them natively; only the genuinely dynamic argument values (preset names, algorithm ids,
 * material ids, challenge ids) need a custom {@link SuggestionProvider} registered here.
 */
public final class CloudCommandRegistrar {

    private CloudCommandRegistrar() {
    }

    public static void register(PaperCommandManager<CommandSource> manager,
                                 Presets presets,
                                 RuleAdminUseCase admin,
                                 ChallengeAdminUseCase challenges,
                                 PresetCommands presetCommands,
                                 AlgorithmCommands algorithmCommands,
                                 MaintenanceCommands maintenanceCommands,
                                 ChallengeCommands challengeCommands,
                                 HelpCommands helpCommands) {
        registerSuggestionProviders(manager, presets, admin, challenges);
        registerNoPermissionHandler(manager);

        AnnotationParser<CommandSource> parser = new AnnotationParser<>(manager, CommandSource.class);
        parser.parse(presetCommands, algorithmCommands, maintenanceCommands, challengeCommands, helpCommands);
    }

    private static void registerSuggestionProviders(PaperCommandManager<CommandSource> manager,
                                                      Presets presets,
                                                      RuleAdminUseCase admin,
                                                      ChallengeAdminUseCase challenges) {
        manager.parserRegistry().registerSuggestionProvider("preset-names",
                SuggestionProvider.blockingStrings((ctx, input) -> presets.names()));
        manager.parserRegistry().registerSuggestionProvider("algorithm-ids",
                SuggestionProvider.blockingStrings((ctx, input) -> admin.algorithmIds()));
        manager.parserRegistry().registerSuggestionProvider("material-ids",
                SuggestionProvider.blockingStrings((ctx, input) -> admin.current().skygrid().blocks().stream()
                        .map(blockWeight -> blockWeight.material().asString())
                        .toList()));
        manager.parserRegistry().registerSuggestionProvider("challenge-ids",
                SuggestionProvider.blockingStrings((ctx, input) -> challenges.list().stream()
                        .map(ChallengeRule::id)
                        .toList()));
    }

    private static void registerNoPermissionHandler(PaperCommandManager<CommandSource> manager) {
        manager.exceptionController().registerHandler(NoPermissionException.class,
                ctx -> ctx.context().sender().sendMessage(Messages.noPermission()));
    }
}
