package net.onelitefeather.skygridng.adapters.command.parser;

import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import org.incendo.cloud.annotations.suggestion.Suggestions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cloud suggestion providers (tab completion) for the dynamic arguments of
 * {@link net.onelitefeather.skygridng.adapters.command.SkyGridCommands}, backed by the same three
 * in ports. Kept in a separate class so the command-dispatch class stays focused on subcommand
 * behavior; both are parsed together (see {@code platforms/common}'s {@code CloudCommandRegistrar})
 * in the same {@code AnnotationParser.parse(...)} call, so these providers are registered before
 * the commands that reference them by name, regardless of instance order.
 *
 * Framework-free like {@code SkyGridCommands}: only {@code org.incendo.cloud.annotations} and
 * {@code org.incendo.cloud.core} types, no Bukkit, Guice, or jakarta.inject. Plain constructor,
 * instantiated by {@code CloudCommandRegistrar} the same way {@code SkyGridCommands} is.
 *
 * The subcommand name list backing {@code /skygrid help [subcommand]} is intentionally duplicated
 * here as a plain constant rather than shared with {@code SkyGridCommands}'s {@code USAGE} map, so
 * that map can stay package-private to {@code net.onelitefeather.skygridng.adapters.command} (this
 * class lives one package below it).
 */
public final class SkyGridArgumentParsers {

    private static final List<String> HELP_SUBCOMMANDS = List.of(
            "reload", "algorithm", "presets", "worlds", "undo", "save", "preset", "skyweight", "challenge");

    private final RuleAdminUseCase admin;
    private final Presets presets;
    private final ChallengeAdminUseCase challenges;

    public SkyGridArgumentParsers(RuleAdminUseCase admin, Presets presets, ChallengeAdminUseCase challenges) {
        this.admin = admin;
        this.presets = presets;
        this.challenges = challenges;
    }

    @Suggestions("skygrid-help-subcommands")
    public List<String> helpSubcommands(String input) {
        return filter(HELP_SUBCOMMANDS, input);
    }

    @Suggestions("skygrid-presets")
    public List<String> presetNames(String input) {
        return filter(presets.names(), input);
    }

    @Suggestions("skygrid-algorithms")
    public List<String> algorithmIds(String input) {
        return filter(List.copyOf(admin.algorithmIds()), input);
    }

    @Suggestions("skygrid-materials")
    public List<String> materialIds(String input) {
        List<String> ids = admin.current().skygrid().blocks().stream()
                .map(blockWeight -> blockWeight.material().asString())
                .toList();
        return filter(ids, input);
    }

    @Suggestions("skygrid-challenges")
    public List<String> challengeIds(String input) {
        List<String> ids = new ArrayList<>();
        for (ChallengeRule rule : challenges.list()) {
            ids.add(rule.id());
        }
        return filter(ids, input);
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
