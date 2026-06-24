package net.onelitefeather.skygridng.adapters.command;

import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import jakarta.inject.Inject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In adapter: the /skygrid command for runtime administration of the configuration.
 *
 * Knows only the {@link RuleAdminUseCase} port, no service implementation. Input is parsed and
 * delegated; the domain validates. All player facing text is built as translatable components by
 * {@link Messages}, so it renders in each viewer's locale.
 */
public final class SkyGridCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "list", "info", "weight", "allow", "disallow", "algorithm", "skyweight",
            "reload", "save", "undo", "help");

    private static final List<String> ALGORITHMS = List.of("wfc", "skygrid", "noise_skygrid");

    private final RuleAdminUseCase admin;

    @Inject
    public SkyGridCommand(RuleAdminUseCase admin) {
        this.admin = admin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skygridng.admin")) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(Messages.help());
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> sender.sendMessage(Messages.list(String.join(", ", admin.tileIds())));
            case "info" -> info(sender, args);
            case "weight" -> weight(sender, args);
            case "allow" -> allow(sender, args);
            case "disallow" -> disallow(sender, args);
            case "algorithm" -> algorithm(sender, args);
            case "skyweight" -> skyWeight(sender, args);
            case "reload" -> {
                admin.reload();
                sender.sendMessage(Messages.reloaded());
            }
            case "save" -> {
                admin.save();
                sender.sendMessage(Messages.saved());
            }
            case "undo" -> sender.sendMessage(admin.undo() ? Messages.undoDone() : Messages.undoEmpty());
            default -> sender.sendMessage(Messages.help());
        }
        return true;
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.usage("/skygrid info <tile>"));
            return;
        }
        admin.tile(args[1]).ifPresentOrElse(
                tile -> sender.sendMessage(Messages.info(
                        tile.id(), tile.weight(), tile.neutral(), tile.surface().asString(), tile.liquid(), tile.features().size())),
                () -> sender.sendMessage(Messages.unknownTile(args[1])));
    }

    private void weight(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.usage("/skygrid weight <tile> <value>"));
            return;
        }
        double value = parseDouble(args[2]);
        if (Double.isNaN(value)) {
            sender.sendMessage(Messages.notANumber());
            return;
        }
        sender.sendMessage(admin.setWeight(args[1], value)
                ? Messages.weightSet(args[1], value)
                : Messages.weightFail());
    }

    private void allow(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.usage("/skygrid allow <tileA> <tileB>"));
            return;
        }
        sender.sendMessage(admin.allow(args[1], args[2])
                ? Messages.allowed(args[1], args[2]) : Messages.unknownTilePair());
    }

    private void disallow(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.usage("/skygrid disallow <tileA> <tileB>"));
            return;
        }
        sender.sendMessage(admin.disallow(args[1], args[2])
                ? Messages.removed(args[1], args[2]) : Messages.unknownTilePair());
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
            sender.sendMessage(Messages.usage("/skygrid skyweight <material> <value>"));
            return;
        }
        double value = parseDouble(args[2]);
        if (Double.isNaN(value)) {
            sender.sendMessage(Messages.notANumber());
            return;
        }
        sender.sendMessage(admin.setSkyGridWeight(args[1], value)
                ? Messages.skyWeightSet(args[1], value) : Messages.weightFail());
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
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "info", "weight", "allow", "disallow" -> filter(admin.tileIds(), args[1]);
                case "algorithm" -> filter(ALGORITHMS, args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && (sub.equals("allow") || sub.equals("disallow"))) {
            return filter(admin.tileIds(), args[2]);
        }
        return List.of();
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
