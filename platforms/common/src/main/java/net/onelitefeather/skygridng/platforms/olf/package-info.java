/**
 * The OneLiteFeather network distribution of SkyGrid-ng.
 *
 * This module is a sibling platform to {@code skygrid-paper}. {@link
 * net.onelitefeather.skygridng.platforms.olf.OlfNetworkPlugin} reuses the base composition root and
 * layers OLF only mechanics on top through the {@link
 * net.onelitefeather.skygridng.platforms.olf.OlfFeature} seam: each mechanic is one feature bound in
 * {@link net.onelitefeather.skygridng.platforms.olf.OlfModule} and enabled with the plugin lifecycle.
 *
 * These mechanics (for example challenges) ship only here. The module is not published to Modrinth or
 * Hangar, so OLF only features never reach the public distribution. Features may inject the public
 * api, the core ports, and the {@code JavaPlugin}, but the dependency direction stays
 * adapter -&gt; core -&gt; api: nothing here is depended on by the core or the api.
 */
package net.onelitefeather.skygridng.platforms.olf;
