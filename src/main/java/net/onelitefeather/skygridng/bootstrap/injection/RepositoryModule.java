package net.onelitefeather.skygridng.bootstrap.injection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import net.onelitefeather.skygridng.adapter.out.json.JsonRuleRepository;
import net.onelitefeather.skygridng.core.port.out.RuleRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Binds the out port {@link RuleRepository} to its JSON adapter.
 *
 * The bundled default config is read here so the core never touches the plugin jar.
 */
public final class RepositoryModule extends AbstractModule {

    @Provides
    @Singleton
    RuleRepository provideRuleRepository(JavaPlugin plugin, Path dataFolder) {
        Path file = dataFolder.resolve("generator.json");
        return new JsonRuleRepository(file, defaultConfigJson(plugin));
    }

    private String defaultConfigJson(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("generator.json")) {
            if (in == null) {
                throw new IllegalStateException("generator.json not found in plugin jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read bundled generator.json", e);
        }
    }
}
