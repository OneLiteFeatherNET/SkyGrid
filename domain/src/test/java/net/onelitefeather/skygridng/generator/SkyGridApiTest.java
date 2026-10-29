package net.onelitefeather.skygridng.generator;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** White-box test for the read only season facade on {@link SkyGridApi}. */
class SkyGridApiTest {

    private static final SkyGridApi API = new SkyGridApi() {
        @Override
        public String algorithm() {
            return "skygrid";
        }

        @Override
        public GeneratorConfig configuration() {
            return null;
        }

        @Override
        public Component describe() {
            return Component.empty();
        }
    };

    @Test
    @DisplayName("currentSeason is empty when no season is configured")
    void currentSeasonIsEmptyWithoutSeasons() {
        assertThat(API.currentSeason()).isEqualTo(Optional.empty());
    }
}
