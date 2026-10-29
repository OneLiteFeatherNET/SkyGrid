package net.onelitefeather.skygridng.season;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.challenge.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.adapters.command.handler.RecordingCommandSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SeasonCommands}. The clock is fixed and the sender is {@link RecordingCommandSource}, so the
 * tests need no server. Unconfirmed forms of the state-changing commands must only warn.
 */
class SeasonCommandsTest {

    private static final Instant NOW = Instant.parse("2027-01-10T12:00:00Z");
    private static final Instant START = Instant.parse("2027-01-01T00:00:00Z");
    private static final Season S01 = new Season(new SeasonId("s01"), "Season 1", START, Duration.ofDays(28),
            "standard", List.of(), null);

    private SeasonAdminUseCase seasons;
    private ChallengeProgressUseCase progress;
    private CurrentSeason current;
    private SeasonCommands subject;
    private RecordingCommandSource source;

    @BeforeEach
    void setUp() {
        seasons = mock(SeasonAdminUseCase.class);
        progress = mock(ChallengeProgressUseCase.class);
        current = mock(CurrentSeason.class);
        when(current.season()).thenReturn(Optional.of(S01));
        when(seasons.list()).thenReturn(new SeasonCatalog(List.of(S01)));
        subject = new SeasonCommands(seasons, current, progress, Clock.fixed(NOW, ZoneOffset.UTC));
        source = new RecordingCommandSource();
    }

    @Test
    @DisplayName("'season info' sends the season lines with the absolute end date and both reset lists")
    void infoSendsSeasonLinesWithEndDateAndResetLists() {
        subject.info(source);

        assertThat(source.messages())
                .as("info names the season with its absolute end date")
                .containsAll(Messages.seasonInfo(S01.id().value(), S01.displayName(),
                        Messages.seasonDate(S01.end(), source.locale()), 0))
                .as("info names what is deleted and what is kept at the season end")
                .containsAll(Messages.seasonResetLines(0));
    }

    @Test
    @DisplayName("'season info' without a configured season says so")
    void infoWithoutSeasonSaysNoSeason() {
        when(current.season()).thenReturn(Optional.empty());
        when(seasons.list()).thenReturn(SeasonCatalog.empty());

        subject.info(source);

        assertThat(source.messages()).containsExactly(Messages.seasonNone());
    }

    @Test
    @DisplayName("'season list' sends one entry per configured season")
    void listSendsOneEntryPerSeason() {
        subject.list(source);

        assertThat(source.messages()).containsExactly(Messages.seasonListEntry("s01", "Season 1",
                Messages.seasonDate(START, source.locale()), Messages.seasonDate(S01.end(), source.locale())));
        verify(seasons).list();
    }

    @Test
    @DisplayName("'season list' renders the MiniMessage display name as a styled component, not raw tags")
    void listRendersDisplayNameAsMiniMessage() {
        Season gold = new Season(new SeasonId("s01"), "<gold>Season 1: Herbst</gold>", START,
                Duration.ofDays(28), "standard", List.of(), null);
        when(seasons.list()).thenReturn(new SeasonCatalog(List.of(gold)));

        subject.list(source);

        assertThat(source.messages()).hasSize(1);
        assertThat(argumentsOf(source.messages().get(0)))
                .as("the name is passed as the parsed MiniMessage component, so the tags are not shown raw")
                .contains(MiniMessage.miniMessage().deserialize("<gold>Season 1: Herbst</gold>"));
    }

    @Test
    @DisplayName("'season info' while legacy runs and a season is upcoming names that season and its start")
    void infoWithUpcomingSeasonNamesItAndItsStart() {
        Season upcoming = new Season(new SeasonId("s01_autumn"), "<gold>Autumn</gold>",
                Instant.parse("2027-02-01T00:00:00Z"), Duration.ofDays(28), "standard", List.of(), null);
        when(current.season()).thenReturn(Optional.empty());
        when(seasons.list()).thenReturn(new SeasonCatalog(List.of(upcoming)));

        subject.info(source);

        assertThat(source.messages()).hasSize(1);
        assertThat(argumentsOf(source.messages().get(0)))
                .as("the upcoming season is named by its display name, parsed as MiniMessage")
                .contains(MiniMessage.miniMessage().deserialize("<gold>Autumn</gold>"));
    }

