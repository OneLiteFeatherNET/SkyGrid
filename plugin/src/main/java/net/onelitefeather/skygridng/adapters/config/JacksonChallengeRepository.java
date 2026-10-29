package net.onelitefeather.skygridng.adapters.config;

import net.onelitefeather.skygridng.adapters.config.ChallengeDtos.ChallengeDto;
import net.onelitefeather.skygridng.adapters.config.ChallengeDtos.RewardDto;
import net.onelitefeather.skygridng.adapters.config.ChallengeDtos.TaskDto;
import net.onelitefeather.skygridng.api.model.ChallengeCatalog;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.ItemId;
import net.onelitefeather.skygridng.api.model.Reward;
import net.onelitefeather.skygridng.api.model.Task;
import net.onelitefeather.skygridng.ports.out.ChallengeRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Out adapter: loads and saves the challenge catalog with Jackson, one file per challenge under the
 * {@code challenges/} directory ({@code challenges/no_crafting_table.yml}), sibling to {@code presets/}
 * and {@code rules/}. The id inside the file is authoritative; the file name is only there for humans.
 * The format is chosen per file from its extension (JSON, YAML or TOML). A fresh {@code challenges/}
 * directory is seeded from the bundled defaults. A challenge is a player/session facing switch, a
 * different axis from the per-block {@link net.onelitefeather.skygridng.generator.RuleCatalog}, so
 * the api/core never see Jackson here either.
 */
public final class JacksonChallengeRepository implements ChallengeRepository {

    private static final String CHALLENGES_DIR = "challenges";
    private static final List<String> DEFAULT_CHALLENGE_RESOURCES = List.of(
            "challenges/no_crafting_table.yml",
            "challenges/one_life.yml",
            "challenges/no_natural_regeneration.yml",
            "challenges/level_is_border.yml");

    private final Path dataFolder;
    private final BundledDefaults defaults;
    private final ConfigSchemas schemas = ConfigSchemas.shared();

    public JacksonChallengeRepository(Path dataFolder, BundledDefaults defaults) {
        this.dataFolder = dataFolder;
        this.defaults = defaults;
    }

    @Override
    public ChallengeCatalog load() {
        try {
            seedDefaultsIfAbsent();
            schemas.writeTo(dataFolder);

            Map<String, ChallengeRule> challenges = new LinkedHashMap<>();
            for (Path file : challengeFiles()) {
                ChallengeDto dto = ConfigFormats.mapperFor(file)
                        .readValue(Files.readAllBytes(file), ChallengeDto.class);
                ChallengeRule rule = toRule(dto, file);
                challenges.put(rule.id(), rule);
            }
            return new ChallengeCatalog(challenges);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not load challenges in " + dataFolder.resolve(CHALLENGES_DIR), e);
        }
    }

    @Override
    public void save(ChallengeCatalog catalog) {
        try {
            Path challengesDir = dataFolder.resolve(CHALLENGES_DIR);
            Files.createDirectories(challengesDir);
            for (ChallengeRule rule : catalog.challenges().values()) {
                Path file = challengeFileFor(challengesDir, rule.id());
                byte[] bytes = ConfigFormats.mapperFor(file).writeValueAsBytes(toDto(rule));
                Files.write(file, bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not save challenges in " + dataFolder.resolve(CHALLENGES_DIR), e);
        }
    }

    // ----- seeding and file layout -----

    private void seedDefaultsIfAbsent() throws IOException {
        Path challengesDir = dataFolder.resolve(CHALLENGES_DIR);
        if (Files.isDirectory(challengesDir) && !challengeFiles().isEmpty()) {
            return;
        }
        Files.createDirectories(challengesDir);
        for (String resource : DEFAULT_CHALLENGE_RESOURCES) {
            try (InputStream in = defaults.open(resource)) {
                if (in == null) {
                    throw new IllegalStateException("bundled default " + resource + " not found");
                }
                Files.write(dataFolder.resolve(resource), in.readAllBytes());
            }
        }
    }

    private List<Path> challengeFiles() throws IOException {
        Path challengesDir = dataFolder.resolve(CHALLENGES_DIR);
        if (!Files.isDirectory(challengesDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(challengesDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(ConfigFormats::isSupported)
                    .sorted()
                    .toList();
        }
    }

    /** The target file for a challenge on save: keep an existing file's format, else default to YAML. */
    private Path challengeFileFor(Path challengesDir, String id) throws IOException {
        if (Files.isDirectory(challengesDir)) {
            try (Stream<Path> files = Files.list(challengesDir)) {
                Optional<Path> existing = files
                        .filter(Files::isRegularFile)
                        .filter(ConfigFormats::isSupported)
                        .filter(file -> ConfigFormats.baseName(file).equals(id))
                        .findFirst();
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
        }
        return challengesDir.resolve(id + "." + ConfigFormats.DEFAULT_EXTENSION);
    }

    // ----- DTO <-> domain -----

    private ChallengeRule toRule(ChallengeDto dto, Path file) {
        if (dto.id() == null || dto.id().isBlank()) {
            throw new IllegalStateException("challenge file " + file.getFileName() + " is missing an 'id'");
        }
        List<Task> tasks = dto.tasks() == null
                ? List.of()
                : dto.tasks().stream().map(task -> toTask(task, file)).toList();
        return new ChallengeRule(
                dto.id(), dto.description(), dto.active() != null && dto.active(),
                tasks, dto.sequential() != null && dto.sequential());
    }

    private Task toTask(TaskDto dto, Path file) {
        if (dto.id() == null || dto.id().isBlank()) {
            throw new IllegalStateException("challenge file " + file.getFileName() + " has a task missing an 'id'");
        }
        Reward reward = dto.reward() == null ? null : toReward(dto.reward(), file);
        return new Task(dto.id(), dto.description(), reward);
    }

    private Reward toReward(RewardDto dto, Path file) {
        return switch (dto.type()) {
            case "item" -> {
                if (dto.quantity() == null) {
                    throw new IllegalStateException("challenge file " + file.getFileName()
                            + " has an 'item' reward missing 'quantity'");
                }
                yield new Reward.ItemGrant(ItemId.of(dto.itemId()), dto.quantity());
            }
            case "command" -> new Reward.CommandDispatch(dto.command());
            case "message" -> new Reward.PlayerMessage(dto.message());
            case null, default -> throw new IllegalStateException("challenge file " + file.getFileName()
                    + " has a task reward with an unrecognized 'type' '" + dto.type() + "'");
        };
    }

    private ChallengeDto toDto(ChallengeRule rule) {
        List<TaskDto> tasks = rule.tasks().isEmpty()
                ? null
                : rule.tasks().stream().map(this::toDto).toList();
        return new ChallengeDto(
                rule.id(), rule.description(), rule.active(), tasks, rule.sequential());
    }

    private TaskDto toDto(Task task) {
        return new TaskDto(task.id(), task.description(), task.hasReward() ? toDto(task.reward()) : null);
    }

    private RewardDto toDto(Reward reward) {
        return switch (reward) {
            case Reward.ItemGrant(var item, var quantity) ->
                    new RewardDto("item", item.asString(), quantity, null, null);
            case Reward.CommandDispatch(var command) ->
                    new RewardDto("command", null, null, command, null);
            case Reward.PlayerMessage(var text) ->
                    new RewardDto("message", null, null, null, text);
        };
    }
}
