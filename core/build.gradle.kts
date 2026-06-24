plugins {
    id("skygrid.java-conventions")
    `java-test-fixtures`
}

// The core is framework free: no Bukkit, Gson, Guice, jakarta.inject, or Adventure. It implements
// the ports and uses the public api (model), both exposed with the api configuration so the types
// resolve transitively for the platform modules. The shared test data builder (Fixtures) lives in
// the testFixtures source set so the platform modules can reuse it.
dependencies {
    api(project(":ports"))
    api(project(":api"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
}
