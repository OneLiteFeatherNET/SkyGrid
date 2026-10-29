package net.onelitefeather.skygridng.config;

import java.io.InputStream;

/**
 * Supplies the bundled default config files used to seed a fresh data folder.
 *
 * The platform composition root implements this (backed by the plugin jar via
 * {@code JavaPlugin.getResource}) so the adapter can read the shipped defaults without depending on
 * Bukkit. A path is relative to the jar's config resource root, for example {@code "config.yml"} or
 * {@code "presets/classic.yml"}.
 */
@FunctionalInterface
public interface BundledDefaults {

    /** Opens a bundled resource by its jar-relative path, or returns {@code null} if it is absent. */
    InputStream open(String path);
}
