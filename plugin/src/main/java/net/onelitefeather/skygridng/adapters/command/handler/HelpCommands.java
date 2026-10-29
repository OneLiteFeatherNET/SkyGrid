package net.onelitefeather.skygridng.adapters.command.handler;

import net.onelitefeather.skygridng.adapters.command.Messages;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.util.Locale;
import java.util.Map;

/**
 * Cloud annotated subcommands for the bare command and its help text, ported 1:1 from the legacy
 * {@code SkyGridCommand}: bare {@code /skygrid} and {@code /skygrid help} both send the full help
 * list; {@code /skygrid help <subcommand>} looks up the one-line usage for a known subcommand and
 * falls back to the full help on an unknown key.
 *
 * Has no port dependency at all, unlike the old monolithic command.
 */
public final class HelpCommands implements CloudCommandHandler {

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

    @Command("skygrid")
    @Permission("skygridng.admin")
    public void bare(CommandSource source) {
        sendHelp(source);
    }

    @Command("skygrid help")
    @Permission("skygridng.admin")
    public void help(CommandSource source) {
        sendHelp(source);
    }

    @Command("skygrid help <subcommand>")
    @Permission("skygridng.admin")
    public void help(CommandSource source, @Argument("subcommand") String subcommand) {
        String usage = USAGE.get(subcommand.toLowerCase(Locale.ROOT));
        if (usage != null) {
            source.sendMessage(Messages.usage(usage));
        } else {
            sendHelp(source);
        }
    }

    private void sendHelp(CommandSource source) {
        Messages.help().forEach(source::sendMessage);
    }
}
