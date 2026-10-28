package net.onelitefeather.skygridng.platforms.paper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the merged service files of the shaded plugin jar, the artifact the server actually loads.
 * Shadow excludes duplicate entries by default, so unless the duplicates strategy lets them reach
 * {@code mergeServiceFiles()}, only the first jar's service file survives and every provider it
 * did not list is silently lost. The jar path comes from the {@code skygrid.shadowJar} system
 * property, which the {@code test} task sets to the output of {@code shadowJar}.
 */
class ShadowJarServiceFilesTest {

    private static final String FLYWAY_PLUGINS = "META-INF/services/org.flywaydb.core.extensibility.Plugin";
    private static final String RELOCATED_JSON_FACTORY =
            "META-INF/services/net.onelitefeather.skygridng.libs.jackson.core.JsonFactory";

    @Test
    void flywayPluginServiceFileListsTheCoreAndPostgresqlPlugins() throws IOException {
        var entries = serviceEntries(FLYWAY_PLUGINS);

        assertThat(entries)
                .as("flyway-core must register its migration resource types (the V and R prefixes)")
                .contains("org.flywaydb.core.internal.resource.CoreResourceTypeProvider");
        assertThat(entries)
                .as("flyway-database-postgresql must register its database type")
                .contains("org.flywaydb.database.postgresql.PostgreSQLDatabaseType");
    }

    @Test
    void duplicatedJacksonJsonFactoryServiceFileKeepsEveryProvider() throws IOException {
        var entries = serviceEntries(RELOCATED_JSON_FACTORY);

        assertThat(entries)
                .as("jackson-core and jackson-dataformat-yaml both declare a JsonFactory provider")
                .contains(
                        "net.onelitefeather.skygridng.libs.jackson.core.JsonFactory",
                        "net.onelitefeather.skygridng.libs.jackson.dataformat.yaml.YAMLFactory");
    }

    private static List<String> serviceEntries(String entryName) throws IOException {
        var jarPath = System.getProperty("skygrid.shadowJar");
        assertThat(jarPath).as("the test task must pass the shaded jar path").isNotNull();
        try (var jar = new JarFile(jarPath)) {
            var entry = jar.getEntry(entryName);
            assertThat(entry).as("%s is missing from the shaded jar", entryName).isNotNull();
            try (var in = jar.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .toList();
            }
        }
    }
}
