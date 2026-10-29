package net.onelitefeather.skygridng.adapters.command.handler;

import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonStatus;
import net.onelitefeather.skygridng.ports.in.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import net.onelitefeather.skygridng.ports.in.SeasonAdminUseCase;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cloud annotated subcommands for the season lifecycle: {@code /skygrid season info}, {@code list}, {@code status},
 * {@code history [player]}, {@code advance [confirm]}, {@code advance now confirm}, {@code postpone <duration> [confirm]},
 * {@code retry} and {@code reload [confirm]}. Every state change needs its explicit {@code confirm} form. The unconfirmed
 * form only warns, so a mistyped command changes nothing.
 */
public final class SeasonCommands implements CloudCommandHandler {

    private static final String ADMIN = "skygridng.admin";
    private static final Pattern DURATION = Pattern.compile("(\\d{1,9})([mhd])");

    private final SeasonAdminUseCase seasons;
    private final CurrentSeason current;
    private final ChallengeProgressUseCase progress;
    private final Clock clock;

    public SeasonCommands(SeasonAdminUseCase seasons, CurrentSeason current, ChallengeProgressUseCase progress,
                          Clock clock) {
        this.seasons = seasons;
        this.current = current;
        this.progress = progress;
        this.clock = clock;
    }

    @Command("skygrid season info")
    public void info(CommandSource source) {
        Optional<Season> season = current.season();
        if (season.isEmpty()) {
            Optional<Season> upcoming = seasons.list().seasons().stream()
                    .filter(next -> next.end().isAfter(clock.instant())).findFirst();
            if (upcoming.isEmpty()) {
                source.sendMessage(Messages.seasonNone());
                return;
            }
            source.sendMessage(Messages.seasonUpcoming(upcoming.get().displayName(),
                    Messages.seasonDate(upcoming.get().start(), source.locale())));
            return;
        }
        String end = Messages.seasonDate(season.get().end(), source.locale());
        sendAll(source, Messages.seasonInfo(season.get().id().value(), season.get().displayName(), end,
                pendingMailbox(source)));
    }

    @Command("skygrid season list")
    @Permission(ADMIN)
    public void list(CommandSource source) {
        List<Season> seasonList = seasons.list().seasons();
        if (seasonList.isEmpty()) {
            source.sendMessage(Messages.seasonListEmpty());
            return;
        }
        for (Season season : seasonList) {
            source.sendMessage(Messages.seasonListEntry(season.id().value(), season.displayName(),
                    Messages.seasonDate(season.start(), source.locale()),
                    Messages.seasonDate(season.end(), source.locale())));
        }
    }

    @Command("skygrid season status")
    @Permission(ADMIN)
    public void status(CommandSource source) {
        Optional<SeasonStatus> status = seasons.status(clock.instant());
        if (status.isEmpty()) {
            source.sendMessage(Messages.seasonRefused());
            return;
        }
        SeasonStatus value = status.get();
        if (value.activeSeason().equals(SeasonId.LEGACY) && value.nextSeason() == null) {
            source.sendMessage(Messages.seasonNone());
            return;
        }
        String next = value.nextSeason() == null ? null : value.nextSeason().value();
        String endsAt = value.nextActionAt() == null ? "-" : Messages.seasonDate(value.nextActionAt(), source.locale());
        source.sendMessage(Messages.seasonStatus(value.activeSeason().value(), value.phase().name(), next, endsAt));
    }

    /**
     * The tier history of a player, per season. The sender may read their own history; another player's needs
     * {@code skygridng.admin}, checked here because the sender may read their own without it.
     */
    @Command("skygrid season history [player]")
    public void history(CommandSource source, @Argument(value = "player", suggestions = "online-players") String player) {
        UUID target;
        if (player == null) {
            Optional<UUID> self = source.playerId();
            if (self.isEmpty()) {
                source.sendMessage(Messages.usage("/skygrid season history <player>"));
                return;
            }
            target = self.get();
        } else {
            if (!source.hasPermission(ADMIN)) {
                source.sendMessage(Messages.noPermission());
                return;
            }
            target = source.resolvePlayerId(player).orElseThrow();
        }
        Map<SeasonId, Integer> history = progress.history(target);
        if (history.isEmpty()) {
            source.sendMessage(Messages.seasonHistoryEmpty());
            return;
        }
        history.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                .forEach(entry -> source.sendMessage(Messages.seasonHistoryEntry(entry.getKey().value(),
                        entry.getValue())));
    }

