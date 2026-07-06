plugins {
    id("skygrid.java-conventions")
}

// Driving adapter: the /skygrid command and its Adventure messages. It compiles against the ports
// only. Bukkit (which bundles Adventure) and jakarta.inject are provided by the platform at runtime.
dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.jakarta.inject)
    implementation(project(":ports"))
    implementation(project(":api"))

    // Stage 1 of the Cloud command framework migration: infrastructure only, no subcommands yet.
    // Declared as `api` so it flows through platforms/common into the platforms/paper shaded jar.
    api(libs.cloud.paper)
    // Stage 2: annotation-driven subcommands (@Command/@Argument/@Permission, AnnotationParser).
    api(libs.cloud.annotations)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.paper.api)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}
