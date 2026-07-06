package net.onelitefeather.skygridng.adapters.command.handler;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.util.List;

/**
 * Cloud annotated subcommands for challenge administration, ported 1:1 from the legacy
 * {@code SkyGridCommand}: {@code /skygrid challenge list}, {@code /skygrid challenge toggle <id>}.
 */
public final class ChallengeCommands implements CloudCommandHandler {

    private final ChallengeAdminUseCase challenges;

    @Inject
    public ChallengeCommands(ChallengeAdminUseCase challenges) {
        this.challenges = challenges;
    }

    @Command("skygrid challenge list")
    @Permission("skygridng.admin")
    public void list(CommandSource source) {
        List<ChallengeRule> rules = challenges.list();
        if (rules.isEmpty()) {
            source.sendMessage(Messages.challengeListEmpty());
            return;
        }
        for (ChallengeRule rule : rules) {
            source.sendMessage(Messages.challengeListEntry(rule.id(), rule.active()));
        }
    }

    @Command("skygrid challenge toggle <id>")
    @Permission("skygridng.admin")
    public void toggle(CommandSource source, @Argument(value = "id", suggestions = "challenge-ids") String id) {
        boolean currentlyActive = challenges.isActive(id);
        boolean changed = currentlyActive ? challenges.deactivate(id) : challenges.activate(id);
        if (!changed) {
            source.sendMessage(Messages.challengeUnknown(id));
            return;
        }
        source.sendMessage(Messages.challengeToggled(id, !currentlyActive));
    }
}
