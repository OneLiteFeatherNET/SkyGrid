// Root aggregation build. No production code lives here. Each module under skygrid-* applies the
// skygrid.java-conventions plugin from buildSrc. Platform concerns (shadow, plugin-yml, run-paper,
// publishing) live in the platform modules (skygrid-paper, and later skygrid-olf-network).

allprojects {
    group = "net.onelitefeather"
    // Strip the trailing release-please marker comment from the gradle.properties version so the
    // jar name and the Modrinth/Hangar version stay clean. release-please still updates the number
    // in gradle.properties through the x-release-please-version annotation.
    version = providers.gradleProperty("version").get().substringBefore("#").trim()
}
