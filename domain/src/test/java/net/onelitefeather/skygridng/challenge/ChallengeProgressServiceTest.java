package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Black-box tests for {@link ChallengeProgressService} against a Mockito-mocked
 * {@link ChallengeProgressRepository} and a fixed {@link CurrentSeason}. The service is a delegate that
 * resolves the current season per call, so each test proves the season it passes down.
 */
class ChallengeProgressServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String CHALLENGE_ID = "one_life";
    private static final SeasonId SEASON = new SeasonId("s01");

    private ChallengeProgressRepository repository;
    private ChallengeProgressUseCase progress;

    @BeforeEach
    void setUp() {
        repository = mock(ChallengeProgressRepository.class);
        progress = new ChallengeProgressService(repository, fixedSeason(SEASON));
    }

    private static CurrentSeason fixedSeason(SeasonId id) {
        return new CurrentSeason() {
            @Override
            public SeasonId id() {
                return id;
            }

            @Override
            public Optional<Season> season() {
                return Optional.empty();
            }
        };
    }

    @Test
    @DisplayName("tierOf reads the tier of the current season and returns the repository's value")
    void tierOfUsesCurrentSeason() {
        when(repository.tierOf(SEASON, PLAYER_ID, CHALLENGE_ID)).thenReturn(3);

        int result = progress.tierOf(PLAYER_ID, CHALLENGE_ID);

        assertThat(result).isEqualTo(3);
        verify(repository).tierOf(SEASON, PLAYER_ID, CHALLENGE_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("recordCompletion writes into the current season and returns the repository's value")
    void recordCompletionUsesCurrentSeason() {
        when(repository.recordCompletion(SEASON, PLAYER_ID, CHALLENGE_ID)).thenReturn(4);

        int result = progress.recordCompletion(PLAYER_ID, CHALLENGE_ID);

        assertThat(result).isEqualTo(4);
        verify(repository).recordCompletion(SEASON, PLAYER_ID, CHALLENGE_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("resetCurrentSeason clears only the current season")
    void resetCurrentSeasonResetsCurrentSeason() {
        progress.resetCurrentSeason();

        verify(repository).resetSeason(SEASON);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("without a configured season, progress is written to the legacy season")
    void legacySeasonIsUsedWhenNoSeasonIsConfigured() {
        progress = new ChallengeProgressService(repository, fixedSeason(SeasonId.LEGACY));

        progress.recordCompletion(PLAYER_ID, CHALLENGE_ID);

        verify(repository).recordCompletion(SeasonId.LEGACY, PLAYER_ID, CHALLENGE_ID);
    }
}
