package net.onelitefeather.skygridng.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Shared config-format helpers for the Jackson adapters. Config files are YAML: {@code .yml} and
 * {@code .yaml} are read and written. JSON and TOML are no longer supported; a file in one of those
 * formats is skipped with a warning. All mappers are configured the same way: "inherit" ({@code null})
 * fields are omitted on write, a scalar is accepted where a list is expected ({@code extends: classic}
 * as well as {@code extends: [classic]}), and unknown keys are ignored so a newer config does not break
 * an older build.
 */
final class ConfigFormats {

    static final String DEFAULT_EXTENSION = "yml";
    private static final List<String> SUPPORTED = List.of("yml", "yaml");
    private static final List<String> UNSUPPORTED = List.of("json", "toml");
    private static final Logger LOG = Logger.getLogger(ConfigFormats.class.getName());

    private ConfigFormats() {
    }

    /** The YAML mapper every config file is read and written with. */
    static YAMLMapper mapper() {
        return YAMLMapper.builder()
                .defaultPropertyInclusion(JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.USE_DEFAULTS))
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /** True if the file has one of the supported config extensions ({@code .yml} or {@code .yaml}). */
    static boolean isSupported(Path file) {
        return SUPPORTED.contains(extension(file));
    }

    /**
     * Logs one warning per JSON or TOML file directly in {@code dir}. Such files are skipped by the
     * loaders, so the admin is told why a file has no effect. A missing directory has nothing to warn about.
     */
    static void warnAboutUnsupportedFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> UNSUPPORTED.contains(extension(file)))
                    .sorted()
                    .forEach(file -> LOG.warning("Ignoring " + file.getFileName() + ": the "
                            + extension(file).toUpperCase(Locale.ROOT)
                            + " config format is no longer supported, convert the file to YAML (.yml)"));
        }
    }

    /** The file name without its extension (the preset or block base name). */
    static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** The lower-cased extension without the dot, or an empty string when there is none. */
    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