    @Command("skygrid season advance")
    @Permission(ADMIN)
    public void advance(CommandSource source) {
        source.sendMessage(Messages.seasonAdvanceWarn());
    }

    /** Schedules the season end at least the configured minimum notice away. It never starts the transition at once. */
    @Command("skygrid season advance confirm")
    @Permission(ADMIN)
    public void advanceConfirm(CommandSource source) {
        boolean scheduled = seasons.advance(actor(source), clock.instant());
        source.sendMessage(scheduled ? Messages.seasonAdvanced() : Messages.seasonRefused());
    }

    @Command("skygrid season advance now")
    @Permission(ADMIN)
    public void advanceNow(CommandSource source) {
        source.sendMessage(Messages.seasonAdvanceNowWarn());
    }

    /** The only path that ends the active season at once. The acting admin and the instant are logged by the service. */
    @Command("skygrid season advance now confirm")
    @Permission(ADMIN)
    public void advanceNowConfirm(CommandSource source) {
        boolean ended = seasons.advanceNow(actor(source), clock.instant());
        source.sendMessage(ended ? Messages.seasonAdvancedNow() : Messages.seasonRefused());
    }

    @Command("skygrid season postpone <duration>")
    @Permission(ADMIN)
    public void postpone(CommandSource source, @Argument("duration") String duration) {
        source.sendMessage(Messages.seasonPostponeWarn(duration));
    }

    @Command("skygrid season postpone <duration> confirm")
    @Permission(ADMIN)
    public void postponeConfirm(CommandSource source, @Argument("duration") String duration) {
        Optional<Duration> parsed = parseDuration(duration);
        if (parsed.isEmpty()) {
            source.sendMessage(Messages.seasonDurationInvalid(duration));
            return;
        }
        boolean moved = seasons.postpone(clock.instant(), parsed.get());
        source.sendMessage(moved ? Messages.seasonPostponed(duration) : Messages.seasonRefused());
    }

    @Command("skygrid season retry")
    @Permission(ADMIN)
    public void retry(CommandSource source) {
        boolean retried = seasons.retry(clock.instant());
        source.sendMessage(retried ? Messages.seasonRetried() : Messages.seasonRefused());
    }

    @Command("skygrid season reload")
    @Permission(ADMIN)
    public void reload(CommandSource source) {
        source.sendMessage(Messages.seasonReloadWarn());
    }

    @Command("skygrid season reload confirm")
    @Permission(ADMIN)
    public void reloadConfirm(CommandSource source) {
        boolean reloaded = seasons.reload();
        source.sendMessage(reloaded ? Messages.seasonReloaded() : Messages.seasonRefused());
    }

    /**
     * The mailbox count of the viewer. It is always 0 until the economy adds pending mailbox items; the hook stays here so
     * the reset lines already show the count.
     */
    private static int pendingMailbox(CommandSource source) {
        return 0;
    }

    private static String actor(CommandSource source) {
        return source.playerId().map(UUID::toString).orElse("console");
    }

    private static void sendAll(CommandSource source, List<Component> lines) {
        lines.forEach(source::sendMessage);
    }

    /** A number followed by {@code m}, {@code h} or {@code d}, for example {@code 2h}. */
    static Optional<Duration> parseDuration(String raw) {
        Matcher matcher = DURATION.matcher(raw);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long amount = Long.parseLong(matcher.group(1));
        return Optional.of(switch (matcher.group(2)) {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> Duration.ofDays(amount);
        });
    }
}
