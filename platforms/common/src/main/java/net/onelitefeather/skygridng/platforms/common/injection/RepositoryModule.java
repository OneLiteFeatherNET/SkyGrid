package net.onelitefeather.skygridng.platforms.common.injection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import net.onelitefeather.skygridng.adapters.config.BundledDefaults;
import net.onelitefeather.skygridng.adapters.config.JacksonPresetRepository;
import net.onelitefeather.skygridng.adapters.config.JacksonRuleRepository;
import net.onelitefeather.skygridng.ports.out.PresetRepository;
import net.onelitefeather.skygridng.ports.out.RuleRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

/**
 * Binds the out ports {@link PresetRepository} and {@link RuleRepository} to their Jackson adapters.
 *
 * The config is split three ways: a {@code config.<ext>} selector, a {@code presets/} directory (one
 * file per preset) and a {@code rules/} directory (one file per block). On first run the adapters seed
 * their defaults from the bundled resources they read through {@link BundledDefaults}, which here is
 * backed by the plugin jar ({@code JavaPlugin.getResource}) so the core never touches the jar. JSON,
 * YAML and TOML are all accepted; the format is chosen per file from its extension.
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
}
