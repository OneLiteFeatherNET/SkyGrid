plugins {
    id("skygrid.java-conventions")
}

// The public API: stable, framework-free interfaces and immutable model records that other
// plugins and OneLiteFeather network components compile against. It has no dependencies beyond
// the JDK and must never import Bukkit, Gson, Guice, jakarta.inject, or Adventure.
dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
}
