plugins {
    id("skygrid.java-conventions")
}

// Driven adapter: the PostgreSQL-backed challenge progress repository. It implements the
// ports.out.ChallengeProgressRepository out-port and compiles against the ports and the public api
// only, never core, per the project's hexagonal dependency rule. Connections are pooled with
// HikariCP; the schema is versioned with Flyway migrations bundled as classpath resources
// (src/main/resources/db/migration), never a hand-edited CREATE TABLE assumed to match the code.
// java.sql.*, HikariCP and Flyway types stay entirely inside this module; they never leak into
// ports, api or core.
dependencies {
    implementation(project(":ports"))
    implementation(project(":api"))

    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
