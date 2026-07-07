plugins {
    id("skygrid.java-conventions")
}

// Driven adapter: the PostgreSQL-backed challenge progress repository. It implements the
// ports.out.ChallengeProgressRepository out-port and compiles against the ports and the public api
// only, never core, per the project's hexagonal dependency rule. The generic JPA/Hibernate bootstrap
// (lazy EntityManagerFactory construction, Flyway migration, Hikari-via-Hibernate wiring,
// transaction lifecycle) lives in the shared adapters/persistence module (AbstractJpaRepository);
// this module only adds the challenge_tier-specific entity, metamodel, migration, and repository
// logic (the pessimistic-lock-plus-retry completion recording, the Criteria based season reset).
dependencies {
    implementation(project(":ports"))
    implementation(project(":api"))
    implementation(project(":adapters:persistence"))

    // jakarta.persistence.api and hibernate.core come transitively through adapters:persistence
    // (declared there with the api configuration), since AbstractJpaRepository's read/inTransaction
    // signatures and this module's own use of Hibernate-specific exception types need them at
    // compile time too.
    annotationProcessor(libs.hibernate.jpamodelgen)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
