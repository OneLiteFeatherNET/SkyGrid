package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.ports.in.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Black-box tests for {@link ChallengeProgressService} against a Mockito-mocked
 * {@link ChallengeProgressRepository}. The service is a thin delegate with no caching and no
 * transformation, so each test only proves the pass-through: same arguments in, the repository's
 * return value out, exactly one call.
 */
class ChallengeProgressServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String CHALLENGE_ID = "one_life";

    private ChallengeProgressRepository repository;
    private ChallengeProgressUseCase progress;

    @BeforeEach
    void setUp() {
        repository = mock(ChallengeProgressRepository.class);
        progress = new ChallengeProgressService(repository);
    }

    @Test
    @DisplayName("tierOf delegates exactly once with the same arguments and returns the repository's value")
    void tierOfDelegates() {
        when(repository.tierOf(PLAYER_ID, CHALLENGE_ID)).thenReturn(3);

        int result = progress.tierOf(PLAYER_ID, CHALLENGE_ID);

        assertThat(result).isEqualTo(3);
        verify(repository).tierOf(PLAYER_ID, CHALLENGE_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("recordCompletion delegates exactly once with the same arguments and returns the repository's value")
    void recordCompletionDelegates() {
        when(repository.recordCompletion(PLAYER_ID, CHALLENGE_ID)).thenReturn(4);

        int result = progress.recordCompletion(PLAYER_ID, CHALLENGE_ID);

        assertThat(result).isEqualTo(4);
        verify(repository).recordCompletion(PLAYER_ID, CHALLENGE_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("resetAll delegates exactly once")
    void resetAllDelegates() {
        progress.resetAll();

        verify(repository).resetAll();
        verifyNoMoreInteractions(repository);
    }
}
