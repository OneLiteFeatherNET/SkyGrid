package net.onelitefeather.skygridng.adapters.command.handler;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.ports.in.Presets;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.util.Map;

/**
 * Cloud annotated subcommands for preset and world administration, ported 1:1 from the legacy
 * {@code SkyGridCommand}: {@code /skygrid preset <name>}, {@code /skygrid presets},
 * {@code /skygrid worlds}.
 */
public final class PresetCommands implements CloudCommandHandler {

    private final Presets presets;

    @Inject
    public PresetCommands(Presets presets) {
        this.presets = presets;
    }

    @Command("skygrid preset <name>")
    @Permission("skygridng.admin")
    public void preset(CommandSource source, @Argument(value = "name", suggestions = "preset-names") String name) {
        source.sendMessage(presets.activate(name) ? Messages.presetSet(name) : Messages.presetUnknown(name));
    }

    @Command("skygrid presets")
    @Permission("skygridng.admin")
    public void presets(CommandSource source) {
        source.sendMessage(Messages.presetList(String.join(", ", presets.names()), presets.active()));
    }

    @Command("skygrid worlds")
    @Permission("skygridng.admin")
    public void worlds(CommandSource source) {
        source.sendMessage(Messages.worldList(formatWorlds()));
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
}
