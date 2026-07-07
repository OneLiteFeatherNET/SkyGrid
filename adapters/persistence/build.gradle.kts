plugins {
    id("skygrid.java-conventions")
}

// Shared driven-adapter infrastructure: the reusable Jakarta Persistence (JSR 338) via Hibernate
// ORM bootstrap that every Postgres-backed adapter in this project builds on, so a new adapter
// needs only its own @Entity class, a small concrete repository, and one persistence-unit entry,
// not a second copy of the factory-bootstrap/transaction-lifecycle plumbing. This module compiles
// against the ports and the public api only, never core, per the project's hexagonal dependency
// rule. Persistence is bootstrapped standalone (no Spring/Micronaut/application server) through the
// standard Persistence.createEntityManagerFactory entry point; Hibernate owns the HikariCP
// connection pool directly (hibernate-hikaricp). Schema ownership (versioned Flyway migrations,
// never a hand-edited CREATE TABLE assumed to match the code) is left to each concrete adapter's own
// persistence unit and migration location; this module only owns the generic lazy bootstrap and
// transaction helpers that every one of them shares.
// jakarta.persistence, org.hibernate, java.sql, HikariCP and Flyway types stay entirely inside this
// module and the adapters that depend on it; they never leak into ports, api or core.
dependencies {
    implementation(project(":ports"))
    implementation(project(":api"))

    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.hibernate.hikaricp)

    // Exposed with the api configuration (not implementation): AbstractJpaRepository's protected
    // read/inTransaction methods take a jakarta.persistence.EntityManager, and a concrete subclass
    // (for example PostgresChallengeProgressRepository) typically also needs Hibernate-specific
    // exception types (ConstraintViolationException) directly at compile time. Declaring these as
    // api here means a subclass module only needs implementation(project(":adapters:persistence")),
    // not a repeated copy of these library coordinates.
    api(libs.jakarta.persistence.api)
    api(libs.hibernate.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
