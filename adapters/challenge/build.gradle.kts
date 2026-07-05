plugins {
    id("skygrid.java-conventions")
}

// Driving adapter: a Bukkit listener that enforces challenge rules (for example "no crafting
// table") via the ChallengeAdminUseCase in-port. It compiles against the ports and the public api
// only, never the core. Bukkit is provided by the platform at runtime, so it is compileOnly.
dependencies {
    compileOnly(libs.paper.api)
    implementation(project(":ports"))
    implementation(project(":api"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}
