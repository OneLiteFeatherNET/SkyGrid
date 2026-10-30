package net.onelitefeather.skygridng.season;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import net.onelitefeather.skygridng.generator.SpawnPoints;
import net.onelitefeather.skygridng.generator.WorldGenerators;
import net.onelitefeather.skygridng.challenge.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.generator.Presets;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Set;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Checks the listener multibinding of {@link SeasonModule} without building the whole graph: every collaborator of
 * the season service is a Mockito double, and the listeners come from a second module, as a prestige or economy
 * change would bind them.
 */
class SeasonModuleTest {

    @Test
    void theServiceReceivesEveryListenerBoundByAnotherModuleWithoutEditingTheModule() {
        SeasonTransitionListener prestige = (from, to) -> {
        };
        SeasonTransitionListener economy = (from, to) -> {
        };
        Injector injector = Guice.createInjector(new SeasonModule(), doubles(), new AbstractModule() {
            @Override
            protected void configure() {
                Multibinder<SeasonTransitionListener> listeners =
                        Multibinder.newSetBinder(binder(), SeasonTransitionListener.class);
                listeners.addBinding().toInstance(prestige);
                listeners.addBinding().toInstance(economy);
            }
        });

        Set<SeasonTransitionListener> bound = injector.getInstance(
                Key.get(new TypeLiteral<Set<SeasonTransitionListener>>() {}));

        assertThat(bound)
                .as("the season listener set holds every listener bound by any module")
                .containsExactlyInAnyOrder(prestige, economy);
    }

    /** Doubles for every collaborator the season providers need from outside the module; none is called here. */
    private static AbstractModule doubles() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(org.bukkit.plugin.java.JavaPlugin.class).toInstance(mock(org.bukkit.plugin.java.JavaPlugin.class));
                bind(SeasonRepository.class).toInstance(mock(SeasonRepository.class));
                bind(SeasonRunRepository.class).toInstance(mock(SeasonRunRepository.class));
                bind(ChallengeAdminUseCase.class).toInstance(mock(ChallengeAdminUseCase.class));
                bind(Presets.class).toInstance(mock(Presets.class));
                bind(WorldGenerators.class).toInstance(mock(WorldGenerators.class));
                bind(SpawnPoints.class).toInstance(mock(SpawnPoints.class));
                bind(LongSupplier.class).toInstance(mock(LongSupplier.class));
                bind(Clock.class).toInstance(Clock.systemUTC());
            }
        };
    }
}