    @Test
    @DisplayName("'season info' parses the display name of the configured season as MiniMessage")
    void infoRendersDisplayNameAsMiniMessage() {
        Season gold = new Season(new SeasonId("s01"), "<gold>Season 1: Herbst</gold>", START,
                Duration.ofDays(28), "standard", List.of(), null);
        when(current.season()).thenReturn(Optional.of(gold));

        subject.info(source);

        assertThat(argumentsOf(source.messages().get(0)))
                .as("the configured name is passed as the parsed MiniMessage component, not as raw tags")
                .contains(MiniMessage.miniMessage().deserialize("<gold>Season 1: Herbst</gold>"));
    }

    @Test
    @DisplayName("'season status' while legacy runs says the next season starts at its start, not that it ends")
    void statusWithUpcomingSeasonSaysItStarts() {
        Instant start = Instant.parse("2027-02-01T00:00:00Z");
        when(seasons.status(NOW)).thenReturn(Optional.of(new SeasonStatus(SeasonId.LEGACY,
                SeasonPhase.ACTIVE, new SeasonId("s01_autumn"), start)));

        subject.status(source);

        assertThat(source.messages()).containsExactly(Messages.seasonStatusUpcoming(SeasonId.LEGACY.value(), "ACTIVE",
                "s01_autumn", Messages.seasonDate(start, source.locale())));
        assertThat(keysOf(source.messages().get(0)))
                .as("the upcoming wording is used, not the wording that says the season ends")
                .contains("skygridng.command.season.status.upcoming")
                .doesNotContain("skygridng.command.season.status");
    }

    /** Every translation key of the message tree, so the tests can tell which message was sent. */
    private static List<String> keysOf(Component message) {
        List<String> found = new ArrayList<>();
        if (message instanceof TranslatableComponent translatable) {
            found.add(translatable.key());
        }
        message.children().forEach(child -> found.addAll(keysOf(child)));
        return found;
    }

    /** Every argument of every translatable component in the message tree, so the tests assert on components. */
    private static List<Component> argumentsOf(Component message) {
        List<Component> found = new ArrayList<>();
        if (message instanceof TranslatableComponent translatable) {
            found.addAll(translatable.args());
        }
        message.children().forEach(child -> found.addAll(argumentsOf(child)));
        return found;
    }

    @Test
    @DisplayName("'season status' delegates to the use case and reports the active season")
    void statusDelegatesAndReports() {
        when(seasons.status(NOW)).thenReturn(Optional.of(new SeasonStatus(new SeasonId("s01"),
                SeasonPhase.ACTIVE, null, S01.end())));

        subject.status(source);

        verify(seasons).status(NOW);
        assertThat(source.messages()).hasSize(1);
    }

    @Test
    @DisplayName("'season status' says no season is configured while the server runs the legacy season")
    void statusWithoutSeasonsSaysNoSeason() {
        when(seasons.status(NOW)).thenReturn(Optional.of(new SeasonStatus(SeasonId.LEGACY,
                SeasonPhase.ACTIVE, null, null)));

        subject.status(source);

        assertThat(source.messages()).containsExactly(Messages.seasonNone());
    }

    @Test
    @DisplayName("'season status' reports a refusal when the status cannot be read")
    void statusReportsRefusalWhenUnreadable() {
        when(seasons.status(NOW)).thenReturn(Optional.empty());

        subject.status(source);

        assertThat(source.messages()).containsExactly(Messages.seasonRefused());
    }

    @Test
    @DisplayName("'season history' for the sender reads the sender's own tiers")
    void historyOfSelfReadsOwnTiers() {
        UUID self = source.selfId();
        when(progress.history(self)).thenReturn(Map.of(new SeasonId("s01"), 3));

        subject.history(source, null);

        verify(progress).history(self);
        assertThat(source.messages()).containsExactly(Messages.seasonHistoryEntry("s01", 3));
    }

    @Test
    @DisplayName("'season history <player>' is denied without skygridng.admin and reads nothing")
    void historyOfOtherPlayerDeniedWithoutAdmin() {
        source.withoutPermissions();

        subject.history(source, "Steve");

        verify(progress, never()).history(any());
        assertThat(source.messages()).containsExactly(Messages.noPermission());
    }

