package net.onelitefeather.skygridng.platforms.olf.season;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the season message keys exist in both bundles, so no German player sees a raw key and no English key
 * is forgotten in the German bundle. Reads the bundles from the classpath only.
 */
class SeasonMessageBundleTest {

    private static final String PREFIX = "skygridng.command.season.";

    @Test
    void everySeasonKeyInTheEnglishBundleExistsInTheGermanBundle() throws IOException {
        Set<String> english = seasonKeys("messages/Bundle_en.properties");
        Set<String> german = seasonKeys("messages/Bundle_de.properties");

        assertThat(german)
                .as("every season key of Bundle_en is translated in Bundle_de")
                .containsAll(english);
    }

    @Test
    void everySeasonKeyInTheGermanBundleExistsInTheEnglishBundle() throws IOException {
        Set<String> english = seasonKeys("messages/Bundle_en.properties");
        Set<String> german = seasonKeys("messages/Bundle_de.properties");

        assertThat(english)
                .as("Bundle_de has no season key that Bundle_en lacks")
                .containsAll(german);
    }

    @Test
    void theGermanBundleHasNoEmptyValues() throws IOException {
        Properties german = load("messages/Bundle_de.properties");

        assertThat(german.stringPropertyNames().stream()
                .filter(key -> key.startsWith(PREFIX))
                .filter(key -> german.getProperty(key).isBlank())
                .collect(Collectors.toSet()))
                .as("no season key in Bundle_de is blank")
                .isEmpty();
    }

    private static Set<String> seasonKeys(String resource) throws IOException {
        return load(resource).stringPropertyNames().stream()
                .filter(key -> key.startsWith(PREFIX))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = SeasonMessageBundleTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("the bundle %s is on the classpath", resource).isNotNull();
            properties.load(in);
        }
        return properties;
    }
}
