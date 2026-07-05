plugins {
    id("skygrid.java-conventions")
}

// Driving adapter: a Bukkit listener that enforces challenge rules (for example "no crafting
// table") via the ChallengeAdminUseCase in-port. It compiles against the ports and the public api
// only, never the core. Bukkit and jakarta.inject are provided by the platform at runtime, so they
// are compileOnly.
dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.jakarta.inject)
    implementation(project(":ports"))
    implementation(project(":api"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}
