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

    testImplementation(project(":api"))
    testImplementation(project(":ports"))
    testImplementation(project(":core"))
    testImplementation(project(":adapters:generator"))
    testImplementation(project(":adapters:command"))
    testImplementation(project(":adapters:config"))
    testImplementation(project(":adapters:spawn"))
    testImplementation(project(":adapters:challenge"))
    testImplementation(project(":adapters:challenge-progress"))
    testImplementation(project(":adapters:persistence"))
    testImplementation(project(":adapters:task-progress"))
    testImplementation(project(":adapters:scoreboard"))
    testImplementation(project(":platforms:common"))

    testImplementation(libs.paper.api)
}
