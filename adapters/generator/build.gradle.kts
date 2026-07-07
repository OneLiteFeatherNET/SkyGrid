plugins {
    id("skygrid.java-conventions")
}

// The decoration/structure/still-water logic: framework-free, like core. It compiles against the
// ports and the public api only, resolving everything through ports.algorithm.RegionWriter instead
// of a Bukkit type directly (the Bukkit implementation of that port lives in platforms/common).
dependencies {
    implementation(project(":ports"))
    implementation(project(":api"))
    // BlockId implements Adventure's Keyed; the platform provides Adventure at runtime.
    compileOnly(libs.adventure.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.adventure.api)
    testImplementation(libs.mockito.core)
}
