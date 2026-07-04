plugins {
    id("skygrid.java-conventions")
}

// The public API: stable, platform-neutral interfaces and immutable model records that other
// plugins and OneLiteFeather network components compile against. It must never import Bukkit, Gson,
// Guice, or jakarta.inject. Adventure (net.kyori.adventure) is allowed: it is a cross-platform text
// API every supported platform ships, so it does not tie the api to one platform. It is compileOnly
// because the platform (Paper, and equivalents elsewhere) provides Adventure at runtime.
dependencies {
    compileOnly(libs.adventure.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.adventure.api)
}
