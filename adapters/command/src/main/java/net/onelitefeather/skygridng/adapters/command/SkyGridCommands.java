package net.onelitefeather.skygridng.adapters.command;

import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.exception.ExceptionHandler;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Driving adapter: the /skygrid command for runtime administration of the configuration.
 *
 * Migration stage 2 (Incendo Cloud): every subcommand is a Cloud-annotated method operating on the
 * neutral {@link CommandSource}, never a Bukkit sender, and only the three in ports below (no
 * service implementation). Input is parsed by Cloud and delegated; the domain validates. All
 * player facing text is built as translatable components by {@link Messages}, so it renders in
 * each viewer's locale.
 *
 * Framework-free like {@code core}: no Bukkit, no Cloud Paper/Brigadier types, no Guice or
 * jakarta.inject import anywhere in this class, only plain {@code org.incendo.cloud.core} and
 * {@code org.incendo.cloud.annotations} packages. The plain constructor below is instantiated in
 * {@code platforms/common}'s {@code CloudCommandRegistrar} the same way
 * {@code SkyGridDecorationPopulator} does {@code new DecorationEngine(...)}: Guice injects the
 * ports there, this class is never itself Guice-managed.
 *
 * The class-level {@link Permission} and {@link CommandDescription} apply to every subcommand
 * method below (Cloud merges method and class level annotations); the class-level {@link Command}
 * value is prepended as the syntax prefix to each method's own (shorter) syntax string.
 *
 * {@link #unmatched} is a real subcommand, not just an exception handler, precisely because the
 * live command manager mirrors this whole class into a native Brigadier tree; see its javadoc and
 * {@link #handleInvalidSyntax}'s for why an unrecognized first token has to be caught this way.
 */
@Command("skygrid")
@CommandDescription("Manages the generator configuration at runtime.")
@Permission("skygridng.admin")
public final class SkyGridCommands {

    /**
     * Usage strings for {@code /skygrid help <subcommand>}, keyed by subcommand name. Kept
     * package-private (via the enclosing class) so {@code SkyGridArgumentParsers}, which lives in a
     * sibling package, cannot see it; its own tab-completion suggestion list is a small, intentional
     * duplicate of this map's key set instead.
     */
    private static final Map<String, String> USAGE = Map.ofEntries(
            Map.entry("reload", "/skygrid reload confirm"),
            Map.entry("algorithm", "/skygrid algorithm [id]"),
            Map.entry("presets", "/skygrid presets"),
            Map.entry("worlds", "/skygrid worlds"),
            Map.entry("undo", "/skygrid undo"),
            Map.entry("save", "/skygrid save"),
            Map.entry("preset", "/skygrid preset <name>"),
            Map.entry("skyweight", "/skygrid skyweight <material> <value>"),
            Map.entry("challenge", "/skygrid challenge <list|toggle> [id]"));

    private final RuleAdminUseCase admin;
    private final Presets presets;
    private final ChallengeAdminUseCase challenges;

    public SkyGridCommands(RuleAdminUseCase admin, Presets presets, ChallengeAdminUseCase challenges) {
        this.admin = admin;
        this.presets = presets;
        this.challenges = challenges;
    }

    @Command("")
    public void root(CommandSource source) {
        sendHelp(source);
    }

    @Command("help [subcommand]")
    public void help(
            CommandSource source,
            @Argument(value = "subcommand", suggestions = "skygrid-help-subcommands") String subcommand
    ) {
        if (subcommand == null) {
            sendHelp(source);
            return;
        }
        String usage = USAGE.get(subcommand.toLowerCase(Locale.ROOT));
        if (usage != null) {
            source.sendMessage(Messages.usage(usage));
        } else {
            sendHelp(source);
        }
    }

    @Command("presets")
    public void presetsList(CommandSource source) {
        source.sendMessage(Messages.presetList(String.join(", ", presets.names()), presets.active()));
    }

    @Command("preset [name]")
    public void preset(
            CommandSource source,
            @Argument(value = "name", suggestions = "skygrid-presets") String name
    ) {
        if (name == null) {
            source.sendMessage(Messages.usage(USAGE.get("preset")));
            return;
        }
        source.sendMessage(presets.activate(name) ? Messages.presetSet(name) : Messages.presetUnknown(name));
    }

    @Command("worlds")
    public void worlds(CommandSource source) {
        source.sendMessage(Messages.worldList(formatWorlds()));
    }

    @Command("algorithm [id]")
    public void algorithm(
            CommandSource source,
            @Argument(value = "id", suggestions = "skygrid-algorithms") String id
    ) {
        if (id == null) {
            source.sendMessage(Messages.algorithmCurrent(admin.algorithm()));
            return;
        }
        String lowered = id.toLowerCase(Locale.ROOT);
        source.sendMessage(admin.setAlgorithm(lowered) ? Messages.algorithmSet(lowered) : Messages.algorithmUnknown(lowered));
    }

    @Command("skyweight [material] [value]")
    public void skyWeight(
            CommandSource source,
            @Argument(value = "material", suggestions = "skygrid-materials") String material,
            @Argument("value") String value
    ) {
        if (material == null || value == null) {
            source.sendMessage(Messages.usage(USAGE.get("skyweight")));
            return;
        }
        try {
            BlockId.of(material);
        } catch (IllegalArgumentException e) {
            source.sendMessage(Messages.invalidMaterial(material));
            return;
        }
        double parsed = parseDouble(value);
        if (Double.isNaN(parsed)) {
            source.sendMessage(Messages.notANumber(value));
            return;
        }
        source.sendMessage(admin.setSkyGridWeight(material, parsed)
                ? Messages.skyWeightSet(material, parsed) : Messages.weightFail(value));
    }

    @Command("reload")
    public void reload(CommandSource source) {
        source.sendMessage(Messages.reloadConfirm());
    }

    @Command("reload confirm")
    public void reloadConfirm(CommandSource source) {
        admin.reload();
        source.sendMessage(Messages.reloaded());
    }

    @Command("save")
    public void save(CommandSource source) {
        admin.save();
        source.sendMessage(Messages.saved());
    }

    @Command("undo")
    public void undo(CommandSource source) {
        source.sendMessage(admin.undo() ? Messages.undoDone() : Messages.undoEmpty());
    }

    @Command("challenge")
    public void challengeUsage(CommandSource source) {
        source.sendMessage(Messages.usage(USAGE.get("challenge")));
    }

    @Command("challenge list")
    public void challengeList(CommandSource source) {
        List<ChallengeRule> rules = challenges.list();
        if (rules.isEmpty()) {
            source.sendMessage(Messages.challengeListEmpty());
            return;
        }
        for (ChallengeRule rule : rules) {
            source.sendMessage(Messages.challengeListEntry(rule.id(), rule.active()));
        }
    }

    @Command("challenge toggle [id]")
    public void challengeToggle(
            CommandSource source,
            @Argument(value = "id", suggestions = "skygrid-challenges") String id
    ) {
        if (id == null) {
            source.sendMessage(Messages.usage("/skygrid challenge toggle <id>"));
            return;
        }
        boolean currentlyActive = challenges.isActive(id);
        boolean changed = currentlyActive ? challenges.deactivate(id) : challenges.activate(id);
        if (!changed) {
            source.sendMessage(Messages.challengeUnknown(id));
            return;
        }
        source.sendMessage(Messages.challengeToggled(id, !currentlyActive));
    }

    /**
     * Cloud's own permission gate (the class-level {@link Permission} above) throws this when it
     * runs; mapped here, in the same class as {@link Messages}, so the player-visible text matches
     * the old hand-rolled command exactly without exposing {@code Messages} outside this package.
     *
     * <p>Whether this actually runs during a live dispatch depends on the sender's own permission:
     * {@code cloud-brigadier} mirrors every node's permission check into the Brigadier node's own
     * {@code requires()} predicate (see {@code BrigadierPermissionPredicate}), so a sender without
     * {@code skygridng.admin} never has "skygrid" itself resolve as a usable node in the mirrored
     * Brigadier tree in the first place; Mojang's own dispatcher then rejects the input as an unknown
     * command before Cloud's exception pipeline ever runs, the same failure mode described in detail
     * on {@link #handleInvalidSyntax}. This has not been exercised against a live, unprivileged
     * sender (the smoke test that found the {@link #handleInvalidSyntax} gap only used an admin
     * account), so treat this as a documented risk rather than a confirmed one.</p>
     */
    @ExceptionHandler(NoPermissionException.class)
    public void handleNoPermission(CommandSource source) {
        source.sendMessage(Messages.noPermission());
    }

    /**
     * Handles an {@link InvalidSyntaxException} thrown by Cloud's own {@code CommandTree} when it
     * actually gets to run one. In practice, through the real dispatch path this command uses
     * ({@code PaperCommandManager}'s built in, always on {@code ModernPaperBrigadier} registration
     * handler), that never happens for the case this handler was originally written for.
     *
     * <p>{@code cloud-brigadier}'s {@code LiteralBrigadierNodeFactory} does not register one
     * Brigadier node per literal with a permissive catch-all behind it: it mirrors the entire Cloud
     * command tree, literal for literal and argument for argument, into a real Brigadier
     * {@code LiteralCommandNode} tree, and {@code ModernPaperBrigadier} registers that tree directly
     * with Paper's native {@code Commands}/{@code CommandDispatcher} (used for both players and
     * console). Mojang's own {@code CommandDispatcher.execute(...)} rejects any input that does not
     * fully match a path through that mirrored tree, with Brigadier's own generic "Incorrect argument
     * for command" message, before ever calling the {@code Command<S>} callback that bridges into
     * Cloud (see {@code CloudBrigadierCommand.run}). Cloud's own {@code CommandTree.parse(...)}, and
     * therefore this {@link InvalidSyntaxException} plus the {@code @ExceptionHandler} pipeline that
     * routes it here, is only ever reached for input that Brigadier already accepted as a complete,
     * valid path, so an unrecognized first token such as {@code /skygrid nonsense} never reaches this
     * method in a live dispatch. There is no {@code PaperCommandManager} builder option or
     * {@code CloudBrigadierManager} setting that changes this ({@code BrigadierSetting.FORCE_EXECUTABLE}
     * only controls whether intermediate nodes get an {@code executes()} callback so Cloud can report
     * errors on a partially completed, otherwise-valid path; it does not affect a token that matches
     * no node at all); the only way around it entirely would be to stop using
     * {@code PaperCommandManager}'s native Brigadier registration, which would also give up its
     * client-side tab completion and argument highlighting for the whole command.
     *
     * <p>The actual fix for the unrecognized-subcommand case is {@link #unmatched}: a required,
     * greedy {@code String[]} argument registered as a sibling of every literal subcommand above.
     * Brigadier always tries literal children before argument children at a given tree depth, and
     * Cloud's own ambiguity check ({@code CommandTree.checkAmbiguity}) explicitly allows any number of
     * literal children plus exactly one variable child at the same node, so a real subcommand keeps
     * matching first and only a token that matches none of them falls through to that method, inside
     * the mirrored Brigadier tree itself rather than through Cloud's exception pipeline.
     *
     * <p>This handler is kept as a defensive fallback (for example, a future command registration
     * handler that does not mirror the whole tree into Brigadier, or a direct, non-Brigadier call into
     * the command executor) rather than removed, even though {@link #unmatched} handles the case it
     * was originally written for on the live server.
     */
    @ExceptionHandler(InvalidSyntaxException.class)
    public void handleInvalidSyntax(CommandSource source) {
        sendHelp(source);
    }

    /**
     * Fallback for a first token that matches none of the literal subcommands above (the old
     * dispatch's {@code default -> sendHelp(sender)}), registered as a required, greedy
     * {@code String[]} argument at the same tree depth as every subcommand literal in this class.
     * See the second half of {@link #handleInvalidSyntax}'s javadoc for why this had to be a real
     * command rather than an exception handler: {@code cloud-brigadier} mirrors this class's whole
     * command tree into Brigadier, literal children are tried before argument children at each node,
     * and this argument only matches once none of the literals do, so real subcommands are never
     * shadowed by it. The greedy {@code String[]} type (Cloud's {@code StringArrayParser}, mapped to
     * Brigadier's {@code greedyString()}) consumes every remaining token in one go, so this also
     * covers trailing input after an otherwise complete command (for example
     * {@code /skygrid save foo}), which the old switch silently ignored; showing the help text for
     * that edge case too remains an acceptable, harmless deviation rather than a behavior this
     * command needs to preserve.
     */
    @Command("<unmatched>")
    public void unmatched(CommandSource source, @Argument("unmatched") String[] unmatched) {
        sendHelp(source);
    }

    private void sendHelp(CommandSource source) {
        Messages.help().forEach(source::sendMessage);
    }

    private String formatWorlds() {
        Map<String, String> worlds = presets.worlds();
        if (worlds.isEmpty()) {
            return "(none yet)";
        }
        StringBuilder out = new StringBuilder();
        worlds.forEach((world, preset) -> {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(world).append('=').append(preset);
        });
        return out.toString();
    }

    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
