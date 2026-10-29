package net.onelitefeather.skygridng.adapters.persistence;

import net.onelitefeather.skygridng.api.model.SeasonId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for {@link SeasonIdConverter}: the {@link SeasonId} to {@code TEXT} mapping shared by
 * every season scoped persistence unit. No database is needed, the converter is a pure function.
 */
class SeasonIdConverterTest {

    private final SeasonIdConverter converter = new SeasonIdConverter();

    @Test
    @DisplayName("a season id is stored as its plain text value")
    void seasonIdIsStoredAsText() {
        assertThat(converter.convertToDatabaseColumn(new SeasonId("s01_overgrowth")))
                .isEqualTo("s01_overgrowth");
    }

    @Test
    @DisplayName("the legacy season is stored as the text legacy")
    void legacyIsStoredAsLegacyText() {
        assertThat(converter.convertToDatabaseColumn(SeasonId.LEGACY)).isEqualTo("legacy");
    }

    @Test
    @DisplayName("the text legacy is read back as the legacy season")
    void legacyTextIsReadAsLegacy() {
        assertThat(converter.convertToEntityAttribute("legacy")).isEqualTo(SeasonId.LEGACY);
    }

    @Test
    @DisplayName("a season id survives a round trip through the text column unchanged")
    void roundTripKeepsTheValueEqual() {
        var seasonId = new SeasonId("s02_frost");

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(seasonId)))
                .isEqualTo(seasonId);
    }

    @Test
    @DisplayName("a null season id stays null in both directions")
    void nullStaysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
