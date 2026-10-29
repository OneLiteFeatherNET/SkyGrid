package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.adapters.command.handler.CloudCommandHandler;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cloud annotated subcommands for challenge administration, ported 1:1 from the legacy
 * {@code SkyGridCommand}: {@code /skygrid challenge list}, {@code /skygrid challenge toggle <id>},
 * {@code /skygrid challenge tier [player]}, {@code /skygrid challenge tier reset [confirm]}.
 */
public final class ChallengeCommands implements CloudCommandHandler {

    /**
     * The only challenge this command's {@code tier} subcommand reports on today. Tier progress is
     * per challenge, but only {@code one_life} has a wired {@link ChallengeProgressUseCase} producer
     * ({@code OneLifeListener}) so far; a future revision could add a challenge id argument once more
     * challenges record progress. Deliberately hardcoded here rather than taken from the command line,
     * matching that scope.
     */
    private static final String ONE_LIFE_CHALLENGE_ID = "one_life";

    private final ChallengeAdminUseCase challenges;
    private final ChallengeProgressUseCase progress;

    public ChallengeCommands(ChallengeAdminUseCase challenges, ChallengeProgressUseCase progress) {
        this.challenges = challenges;
        this.progress = progress;
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

    /**
     * Checks the invoking player's own {@value #ONE_LIFE_CHALLENGE_ID} tier when called with no
     * argument (the console is rejected instead, since it has no tier of its own), or the named
     * player's tier when an argument is given.
     */
    @Command("skygrid challenge tier [player]")
    @Permission("skygridng.admin")
    public void tier(CommandSource source, @Argument(value = "player", suggestions = "online-players") String player) {
        if (player == null) {
            Optional<UUID> self = source.playerId();
            if (self.isEmpty()) {
                source.sendMessage(Messages.challengeTierConsole());
                return;
            }
            int tier = progress.tierOf(self.get(), ONE_LIFE_CHALLENGE_ID);
            source.sendMessage(Messages.challengeTierSelf(tier));
            return;
        }
        UUID target = source.resolvePlayerId(player).orElseThrow();
        int tier = progress.tierOf(target, ONE_LIFE_CHALLENGE_ID);
        source.sendMessage(Messages.challengeTierOther(player, tier));
    }

    /**
     * The one destructive form of {@code tier}: clears every player's progress for every
     * challenge. Like {@code reload}, it requires an explicit {@code confirm} argument before it
     * actually runs; without it, the sender only sees a warning naming what will be lost.
     */
    @Command("skygrid challenge tier reset")
    @Permission("skygridng.admin")
    public void tierReset(CommandSource source) {
        source.sendMessage(Messages.challengeTierResetConfirm());
    }

    @Command("skygrid challenge tier reset confirm")
    @Permission("skygridng.admin")
    public void tierResetConfirm(CommandSource source) {
        progress.resetCurrentSeason();
        source.sendMessage(Messages.challengeTierReset());
    }
}
