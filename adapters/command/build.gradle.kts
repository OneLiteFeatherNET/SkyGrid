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

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.paper.api)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}
