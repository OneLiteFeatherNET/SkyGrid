package net.onelitefeather.skygridng.platforms.common.injection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import net.onelitefeather.skygridng.challenge.PostgresChallengeProgressRepository;
import net.onelitefeather.skygridng.persistence.Database;
import net.onelitefeather.skygridng.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.config.BundledDefaults;
import net.onelitefeather.skygridng.config.JacksonPresetRepository;
import net.onelitefeather.skygridng.config.JacksonRuleRepository;
import net.onelitefeather.skygridng.season.PostgresSeasonRunRepository;
import net.onelitefeather.skygridng.challenge.PostgresTaskCompletionRepository;
import net.onelitefeather.skygridng.challenge.ChallengeProgressService;
import net.onelitefeather.skygridng.challenge.TaskProgressService;
import net.onelitefeather.skygridng.challenge.ChallengeState;
import net.onelitefeather.skygridng.config.JacksonSeasonRepository;
import net.onelitefeather.skygridng.challenge.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.season.CurrentSeason;
import net.onelitefeather.skygridng.challenge.TaskProgressUseCase;
import net.onelitefeather.skygridng.challenge.ChallengeProgressRepository;
import net.onelitefeather.skygridng.generator.PresetRepository;
import net.onelitefeather.skygridng.challenge.RewardGrantor;
import net.onelitefeather.skygridng.generator.RuleRepository;
import net.onelitefeather.skygridng.season.SeasonRepository;
import net.onelitefeather.skygridng.season.SeasonRunRepository;
import net.onelitefeather.skygridng.challenge.TaskCompletionRepository;
import net.onelitefeather.skygridng.generator.GeneratorModule;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Binds the out ports {@link PresetRepository}, {@link RuleRepository},
 * {@link ChallengeProgressRepository} and {@link TaskCompletionRepository} to their concrete
 * adapters.
 *
 * The config is split three ways: a {@code config.<ext>} selector, a {@code presets/} directory (one
 * file per preset) and a {@code rules/} directory (one file per block). On first run the adapters seed
 * their defaults from the bundled resources they read through {@link BundledDefaults}, which here is
 * backed by the plugin jar ({@code JavaPlugin.getResource}) so the core never touches the jar. JSON,
 * YAML and TOML are all accepted; the format is chosen per file from its extension.
 *
 * {@link ChallengeProgressRepository} is the project's first database backed out port: it is bound
 * here (repository bindings live in this module, not {@code ChallengeModule}), together with
 * {@link ChallengeProgressUseCase}, which delegates to the core's thin
 * {@link ChallengeProgressService}, mirroring how {@code GeneratorModule} binds
 * {@code RuleAdminUseCase} to {@code RuleAdminService}.
 */
public final class RepositoryModule extends AbstractModule {

    @Provides
    @Singleton
    PresetRepository providePresetRepository(JavaPlugin plugin, Path dataFolder) {
        BundledDefaults defaults = plugin::getResource;
        return new JacksonPresetRepository(dataFolder, defaults);
    }

    @Provides
    @Singleton
    RuleRepository provideRuleRepository(JavaPlugin plugin, Path dataFolder) {
        BundledDefaults defaults = plugin::getResource;
        return new JacksonRuleRepository(dataFolder, defaults);
    }

    /**
     * The one Postgres database of the plugin: one HikariCP pool and one Flyway history, shared by all
     * three repositories. Construction is cheap: the {@code DatabaseConfig::fromEnvironment} supplier is
     * stored but not invoked here, so no environment variable is read, no pool is opened and no migration
     * runs at provision time. Guice can build it eagerly during {@code onLoad} even when no
     * {@code SKYGRID_DB_*} variable is set. The blocking I/O happens on the async warm-up or the first call.
     */
    @Provides
    @Singleton
    Database provideDatabase() {
        return new Database(DatabaseConfig::fromEnvironment);
    }

    @Provides
    @Singleton
    ChallengeProgressRepository provideChallengeProgressRepository(Database database) {
        return new PostgresChallengeProgressRepository(database);
    }

    /**
     * The season definitions, read from {@code seasons/}. Construction reads no file; the first load happens when the
     * season service is built during {@code onLoad}, like the preset and rule repositories.
     */
    @Provides
    @Singleton
    SeasonRepository provideSeasonRepository(JavaPlugin plugin, Path dataFolder) {
        BundledDefaults defaults = plugin::getResource;
        return new JacksonSeasonRepository(dataFolder, defaults);
    }

    @Provides
    @Singleton
    SeasonRunRepository provideSeasonRunRepository(Database database, Clock clock) {
        return new PostgresSeasonRunRepository(database, clock);
    }

    @Provides
    @Singleton
    ChallengeProgressService provideChallengeProgressService(ChallengeProgressRepository repository,
                                                            CurrentSeason currentSeason) {
        return new ChallengeProgressService(repository, currentSeason);
    }

    @Provides
    @Singleton
    ChallengeProgressUseCase provideChallengeProgressUseCase(ChallengeProgressService service) {
        return service;
    }

    @Provides
    @Singleton
    TaskCompletionRepository provideTaskCompletionRepository(Database database) {
        return new PostgresTaskCompletionRepository(database);
    }

    @Provides
    @Singleton
    TaskProgressService provideTaskProgressService(ChallengeState challengeState, TaskCompletionRepository repository,
                                                   RewardGrantor grantor, CurrentSeason currentSeason) {
        return new TaskProgressService(challengeState, repository, grantor, currentSeason);
    }

    @Provides
    @Singleton
    TaskProgressUseCase provideTaskProgressUseCase(TaskProgressService service) {
        return service;
    }
}
