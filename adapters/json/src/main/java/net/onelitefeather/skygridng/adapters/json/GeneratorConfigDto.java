package net.onelitefeather.skygridng.adapters.json;

import net.onelitefeather.skygridng.api.model.GenerationSettings;
import net.onelitefeather.skygridng.api.model.SkyGridConfig;
import net.onelitefeather.skygridng.api.model.TileType;

import java.util.List;

/**
 * Transfer object for JSON serialization (adapter internal).
 *
 * Separates the file format from the domain. {@code wfc.allowed} is a list of [a,b] pairs that is
 * translated into AllowedPair on mapping. {@link TileType}, {@link GenerationSettings} and
 * {@link SkyGridConfig} are records with matching field names and bind directly (Gson 2.10+
 * supports records).
 */
final class GeneratorConfigDto {
    String algorithm;
    WfcDto wfc;
    SkyGridConfig skygrid;
}

final class WfcDto {
    GenerationSettings settings;
    List<TileType> tiles;
    List<List<String>> allowed;
}
