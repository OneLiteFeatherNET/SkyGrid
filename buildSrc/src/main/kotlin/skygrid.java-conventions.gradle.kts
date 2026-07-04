plugins {
    `java-library`
}

// The Java version is fixed by the target platform (Paper 26.1 requires Java 25). It is a literal
// here because buildSrc cannot read the inline version catalog declared in settings.gradle.kts.
// Keep it in sync with the "java" version in the catalog.
val javaVersion = 25

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
    // Keep constructor and record-component parameter names in the bytecode so reflective binders
    // (the Jackson config adapter) can map on-disk keys to record components by name.
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
