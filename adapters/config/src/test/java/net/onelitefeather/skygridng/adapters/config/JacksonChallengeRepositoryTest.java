package net.onelitefeather.skygridng.adapters.config;

import net.onelitefeather.skygridng.api.model.ChallengeCatalog;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.ports.out.ChallengeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the Jackson challenge adapter against the {@link ChallengeRepository}
 * contract: it seeds the bundled default {@code challenges/} directory (one file per challenge)
 * and round-trips a saved catalog, mirroring {@link JacksonRuleRepositoryTest}'s structure and
 * conventions for its own {@code rules/} directory.
 */
class JacksonChallengeRepositoryTest {

    private static final String ID = "no_crafting_table";

    private BundledDefaults bundled() {
        return path -> getClass().getResourceAsStream("/" + path);
    }

    private ChallengeRepository repository(Path dir) {
        return new JacksonChallengeRepository(dir, bundled());
    }

    @Test
    @DisplayName("a missing challenges directory is seeded with the bundled no_crafting_table default")
    void seedsBundledDefault(@TempDir Path dir) {
        ChallengeCatalog catalog = repository(dir).load();

        assertThat(Files.exists(dir.resolve("challenges/no_crafting_table.yml"))).isTrue();

        ChallengeRule rule = catalog.forId(ID).orElseThrow();
        assertThat(rule.id()).isEqualTo(ID);
        assertThat(rule.active()).isFalse();
        assertThat(rule.description()).isNotBlank();
    }

    @Test
    @DisplayName("an empty (but existing) challenges directory is also seeded")
    void seedsWhenDirectoryExistsButEmpty(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("challenges"));

        ChallengeCatalog catalog = repository(dir).load();

        assertThat(catalog.forId(ID)).isPresent();
    }

    @Test
    @DisplayName("a saved catalog is read back unchanged")
    void roundTrip(@TempDir Path dir) {
        ChallengeRepository repository = repository(dir);
        ChallengeCatalog original = repository.load();
        ChallengeCatalog toggled = original.with(original.forId(ID).orElseThrow().withActive(true));

        repository.save(toggled);

        ChallengeCatalog reloaded = repository(dir).load();
        assertThat(reloaded).isEqualTo(toggled);
        assertThat(reloaded.forId(ID)).get().extracting(ChallengeRule::active).isEqualTo(true);
    }

    private void writeChallenge(Path dir, String fileName, String content) throws IOException {
        Path challenges = dir.resolve("challenges");
        Files.createDirectories(challenges);
        Files.write(challenges.resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a challenge round trips through JSON")
    void roundTripJson(@TempDir Path dir) throws IOException {
        writeChallenge(dir, "no_crafting_table.json", """
                {
                  "id": "no_crafting_table",
                  "description": "Crafting tables cannot be opened while this challenge is active.",
                  "active": true
                }
                """);

        ChallengeRepository repository = repository(dir);
        ChallengeCatalog loaded = repository.load();
        assertThat(loaded.forId(ID)).get().extracting(ChallengeRule::active).isEqualTo(true);

        repository.save(loaded);
        assertThat(Files.exists(dir.resolve("challenges/no_crafting_table.json"))).isTrue();

        ChallengeCatalog reloaded = repository(dir).load();
        assertThat(reloaded).isEqualTo(loaded);
    }

    @Test
    @DisplayName("a challenge round trips through YAML")
    void roundTripYaml(@TempDir Path dir) throws IOException {
        writeChallenge(dir, "no_crafting_table.yml", """
                id: no_crafting_table
                description: Crafting tables cannot be opened while this challenge is active.
                active: true
                """);

        ChallengeRepository repository = repository(dir);
        ChallengeCatalog loaded = repository.load();
        assertThat(loaded.forId(ID)).get().extracting(ChallengeRule::active).isEqualTo(true);

        repository.save(loaded);
        assertThat(Files.exists(dir.resolve("challenges/no_crafting_table.yml"))).isTrue();

        ChallengeCatalog reloaded = repository(dir).load();
        assertThat(reloaded).isEqualTo(loaded);
    }

    @Test
    @DisplayName("a challenge round trips through TOML")
    void roundTripToml(@TempDir Path dir) throws IOException {
        writeChallenge(dir, "no_crafting_table.toml", """
                id = "no_crafting_table"
                description = "Crafting tables cannot be opened while this challenge is active."
                active = true
                """);

        ChallengeRepository repository = repository(dir);
        ChallengeCatalog loaded = repository.load();
        assertThat(loaded.forId(ID)).get().extracting(ChallengeRule::active).isEqualTo(true);

        repository.save(loaded);
        assertThat(Files.exists(dir.resolve("challenges/no_crafting_table.toml"))).isTrue();

        ChallengeCatalog reloaded = repository(dir).load();
        assertThat(reloaded).isEqualTo(loaded);
    }
}
