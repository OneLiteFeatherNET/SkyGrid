package net.onelitefeather.skygridng.platforms.olf;

/**
 * A OneLiteFeather network only mechanic layered on top of the base SkyGrid-ng generator.
 *
 * Features live only in the {@code skygrid-olf-network} distribution, which is not published to
 * Modrinth or Hangar, so they ship to the OLF network only. A new mechanic (for example challenges)
 * is added by implementing this interface and binding it in {@link OlfModule}; the bootstrap enables
 * and disables every bound feature with the plugin lifecycle. Implementations may inject the
 * {@code JavaPlugin}, the public {@code SkyGridApi}, and any core port.
 */
public interface OlfFeature {

    /** A stable, kebab-case id used in logs. */
    String id();

    /** Called once after the base generator is wired. Register listeners, commands, or tasks here. */
    void enable();

    /** Called once on shutdown. Release anything {@link #enable()} acquired. */
    void disable();
}
