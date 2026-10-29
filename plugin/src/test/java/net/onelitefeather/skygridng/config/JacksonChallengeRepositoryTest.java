package net.onelitefeather.skygridng.config;

import net.onelitefeather.skygridng.challenge.ChallengeCatalog;
import net.onelitefeather.skygridng.challenge.ChallengeRule;
import net.onelitefeather.skygridng.challenge.ItemId;
import net.onelitefeather.skygridng.challenge.Reward;
import net.onelitefeather.skygridng.challenge.Task;
import net.onelitefeather.skygridng.challenge.ChallengeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("a challenge with no 'tasks' key loads with an empty, non-null tasks list")
    void absentTasksLoadAsEmptyList(@TempDir Path dir) throws IOException {
        writeChallenge(dir, "level_is_border.yml", """
                id: level_is_border
                description: Periodically reminds players that their experience level sets their allowed travel radius.
                active: false
                """);

        ChallengeRule rule = repository(dir).load().forId("level_is_border").orElseThrow();

        assertThat(rule.tasks()).isNotNull().isEmpty();
        assertThat(rule.sequential()).isFalse();
    }

    @Test
    @DisplayName("a challenge with sequential tasks round trips")
    void sequentialTasksRoundTrip(@TempDir Path dir) throws IOException {
        writeChallenge(dir, "quest_challenge.yml", """
                id: quest_challenge
                description: A challenge with an ordered quest.
                active: true
                sequential: true
                tasks:
                  - id: first_step
                    description: Do the first thing.
                  - id: second_step
                    description: Do the second thing.
                """);

        ChallengeRepository repository = repository(dir);
        ChallengeRule loaded = repository.load().forId("quest_challenge").orElseThrow();

        assertThat(loaded.sequential()).isTrue();
        assertThat(loaded.tasks()).extracting(Task::id).containsExactly("first_step", "second_step");
        assertThat(loaded.tasks()).extracting(Task::reward).containsOnlyNulls();

        repository.save(repository.load());
        ChallengeRule reloaded = repository(dir).load().forId("quest_challenge").orElseThrow();
        assertThat(reloaded).isEqualTo(loaded);
    }

    @Test
    @DisplayName("all three reward shapes round trip to the matching Reward variant")
    void rewardShapesRoundTrip(@TempDir Path dir) throws IOException {
        writeChallenge(dir, "reward_challenge.yml", """
                id: reward_challenge
                description: A challenge whose tasks grant every reward kind.
                active: true
                tasks:
                  - id: item_task
                    description: Grants an item.
                    reward:
                      type: item
                      itemId: minecraft:diamond
                      quantity: 3
                  - id: command_task
                    description: Grants a command.
                    reward:
                      type: command
                      command: "give {player} minecraft:emerald 1"
                  - id: message_task
                    description: Grants a message.
                    reward:
                      type: message
                      message: "<gold>Well done!"
                """);

        ChallengeRepository repository = repository(dir);
        ChallengeRule loaded = repository.load().forId("reward_challenge").orElseThrow();

        List<Task> tasks = loaded.tasks();
        assertThat(tasks).hasSize(3);

        assertThat(tasks.get(0).reward()).isEqualTo(new Reward.ItemGrant(ItemId.of("minecraft:diamond"), 3));
        assertThat(tasks.get(1).reward())
                .isEqualTo(new Reward.CommandDispatch("give {player} minecraft:emerald 1"));
        assertThat(tasks.get(2).reward()).isEqualTo(new Reward.PlayerMessage("<gold>Well done!"));

        repository.save(repository.load());
        ChallengeRule reloaded = repository(dir).load().forId("reward_challenge").orElseThrow();
        assertThat(reloaded).isEqualTo(loaded);
    }

    @Test
    @DisplayName("a task with an unrecognized reward type fails fast, naming the file and the bad type")
    void unrecognizedRewardTypeFailsFast(@TempDir Path dir) throws IOException {
        writeChallenge(dir, "broken_challenge.yml", """
                id: broken_challenge
                description: A challenge with a bogus reward type.
                active: true
                tasks:
                  - id: broken_task
                    description: A task with a bogus reward.
                    reward:
                      type: teleport
                """);

        assertThatThrownBy(() -> repository(dir).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broken_challenge.yml")
                .hasMessageContaining("teleport");
    }
}
