plugins {
    id("skygrid.java-conventions")
}

// The shared Bukkit composition root: the plugin base class (SkyGridNgPlugin), i18n, and the Guice
// modules that wire the adapters to the ports. The single Paper distribution (platforms/paper) builds
// on it, so the whole object graph is exposed with the api configuration. This is a library: no
// shadow, no plugin-yml, no publishing. Bukkit is provided by the server at runtime, hence compileOnly.
dependencies {
    compileOnly(libs.paper.api)
    api(project(":core"))
    api(project(":ports"))
    api(project(":api"))
    api(project(":adapters:generator"))
    api(project(":adapters:command"))
    api(project(":adapters:config"))
    api(project(":adapters:spawn"))
    api(project(":adapters:challenge"))
    api(project(":adapters:challenge-progress"))
    api(project(":adapters:persistence"))
    api(libs.guice)
    api(libs.jakarta.inject)

    // cloud-paper is the Bukkit/Paper binding for the Cloud command framework, it belongs on the
    // composition root, not in the framework-free adapters/command (which only depends on
    // cloud-core/cloud-annotations). This module hosts PaperCommandSource, CloudCommandManagerFactory
    // and CloudCommandRegistrar, all of which need org.incendo.cloud.paper.* types.
    api(libs.cloud.paper)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    // Paper API is compileOnly for main, so tests need it explicitly (MockBukkit does not export it).
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(testFixtures(project(":core")))
}
