plugins {
    id("skygrid.java-conventions")
    `java-test-fixtures`
}

// The domain: the public api (model and interfaces other plugins compile against), the ports (driving
// and driven interfaces, plus the terrain algorithm SPI) and the core (the generation logic and
// services that implement the ports). It is framework free and must never import Bukkit, Jackson,
// Guice, or jakarta.inject; this module's compile classpath enforces that. Adventure
// (net.kyori.adventure) is allowed as a cross-platform text API and is compileOnly, since the platform
// provides it at runtime. The shared test data builder (Fixtures) lives in the testFixtures source set
// so the plugin module can reuse it.
dependencies {
    compileOnly(libs.adventure.api)
    testFixturesCompileOnly(libs.adventure.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.adventure.api)
    testImplementation(libs.mockito.core)
}
