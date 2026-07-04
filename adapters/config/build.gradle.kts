plugins {
    id("skygrid.java-conventions")
}

// Driven adapter: the Jackson-backed preset repository. It implements the out-port and compiles
// against the ports and the public api only. The on-disk format is picked from the file extension:
// .json, .yml/.yaml and .toml are all supported, so server admins get the format they are used to.
// Jackson (databind plus the YAML and TOML dataformats, and the snakeyaml the YAML backend pulls in)
// is bundled and relocated by the platform distribution.
dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.yaml)
    implementation(libs.jackson.toml)
    // JSON Schema: victools generates a Draft 2020-12 schema from the DTOs and writes it to schema/ so
    // an editor can validate and autocomplete the config. It is bundled and relocated by the platform.
    implementation(libs.jsonschema.generator)
    implementation(libs.jsonschema.module.jackson)
    implementation(project(":ports"))
    implementation(project(":api"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
}
