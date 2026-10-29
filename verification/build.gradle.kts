plugins {
    id("skygrid.java-conventions")
}

// Architecture verification: ArchUnit tests that enforce the hexagonal rules at build time, so they
// cannot silently rot. This module has no production code; it depends on every production module so
// ArchUnit can import and analyze their compiled classes. paper-api is on the test classpath so the
// adapters' Bukkit references resolve cleanly during analysis.
dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.assertj.core)
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    // Flyway drives the pre-season migration state in PostgresAdapterMigrationTest (migrate to V2, then
    // seed rows, then let the adapters apply the season migrations).
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)

    testImplementation(project(":domain"))
    testImplementation(project(":plugin"))

    testImplementation(libs.paper.api)
}
