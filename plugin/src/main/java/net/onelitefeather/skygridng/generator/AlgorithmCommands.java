package net.onelitefeather.skygridng.generator;

import net.onelitefeather.skygridng.command.Messages;
import net.onelitefeather.skygridng.command.CloudCommandHandler;
import net.onelitefeather.skygridng.command.CommandSource;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.util.Locale;

/**
 * Cloud annotated subcommands for algorithm and SkyGrid block weight administration, ported 1:1
 * from the legacy {@code SkyGridCommand}: {@code /skygrid algorithm [id]} and
 * {@code /skygrid skyweight <material> <value>}.
 *
 * {@code value} is deliberately received as a {@code String}, not a Cloud-parsed {@code double}:
 * an invalid number must still produce {@link Messages#notANumber(String)} exactly as before,
 * rather than a generic Cloud parse-failure message.
 */
public final class AlgorithmCommands implements CloudCommandHandler {

    private final RuleAdminUseCase admin;

    public AlgorithmCommands(RuleAdminUseCase admin) {
        this.admin = admin;
    }

    @Command("skygrid algorithm [id]")
    @Permission("skygridng.admin")
    public void algorithm(CommandSource source, @Argument(value = "id", suggestions = "algorithm-ids") String id) {
        if (id == null) {
            source.sendMessage(Messages.algorithmCurrent(admin.algorithm()));
            return;
        }
        String lowerId = id.toLowerCase(Locale.ROOT);
        source.sendMessage(admin.setAlgorithm(lowerId)
                ? Messages.algorithmSet(lowerId) : Messages.algorithmUnknown(lowerId));
    }

    @Command("skygrid skyweight <material> <value>")
    @Permission("skygridng.admin")
    public void skyweight(CommandSource source,
                           @Argument(value = "material", suggestions = "material-ids") String material,
                           @Argument("value") String value) {
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

    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
