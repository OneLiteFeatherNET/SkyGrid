package net.onelitefeather.skygridng.adapter.out.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.onelitefeather.skygridng.core.model.AllowedPair;
import net.onelitefeather.skygridng.core.model.GeneratorConfig;
import net.onelitefeather.skygridng.core.model.RuleSet;
import net.onelitefeather.skygridng.core.port.out.RuleRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Out adapter: loads and saves the generator configuration as JSON (Gson).
 *
 * If the file is missing it is created from the bundled default JSON. Mapping between the file
 * format ({@link GeneratorConfigDto}) and the domain ({@link GeneratorConfig}) happens here so the
 * core stays independent of the format.
 */
public final class JsonRuleRepository implements RuleRepository {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path file;
    private final String defaultJson;

    public JsonRuleRepository(Path file, String defaultJson) {
        this.file = file;
        this.defaultJson = defaultJson;
    }

    @Override
    public GeneratorConfig load() {
        try {
            if (Files.notExists(file)) {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.writeString(file, defaultJson, StandardCharsets.UTF_8);
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            GeneratorConfigDto dto = GSON.fromJson(json, GeneratorConfigDto.class);
            return toDomain(dto);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load configuration: " + file, e);
        } catch (JsonParseException e) {
            throw new IllegalStateException("Invalid generator JSON: " + file + " (" + e.getMessage() + ")", e);
        }
    }

    @Override
    public void save(GeneratorConfig config) {
        try {
            GeneratorConfigDto dto = toDto(config);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(dto), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save configuration: " + file, e);
        }
    }

    private GeneratorConfig toDomain(GeneratorConfigDto dto) {
        if (dto == null || dto.algorithm == null || dto.wfc == null || dto.skygrid == null) {
            throw new IllegalStateException("Generator JSON incomplete (algorithm/skygrid/skygrid required)");
        }
        if (dto.wfc.settings == null || dto.wfc.tiles == null) {
            throw new IllegalStateException("wfc section incomplete (settings/tiles required)");
        }
        Set<AllowedPair> pairs = new LinkedHashSet<>();
        if (dto.wfc.allowed != null) {
            for (List<String> pair : dto.wfc.allowed) {
                if (pair.size() != 2) {
                    throw new IllegalStateException("allowed entry needs exactly two ids: " + pair);
                }
                pairs.add(new AllowedPair(pair.get(0), pair.get(1)));
            }
        }
        RuleSet wfc = new RuleSet(dto.wfc.tiles, pairs, dto.wfc.settings);
        return new GeneratorConfig(dto.algorithm, wfc, dto.skygrid);
    }

    private GeneratorConfigDto toDto(GeneratorConfig config) {
        GeneratorConfigDto dto = new GeneratorConfigDto();
        dto.algorithm = config.algorithm();
        dto.skygrid = config.skygrid();
        WfcDto wfc = new WfcDto();
        wfc.settings = config.wfc().settings();
        wfc.tiles = config.wfc().tiles();
        wfc.allowed = config.wfc().allowed().stream()
                .map(p -> List.of(p.a(), p.b()))
                .collect(Collectors.toList());
        dto.wfc = wfc;
        return dto;
    }
}