    @Test
    @DisplayName("'season history <player>' with skygridng.admin reads the named player")
    void historyOfOtherPlayerReadsWithAdmin() {
        UUID target = source.resolvePlayerId("Steve").orElseThrow();
        when(progress.history(target)).thenReturn(Map.of());

        subject.history(source, "Steve");

        verify(progress).history(target);
        assertThat(source.messages()).containsExactly(Messages.seasonHistoryEmpty());
    }

    @Test
    @DisplayName("'season retry' delegates to the use case and reports the outcome")
    void retryDelegates() {
        when(seasons.retry(NOW)).thenReturn(true);

        subject.retry(source);

        verify(seasons).retry(NOW);
        assertThat(source.messages()).containsExactly(Messages.seasonRetried());
    }

    @Test
    @DisplayName("'season advance' without confirm only warns and changes nothing")
    void advanceWithoutConfirmOnlyWarns() {
        subject.advance(source);

        verify(seasons, never()).advance(anyString(), any());
        verify(seasons, never()).advanceNow(anyString(), any());
        assertThat(source.messages()).containsExactly(Messages.seasonAdvanceWarn());
    }

    @Test
    @DisplayName("'season advance confirm' schedules the end and never starts the transition at once")
    void advanceConfirmSchedulesWithoutAdvancingNow() {
        when(seasons.advance(anyString(), any())).thenReturn(true);

        subject.advanceConfirm(source);

        verify(seasons).advance(anyString(), any());
        verify(seasons, never()).advanceNow(anyString(), any());
        assertThat(source.messages()).containsExactly(Messages.seasonAdvanced());
    }

    @Test
    @DisplayName("'season advance now' without confirm only warns and changes nothing")
    void advanceNowWithoutConfirmOnlyWarns() {
        subject.advanceNow(source);

        verify(seasons, never()).advanceNow(anyString(), any());
        assertThat(source.messages()).containsExactly(Messages.seasonAdvanceNowWarn());
    }

    @Test
    @DisplayName("'season advance now confirm' ends the season at once and passes the actor and instant")
    void advanceNowConfirmDelegates() {
        when(seasons.advanceNow(anyString(), any())).thenReturn(true);

        subject.advanceNowConfirm(source);

        verify(seasons).advanceNow(source.selfId().toString(), NOW);
        assertThat(source.messages()).containsExactly(Messages.seasonAdvancedNow());
    }

    @Test
    @DisplayName("'season postpone <duration>' without confirm only warns and changes nothing")
    void postponeWithoutConfirmOnlyWarns() {
        subject.postpone(source, "2h");

        verify(seasons, never()).postpone(any(), any());
        assertThat(source.messages()).containsExactly(Messages.seasonPostponeWarn("2h"));
    }

    @Test
    @DisplayName("'season postpone <duration> confirm' delegates the parsed duration")
    void postponeConfirmDelegatesParsedDuration() {
        when(seasons.postpone(NOW, Duration.ofHours(2))).thenReturn(true);

        subject.postponeConfirm(source, "2h");

        verify(seasons).postpone(NOW, Duration.ofHours(2));
        assertThat(source.messages()).containsExactly(Messages.seasonPostponed("2h"));
    }

    @Test
    @DisplayName("'season postpone' with an invalid duration is rejected before any change")
    void postponeInvalidDurationRejected() {
        subject.postponeConfirm(source, "soon");

        verify(seasons, never()).postpone(any(), any());
        assertThat(source.messages()).containsExactly(Messages.seasonDurationInvalid("soon"));
    }

    @Test
    @DisplayName("'season reload' without confirm only warns and does not reload")
    void reloadWithoutConfirmOnlyWarns() {
        subject.reload(source);

        verify(seasons, never()).reload();
        assertThat(source.messages()).containsExactly(Messages.seasonReloadWarn());
    }

    @Test
    @DisplayName("'season reload confirm' reloads the season definitions")
    void reloadConfirmDelegates() {
        when(seasons.reload()).thenReturn(true);

        subject.reloadConfirm(source);

        verify(seasons).reload();
        assertThat(source.messages()).containsExactly(Messages.seasonReloaded());
    }
}
