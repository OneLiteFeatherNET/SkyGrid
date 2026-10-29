package net.onelitefeather.skygridng;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import net.onelitefeather.skygridng.generator.HexagonalChunkGenerator;
import net.onelitefeather.skygridng.generator.RuleAdminUseCase;
import net.onelitefeather.skygridng.generator.DefaultTerrainAlgorithms;
import net.onelitefeather.skygridng.generator.RuleAdminService;
import net.onelitefeather.skygridng.generator.RuleState;
import net.onelitefeather.skygridng.challenge.ChallengeModule;
import net.onelitefeather.skygridng.season.SeasonModule;
import net.onelitefeather.skygridng.generator.GeneratorModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box test for the composition modules. It builds the object graph from the three modules
 * using a real (MockBukkit) plugin as the platform and asserts that every in port resolves to its
 * service implementation and that shared state is a singleton.
 */
class GuiceModulesTest {

    private SkyGridNgPlugin plugin;
    private Injector injector;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(SkyGridNgPlugin.class);
        injector = Guice.createInjector(
                Stage.PRODUCTION,
                new PlatformModule(plugin),
                new RepositoryModule(),
                new GeneratorModule(),
                new ChallengeModule(),
                new SeasonModule());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("each in port is bound to its service implementation")
    void portsResolveToServices() {
        assertThat(injector.getInstance(DefaultTerrainAlgorithms.class)).isNotNull();
        assertThat(injector.getInstance(RuleAdminUseCase.class)).isInstanceOf(RuleAdminService.class);
    }

    @Test
    @DisplayName("the chunk generator can be constructed from the graph")
    void generatorResolves() {
        assertThat(injector.getInstance(HexagonalChunkGenerator.class)).isNotNull();
    }

    @Test
    @DisplayName("the shared rule state is a singleton across the graph")
    void ruleStateIsSingleton() {
        assertThat(injector.getInstance(RuleState.class))
                .isSameAs(injector.getInstance(RuleState.class));
    }
}
