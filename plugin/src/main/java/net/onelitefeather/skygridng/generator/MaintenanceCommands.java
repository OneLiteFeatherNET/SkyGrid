package net.onelitefeather.skygridng.generator;

import net.onelitefeather.skygridng.command.Messages;
import net.onelitefeather.skygridng.command.CloudCommandHandler;
import net.onelitefeather.skygridng.command.CommandSource;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

/**
 * Cloud annotated subcommands for config maintenance, ported 1:1 from the legacy
 * {@code SkyGridCommand}: {@code /skygrid reload}, {@code /skygrid reload confirm},
 * {@code /skygrid save}, {@code /skygrid undo}.
 *
 * {@code reload} and {@code reload confirm} are two separate annotated methods so that the
 * literal {@code confirm} token routes to the confirmed reload, and every other invocation
 * (including no second argument) routes to the warning, mirroring the old code's
 * {@code args.length < 2 || !args[1].equalsIgnoreCase("confirm")} check.
 */
public final class MaintenanceCommands implements CloudCommandHandler {

    private final RuleAdminUseCase admin;

    public MaintenanceCommands(RuleAdminUseCase admin) {
        this.admin = admin;
    }

    @Command("skygrid reload")
    @Permission("skygridng.admin")
    public void reload(CommandSource source) {
        source.sendMessage(Messages.reloadConfirm());
    }

    @Command("skygrid reload confirm")
    @Permission("skygridng.admin")
    public void reloadConfirm(CommandSource source) {
        admin.reload();
        source.sendMessage(Messages.reloaded());
    }

    @Command("skygrid save")
    @Permission("skygridng.admin")
    public void save(CommandSource source) {
        admin.save();
        source.sendMessage(Messages.saved());
    }

    @Command("skygrid undo")
    @Permission("skygridng.admin")
    public void undo(CommandSource source) {
        source.sendMessage(admin.undo() ? Messages.undoDone() : Messages.undoEmpty());
    }
}
