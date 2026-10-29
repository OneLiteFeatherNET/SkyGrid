package net.onelitefeather.skygridng.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Shared config-format helpers for the Jackson adapters. The on-disk format is chosen per file from its
 * extension, so JSON, YAML and TOML round-trip through the same DTOs. All mappers are configured the
 * same way: "inherit" ({@code null}) fields are omitted on write, a scalar is accepted where a list is
 * expected ({@code extends: classic} as well as {@code extends: [classic]}), and unknown keys are
 * ignored so a newer config does not break an older build.
 */
final class ConfigFormats {

    static final String DEFAULT_EXTENSION = "yml";
    private static final List<String> SUPPORTED = List.of("json", "yml", "yaml", "toml");

    private ConfigFormats() {
    }

    /** A mapper for the file's extension: {@code .json} JSON, {@code .toml} TOML, else YAML. */
    static ObjectMapper mapperFor(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".json")) {
            return configure(JsonMapper.builder());
        }
        if (name.endsWith(".toml")) {
            return configure(TomlMapper.builder());
        }
        return configure(YAMLMapper.builder());
    }

    /** True if the file has one of the supported config extensions. */
    static boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED) {
            if (name.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    /** The file name without its extension (the preset or block base name). */
    static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static <M extends ObjectMapper, B extends MapperBuilder<M, B>> M configure(B builder) {
        return builder
                .defaultPropertyInclusion(JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.USE_DEFAULTS))
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
