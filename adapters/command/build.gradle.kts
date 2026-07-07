plugins {
    id("skygrid.java-conventions")
}

// Driving adapter: the /skygrid command and its Adventure messages. It compiles against the ports
// only, framework-free: no Bukkit, no Guice/jakarta.inject. Cloud command classes here are
// annotated (@Command, @Argument, @Parser) but platform-neutral; the Paper/Bukkit binding
// (cloud-paper) lives in platforms/common instead.
dependencies {
    implementation(project(":ports"))
    implementation(project(":api"))

    // Messages and the neutral CommandSource use Adventure's platform-neutral text API directly
    // (Component). Previously reached transitively through paper-api/cloud-paper; now that neither
    // is on this module's compile classpath, it needs its own compileOnly, matching api/build.gradle.kts's
    // own compileOnly(libs.adventure.api). The platform (Paper) provides the real implementation at
    // runtime.
    compileOnly(libs.adventure.api)

    // Cloud command framework: annotation processing and the platform-neutral command core.
    // Declared as `api` so it flows through platforms/common into the platforms/paper shaded jar,
    // same reasoning as the stage-1 cloud-paper dependency this replaced. The Paper/Bukkit binding
    // (cloud-paper) lives in platforms/common instead, alongside CloudCommandManagerFactory,
    // PaperCommandSource, and CloudCommandRegistrar.
    api(libs.cloud.core)
    api(libs.cloud.annotations)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.adventure.api)
}
