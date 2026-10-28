package net.onelitefeather.skygridng.platforms.common.injection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import net.onelitefeather.skygridng.adapters.challengeprogress.PostgresChallengeProgressRepository;
import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.adapters.config.BundledDefaults;
import net.onelitefeather.skygridng.adapters.config.JacksonPresetRepository;
import net.onelitefeather.skygridng.adapters.config.JacksonRuleRepository;
import net.onelitefeather.skygridng.adapters.taskprogress.PostgresTaskCompletionRepository;
import net.onelitefeather.skygridng.core.service.ChallengeProgressService;
import net.onelitefeather.skygridng.core.service.LegacyCurrentSeason;
import net.onelitefeather.skygridng.core.service.TaskProgressService;
import net.onelitefeather.skygridng.core.state.ChallengeState;
import net.onelitefeather.skygridng.ports.in.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import net.onelitefeather.skygridng.ports.in.TaskProgressUseCase;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import net.onelitefeather.skygridng.ports.out.PresetRepository;
import net.onelitefeather.skygridng.ports.out.RewardGrantor;
import net.onelitefeather.skygridng.ports.out.RuleRepository;
import net.onelitefeather.skygridng.ports.out.TaskCompletionRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

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
     * Construction is cheap: the {@code DatabaseConfig::fromEnvironment} supplier is stored but not
     * invoked here, so no environment variable is read, no HikariCP pool is opened and no Flyway
     * migration runs at provision time. Guice can therefore build this singleton eagerly during
     * {@code onLoad} (server {@code STARTUP}) even when no {@code SKYGRID_DB_*} variable is set or no
     * Postgres is reachable. The blocking I/O (resolving the config, opening the pool, running the
     * migration) happens lazily, on the first actual call to {@code tierOf}, {@code recordCompletion}
     * or {@code resetCurrentSeason}.
     */
    @Provides
    @Singleton
    ChallengeProgressRepository provideChallengeProgressRepository() {
        return new PostgresChallengeProgressRepository(DatabaseConfig::fromEnvironment);
    }

    @Provides
    @Singleton
    CurrentSeason provideCurrentSeason() {
        return new LegacyCurrentSeason();
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

    /**
     * Construction is cheap for the same reason as {@link #provideChallengeProgressRepository}:
     * {@code PostgresTaskCompletionRepository} extends the same {@code AbstractJpaRepository} base,
     * so the environment lookup, the HikariCP pool and the Flyway migration are all deferred to the
     * first real call.
     */
    @Provides
    @Singleton
    TaskCompletionRepository provideTaskCompletionRepository() {
        return new PostgresTaskCompletionRepository(DatabaseConfig::fromEnvironment);
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
