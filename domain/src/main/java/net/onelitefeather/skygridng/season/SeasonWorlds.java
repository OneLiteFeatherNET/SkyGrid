package net.onelitefeather.skygridng.season;


import java.util.List;
import java.util.UUID;

/**
 * Out port: the season worlds on the server. The core drives a transition through this port one step at a
 * time. The platform adapter implements it on the main server thread; a method may do file I/O internally,
 * but a call returns only once its effect is complete.
 */
public interface SeasonWorlds {

    /**
     * Creates the world {@code worldKey} with {@code seed} and {@code preset}, or loads it when its folder
     * already exists. Idempotent for a stored seed, so a restart can call it again.
     *
     * @throws RuntimeException if the world cannot be created or loaded
     */
    void prepare(String worldKey, long seed, String preset);

    /** Whether the world is loaded in the server right now. */
    boolean isLoaded(String worldKey);

    /** Whether the world folder exists on disk, loaded or not. */
    boolean exists(String worldKey);

    /** Whether an archived copy of the world exists. */
    boolean archived(String worldKey);

    /**
     * Moves every player currently in {@code fromKey} to a spread slot in {@code toKey}.
     *
     * @return the ids of the players that were moved, so the core can reset them on arrival
     */
    List<UUID> evacuate(String fromKey, String toKey);

    /**
     * Saves and unloads the world. Returns {@code false} when the world cannot be unloaded yet (for example
     * because a player is still inside); the caller retries on a later tick.
     */
    boolean unload(String worldKey);

    /**
     * Moves the unloaded world folder into the archive and writes its manifest.
     *
     * @throws RuntimeException if the archive cannot be written, for example when the target already exists
     */
    void archive(String worldKey, SeasonRun manifest);
}
