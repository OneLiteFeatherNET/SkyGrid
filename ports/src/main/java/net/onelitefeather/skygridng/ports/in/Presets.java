package net.onelitefeather.skygridng.ports.in;

import java.util.List;
import java.util.Map;

/**
 * In port: switching between named generation presets at runtime (called by the command adapter).
 *
 * A preset is a complete generation profile (classic, modern, nether, ...). Activating one resolves
 * its inheritance graph and applies the result to the default generator (the active selection is
 * persisted). Each world may also pick its own preset through its generator spec, so {@link #worlds}
 * reports which preset every world uses.
 */
public interface Presets {

    /** The ids of all presets, in their stored order. */
    List<String> names();

    /** The id of the active (default) preset. */
    String active();

    /** Activates the named preset as the default. Returns false for an unknown id. */
    boolean activate(String name);

    /** The preset each world has been assigned (by world name), as seen so far this session. */
    Map<String, String> worlds();
}
