package net.onelitefeather.skygridng.generator;

import net.kyori.adventure.text.TranslatableComponent;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the public API facade. It must read through to the admin use case without
 * exposing any mutation, so the same snapshot the admin owns is what callers observe.
 */
class SkyGridApiServiceTest {

    private RuleAdminUseCase admin;
    private SkyGridApi api;

    @BeforeEach
    void setUp() {
        PresetCatalog catalog = Fixtures.presetCatalog("skygrid");
        Fixtures.InMemoryPresetRepository repository = new Fixtures.InMemoryPresetRepository(catalog);
        RuleState state = new RuleState(PresetResolver.resolve(catalog, catalog.active()), 8);
        PresetService presets = new PresetService(repository, state, catalog);
        DefaultTerrainAlgorithms algorithms = new DefaultTerrainAlgorithms(state, Fixtures.defaultAlgorithmFactories());
        admin = new RuleAdminService(state, presets, algorithms);
        api = new SkyGridApiService(admin);
    }

    @Test
    @DisplayName("the facade reads through to the admin use case")
    void readsThroughToAdmin() {
        assertThat(api.algorithm()).isEqualTo(admin.algorithm());
        assertThat(api.configuration()).isEqualTo(admin.current());
    }

    @Test
    @DisplayName("the api version is exposed")
    void exposesApiVersion() {
        assertThat(api.apiVersion()).isEqualTo(SkyGridApi.API_VERSION);
        assertThat(api.apiVersion()).isEqualTo("2.0.0");
    }

    @Test
    @DisplayName("describe() builds a translatable Adventure summary of algorithm, spacing and biome count")
    void describeIsATranslatableSummary() {
        assertThat(api.describe()).isInstanceOfSatisfying(TranslatableComponent.class, summary -> {
            assertThat(summary.key()).isEqualTo("skygridng.api.summary");
            assertThat(summary.arguments()).hasSize(3); // algorithm, spacing, biome count
        });
    }
}
