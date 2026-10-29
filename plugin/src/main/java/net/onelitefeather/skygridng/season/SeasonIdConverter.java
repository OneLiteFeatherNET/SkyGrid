package net.onelitefeather.skygridng.season;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps a {@link SeasonId} to its {@code TEXT} column value and back. Shared by every season scoped
 * entity, so the value is written the same way everywhere. {@code autoApply} makes every {@link SeasonId}
 * attribute use it without a per-field annotation. The persistence unit must list this class in its
 * {@code persistence.xml} entry, because the unit excludes unlisted classes.
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
