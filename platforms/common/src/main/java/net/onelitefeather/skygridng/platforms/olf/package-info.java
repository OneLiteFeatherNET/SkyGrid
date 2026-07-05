/**
 * The OneLiteFeather (OLF) specific mechanics of SkyGrid-ng.
 *
 * This package lives inside the shared {@code platforms.common} composition root, not a separate
 * distribution module. {@link net.onelitefeather.skygridng.platforms.common.SkyGridNgPlugin} wires
 * OLF only mechanics through the {@link net.onelitefeather.skygridng.platforms.olf.OlfFeature} seam:
 * each mechanic is one feature bound in {@link net.onelitefeather.skygridng.platforms.olf.OlfModule}
 * and enabled with the plugin lifecycle.
 *
 * The {@code olf} naming stays as a documentation and naming convention only: these mechanics (for
 * example challenges) are specific to the OneLiteFeather network and not meant for general
 * redistribution, even though there is now a single jar and a single plugin class. Features may
 * inject the public api, the core ports, and the {@code JavaPlugin}, but the dependency direction
 * stays adapter -&gt; core -&gt; api: nothing here is depended on by the core or the api.
 */
package net.onelitefeather.skygridng.platforms.olf;
