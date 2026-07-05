package net.onelitefeather.skygridng.adapters.command;

import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.api.model.BlockId;
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

/**
 * In adapter: the /skygrid command for runtime administration of the configuration.
 *
 * Knows only the {@link RuleAdminUseCase} port, no service implementation. Input is parsed and
 * delegated; the domain validates. All player facing text is built as translatable components by
 * {@link Messages}, so it renders in each viewer's locale.
 */
public final class SkyGridCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "preset", "presets", "worlds", "list", "info", "weight", "allow", "disallow", "algorithm",
            "skyweight", "reload", "save", "undo", "help");

    private static final Map<String, String> USAGE = Map.ofEntries(
            Map.entry("weight", "/skygrid weight <tile> <value>"),
            Map.entry("reload", "/skygrid reload confirm"),
            Map.entry("algorithm", "/skygrid algorithm [id]"),
            Map.entry("presets", "/skygrid presets"),
            Map.entry("worlds", "/skygrid worlds"),
            Map.entry("list", "/skygrid list"),
            Map.entry("undo", "/skygrid undo"),
            Map.entry("save", "/skygrid save"),
            Map.entry("preset", "/skygrid preset <name>"),
            Map.entry("info", "/skygrid info <tile>"),
            Map.entry("allow", "/skygrid allow <tileA> <tileB>"),
            Map.entry("disallow", "/skygrid disallow <tileA> <tileB>"),
            Map.entry("skyweight", "/skygrid skyweight <material> <value>"));

    private final RuleAdminUseCase admin;
    private final Presets presets;

    @Inject
    public SkyGridCommand(RuleAdminUseCase admin, Presets presets) {
        this.admin = admin;
        this.presets = presets;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skygridng.admin")) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Messages.help());
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
            case "list" -> sender.sendMessage(Messages.list(String.join(", ", admin.tileIds())));
            case "info" -> info(sender, args);
            case "weight" -> weight(sender, args);
            case "allow" -> allow(sender, args);
            case "disallow" -> disallow(sender, args);
            case "algorithm" -> algorithm(sender, args);
            case "skyweight" -> skyWeight(sender, args);
            case "reload" -> reload(sender, args);
            case "save" -> {
                admin.save();
                sender.sendMessage(Messages.saved());
            }
            case "undo" -> sender.sendMessage(admin.undo() ? Messages.undoDone() : Messages.undoEmpty());
            default -> sender.sendMessage(Messages.help());
        }
        return true;
    }

    private void help(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.help());
            return;
        }
        String usage = USAGE.get(args[1].toLowerCase(Locale.ROOT));
        if (usage != null) {
            sender.sendMessage(Messages.usage(usage));
        } else {
            sender.sendMessage(Messages.help());
        }
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

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.usage(USAGE.get("info")));
            return;
        }
        admin.tile(args[1]).ifPresentOrElse(
                tile -> sender.sendMessage(Messages.info(
                        tile.id(), tile.weight(), tile.neutral(), tile.surface().asString(), tile.liquid(), tile.features().size())),
                () -> sender.sendMessage(Messages.unknownTile(args[1])));
    }

    private void weight(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.usage(USAGE.get("weight")));
            return;
        }
        if (admin.tile(args[1]).isEmpty()) {
            sender.sendMessage(Messages.unknownTile(args[1]));
            return;
        }
        double value = parseDouble(args[2]);
        if (Double.isNaN(value)) {
            sender.sendMessage(Messages.notANumber(args[2]));
            return;
        }
        sender.sendMessage(admin.setWeight(args[1], value)
                ? Messages.weightSet(args[1], value)
                : Messages.weightFail(args[2]));
    }

    private Component firstUnknownTile(String... ids) {
        for (String id : ids) {
            if (admin.tile(id).isEmpty()) {
                return Messages.unknownTile(id);
            }
        }
        return null;
    }

    private void allow(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.usage(USAGE.get("allow")));
            return;
        }
        Component unknown = firstUnknownTile(args[1], args[2]);
        if (unknown != null) {
            sender.sendMessage(unknown);
            return;
        }
        admin.allow(args[1], args[2]);
        sender.sendMessage(Messages.allowed(args[1], args[2]));
    }

    private void disallow(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.usage(USAGE.get("disallow")));
            return;
        }
        Component unknown = firstUnknownTile(args[1], args[2]);
        if (unknown != null) {
            sender.sendMessage(unknown);
            return;
        }
        admin.disallow(args[1], args[2]);
        sender.sendMessage(Messages.removed(args[1], args[2]));
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
                case "info", "weight", "allow", "disallow" -> filter(admin.tileIds(), args[1]);
                case "algorithm" -> filter(List.copyOf(admin.algorithmIds()), args[1]);
                case "skyweight" -> filter(materialIds(), args[1]);
                case "reload" -> filter(List.of("confirm"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && (sub.equals("allow") || sub.equals("disallow"))) {
            return filter(admin.tileIds(), args[2]);
        }
        return List.of();
    }

    private List<String> materialIds() {
        return admin.current().skygrid().blocks().stream()
                .map(blockWeight -> blockWeight.material().asString())
                .toList();
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
