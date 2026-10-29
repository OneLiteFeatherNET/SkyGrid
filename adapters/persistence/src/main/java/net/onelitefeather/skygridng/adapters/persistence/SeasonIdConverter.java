package net.onelitefeather.skygridng.adapters.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import net.onelitefeather.skygridng.api.model.SeasonId;

/**
 * Maps a {@link SeasonId} to its {@code TEXT} column value and back. Shared by every season scoped
 * persistence unit ({@code season}, {@code challenge-progress}, {@code task-progress}), so the value
 * is written the same way everywhere. {@code autoApply} makes every {@link SeasonId} attribute use it
 * without a per-field annotation. Each persistence unit must list this class in its {@code
 * persistence.xml} entry, because the units exclude unlisted classes.
 */
@Converter(autoApply = true)
public class SeasonIdConverter implements AttributeConverter<SeasonId, String> {

    @Override
    public String convertToDatabaseColumn(SeasonId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SeasonId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new SeasonId(dbData);
    }
}
