package net.onelitefeather.gridwfc.bootstrap.injection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

/**
 * Binds the Bukkit platform into the object graph: the plugin instance and its data folder.
 *
 * Keeping the platform in its own module mirrors the OneLiteFeather convention and isolates the
 * only module that needs a constructor argument.
 */
public final class PlatformModule extends AbstractModule {

    private final JavaPlugin plugin;

    public PlatformModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(JavaPlugin.class).toInstance(plugin);
    }

    @Provides
    @Singleton
    Path provideDataFolder(JavaPlugin plugin) {
        return plugin.getDataFolder().toPath();
    }
}
