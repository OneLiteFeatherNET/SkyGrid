plugins {
    id("skygrid.java-conventions")
    `java-test-fixtures`
}

// The core is platform-neutral: no Bukkit, Gson, Guice, or jakarta.inject. Adventure
// (net.kyori.adventure) is allowed as a cross-platform text API and is compileOnly, since the
// platform provides it at runtime. The core implements the ports and uses the public api (model),
// both exposed with the api configuration so the types resolve transitively for the platform
// modules. The shared test data builder (Fixtures) lives in the testFixtures source set so the
// platform modules can reuse it.
dependencies {
    api(project(":ports"))
    api(project(":api"))
    compileOnly(libs.adventure.api)
    testFixturesCompileOnly(libs.adventure.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.adventure.api)
}
