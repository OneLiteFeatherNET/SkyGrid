plugins {
    id("skygrid.java-conventions")
}

// Driven adapter: the PostgreSQL-backed challenge progress repository. It implements the
// ports.out.ChallengeProgressRepository out-port and compiles against the ports and the public api
// only, never core, per the project's hexagonal dependency rule. Persistence is Jakarta Persistence
// (JSR 338) via Hibernate ORM, bootstrapped standalone (no Spring/Micronaut/application server)
// through the standard Persistence.createEntityManagerFactory entry point; Hibernate owns the
// HikariCP connection pool directly (hibernate-hikaricp). The schema is versioned with Flyway
// migrations bundled as classpath resources (src/main/resources/db/migration), never a hand-edited
// CREATE TABLE assumed to match the code; Hibernate only validates the entity mapping against it
// (hibernate.hbm2ddl.auto=validate), it never creates or alters the schema itself.
// jakarta.persistence, org.hibernate, java.sql, HikariCP and Flyway types stay entirely inside this
// module; they never leak into ports, api or core.
dependencies {
    implementation(project(":ports"))
    implementation(project(":api"))

    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    implementation(libs.jakarta.persistence.api)
    implementation(libs.hibernate.core)
    implementation(libs.hibernate.hikaricp)
    annotationProcessor(libs.hibernate.jpamodelgen)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
