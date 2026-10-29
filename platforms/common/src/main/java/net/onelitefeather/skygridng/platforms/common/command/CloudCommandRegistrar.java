package net.onelitefeather.skygridng.platforms.common.command;

import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.adapters.command.handler.CloudCommandHandler;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import net.onelitefeather.skygridng.ports.in.SeasonAdminUseCase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.Set;

/**
 * Registers stage 2's Cloud annotated subcommand handlers on the manager built by
 * {@link CloudCommandManagerFactory}: the dynamic suggestion providers ported from the legacy
 * {@code SkyGridCommand#onTabComplete}, the {@code NoPermissionException} handler that replaces
 * Cloud's default text with {@link Messages#noPermission()}, and finally the annotation parsing
 * of every handler instance.
 *
 * Lives in platforms.common, not adapters.command: it is the only place the handler set is
 * registered onto a real Paper-bound {@link PaperCommandManager}, and it needs Bukkit
 * (online player suggestions) directly, unlike the handler classes themselves, which stay
 * framework-free.
 *
 * The literal subcommand names (preset, presets, worlds, algorithm, skyweight, reload, save, undo,
 * challenge, help), the literal {@code confirm} token, and the literal {@code list}/{@code toggle}/
 * {@code tier}/{@code reset} challenge subcommands are all Cloud command tree literals now, not
 * free-form arguments, so Cloud suggests them natively; only the genuinely dynamic argument values
 * (preset names, algorithm ids, material ids, challenge ids, online player names) need a custom
 * {@link SuggestionProvider} registered here.
 *
 * The handler set comes from Guice's {@code CloudCommandHandler} set binder (see
 * {@code CommandModule}), so adding a subcommand group is one new class plus one binding line
 * there, never a change to this method's signature.
 */
public final class CloudCommandRegistrar {

    private CloudCommandRegistrar() {
    }

    public static void register(PaperCommandManager<CommandSource> manager,
                                 Presets presets,
                                 RuleAdminUseCase admin,
                                 ChallengeAdminUseCase challenges,
                                 SeasonAdminUseCase seasons,
                                 Set<CloudCommandHandler> handlers) {
        registerSuggestionProviders(manager, presets, admin, challenges, seasons);
        registerNoPermissionHandler(manager);

        AnnotationParser<CommandSource> parser = new AnnotationParser<>(manager, CommandSource.class);
        parser.parse(handlers.toArray());
    }

    private static void registerSuggestionProviders(PaperCommandManager<CommandSource> manager,
                                                      Presets presets,
                                                      RuleAdminUseCase admin,
                                                      ChallengeAdminUseCase challenges,
                                                      SeasonAdminUseCase seasons) {
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
        manager.parserRegistry().registerSuggestionProvider("season-ids",
                SuggestionProvider.blockingStrings((ctx, input) -> seasons.list().seasons().stream()
                        .map(Season::id)
                        .map(id -> id.value())
                        .toList()));
        manager.parserRegistry().registerSuggestionProvider("online-players",
                SuggestionProvider.blockingStrings((ctx, input) -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .toList()));
    }

    private static void registerNoPermissionHandler(PaperCommandManager<CommandSource> manager) {
        manager.exceptionController().registerHandler(NoPermissionException.class,
                ctx -> ctx.context().sender().sendMessage(Messages.noPermission()));
    }
}
