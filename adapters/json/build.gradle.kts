plugins {
    id("skygrid.java-conventions")
}

// Driven adapter: the Gson-backed rule repository. It implements the out-port and compiles against
// the ports and the public api only. Gson is bundled (and relocated) by the platform distribution.
dependencies {
    implementation(libs.gson)
    implementation(project(":ports"))
    implementation(project(":api"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
}
