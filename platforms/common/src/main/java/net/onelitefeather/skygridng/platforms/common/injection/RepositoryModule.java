package net.onelitefeather.skygridng.platforms.common.injection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import net.onelitefeather.skygridng.adapters.challengeprogress.DatabaseConfig;
import net.onelitefeather.skygridng.adapters.challengeprogress.PostgresChallengeProgressRepository;
import net.onelitefeather.skygridng.adapters.config.BundledDefaults;
import net.onelitefeather.skygridng.adapters.config.JacksonPresetRepository;
import net.onelitefeather.skygridng.adapters.config.JacksonRuleRepository;
import net.onelitefeather.skygridng.core.service.ChallengeProgressService;
import net.onelitefeather.skygridng.ports.in.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import net.onelitefeather.skygridng.ports.out.PresetRepository;
import net.onelitefeather.skygridng.ports.out.RuleRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

/**
 * Binds the out ports {@link PresetRepository}, {@link RuleRepository} and
 * {@link ChallengeProgressRepository} to their concrete adapters.
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
     * or {@code resetAll}.
     */
    @Provides
    @Singleton
    ChallengeProgressRepository provideChallengeProgressRepository() {
        return new PostgresChallengeProgressRepository(DatabaseConfig::fromEnvironment);
    }

    @Provides
    @Singleton
    ChallengeProgressService provideChallengeProgressService(ChallengeProgressRepository repository) {
        return new ChallengeProgressService(repository);
    }

    @Provides
    @Singleton
    ChallengeProgressUseCase provideChallengeProgressUseCase(ChallengeProgressService service) {
        return service;
    }
}
