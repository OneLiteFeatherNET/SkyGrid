package net.onelitefeather.skygridng.adapters.command;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import jakarta.inject.Inject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In adapter: the /skygrid command for runtime administration of the configuration.
 *
 * Knows only the {@link RuleAdminUseCase} port, no service implementation. Input is parsed and
 * delegated; the domain validates. All player facing text is built as translatable components by
 * {@link Messages}, so it renders in each viewer's locale.
 */
public final class SkyGridCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "preset", "presets", "worlds", "algorithm", "skyweight", "reload", "save", "undo",
            "challenge", "help");

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

    private static final List<String> CHALLENGE_SUBCOMMANDS = List.of("list", "toggle");

    private final RuleAdminUseCase admin;
    private final Presets presets;
    private final Optional<ChallengeAdminUseCase> challenges;

    @Inject
    public SkyGridCommand(RuleAdminUseCase admin, Presets presets, Optional<ChallengeAdminUseCase> challenges) {
        this.admin = admin;
        this.presets = presets;
        this.challenges = challenges;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skygridng.admin")) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            help(sender, args);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "presets" -> sender.sendMessage(Messages.presetList(String.join(", ", presets.names()), presets.active()));
            case "preset" -> preset(sender, args);
            case "worlds" -> sender.sendMessage(Messages.worldList(formatWorlds()));
            case "algorithm" -> algorithm(sender, args);
            case "skyweight" -> skyWeight(sender, args);
            case "reload" -> reload(sender, args);
            case "save" -> {
                admin.save();
                sender.sendMessage(Messages.saved());
            }
            case "undo" -> sender.sendMessage(admin.undo() ? Messages.undoDone() : Messages.undoEmpty());
            case "challenge" -> challenge(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void help(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        String usage = USAGE.get(args[1].toLowerCase(Locale.ROOT));
        if (usage != null) {
            sender.sendMessage(Messages.usage(usage));
        } else {
            sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        Messages.help().forEach(sender::sendMessage);
    }

    private void preset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.usage(USAGE.get("preset")));
            return;
        }
        String name = args[1];
        sender.sendMessage(presets.activate(name)
                ? Messages.presetSet(name) : Messages.presetUnknown(name));
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

    private void algorithm(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.algorithmCurrent(admin.algorithm()));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        sender.sendMessage(admin.setAlgorithm(id)
                ? Messages.algorithmSet(id) : Messages.algorithmUnknown(id));
    }

    private void skyWeight(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.usage(USAGE.get("skyweight")));
            return;
        }
        try {
            BlockId.of(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Messages.invalidMaterial(args[1]));
            return;
        }
        double value = parseDouble(args[2]);
        if (Double.isNaN(value)) {
            sender.sendMessage(Messages.notANumber(args[2]));
            return;
        }
        sender.sendMessage(admin.setSkyGridWeight(args[1], value)
                ? Messages.skyWeightSet(args[1], value) : Messages.weightFail(args[2]));
    }

    private void reload(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage(Messages.reloadConfirm());
            return;
        }
        admin.reload();
        sender.sendMessage(Messages.reloaded());
    }

    private void challenge(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.usage("/skygrid challenge <list|toggle> [id]"));
            return;
        }
        challenges.ifPresentOrElse(
                useCase -> {
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "list" -> challengeList(sender, useCase);
                        case "toggle" -> challengeToggle(sender, args, useCase);
                        default -> sender.sendMessage(Messages.usage("/skygrid challenge <list|toggle> [id]"));
                    }
                },
                () -> sender.sendMessage(Messages.challengeUnavailable()));
    }

    private void challengeList(CommandSender sender, ChallengeAdminUseCase useCase) {
        List<ChallengeRule> rules = useCase.list();
        if (rules.isEmpty()) {
            sender.sendMessage(Messages.challengeListEmpty());
            return;
        }
        for (ChallengeRule rule : rules) {
            sender.sendMessage(Messages.challengeListEntry(rule.id(), rule.active()));
        }
    }

    private void challengeToggle(CommandSender sender, String[] args, ChallengeAdminUseCase useCase) {
        if (args.length < 3) {
            sender.sendMessage(Messages.usage("/skygrid challenge toggle <id>"));
            return;
        }
        String id = args[2];
        boolean currentlyActive = useCase.isActive(id);
        boolean changed = currentlyActive ? useCase.deactivate(id) : useCase.activate(id);
        if (!changed) {
            sender.sendMessage(Messages.challengeUnknown(id));
            return;
        }
        sender.sendMessage(Messages.challengeToggled(id, !currentlyActive));
    }

    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skygridng.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "preset" -> filter(presets.names(), args[1]);
                case "algorithm" -> filter(List.copyOf(admin.algorithmIds()), args[1]);
                case "skyweight" -> filter(materialIds(), args[1]);
                case "reload" -> filter(List.of("confirm"), args[1]);
                case "challenge" -> filter(CHALLENGE_SUBCOMMANDS, args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("challenge") && args[1].equalsIgnoreCase("toggle")) {
            return challenges.map(useCase -> filter(challengeIds(useCase), args[2])).orElseGet(List::of);
        }
        return List.of();
    }

    private List<String> materialIds() {
        return admin.current().skygrid().blocks().stream()
                .map(blockWeight -> blockWeight.material().asString())
                .toList();
    }

    private List<String> challengeIds(ChallengeAdminUseCase useCase) {
        List<String> ids = new ArrayList<>();
        for (ChallengeRule rule : useCase.list()) {
            ids.add(rule.id());
        }
        return ids;
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
