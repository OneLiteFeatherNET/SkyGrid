rootProject.name = "skygrid-ng"

include(":api")
include(":ports")
include(":core")
include(":adapters:generator")
include(":adapters:command")
include(":adapters:config")
include(":adapters:spawn")
include(":adapters:challenge")
include(":platforms:common")
include(":platforms:paper")
include(":platforms:paper-olf")
include(":verification")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    }
}

// Version catalog kept inline in settings.gradle.kts (instead of gradle/libs.versions.toml).
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.codemc.io/repository/maven-public/") { name = "codemc" }
    }

    versionCatalogs {
        create("libs") {
            // ----- Versions (verify before the first build) -----
            version("paper", "26.1.2.build.+")  // keep the build segment
            version("java", "25")               // Paper 26.1 requires Java 25
            version("jackson", "2.22.0")        // config databinding: JSON/YAML/TOML (relocated in the shaded jar)
            version("jsonSchemaGen", "4.38.0")  // victools; generates the editor schema (Jackson 2.x line)
            version("adventure", "4.24.0")      // platform-neutral text API; matches Paper 26.1's adventure-bom
            version("guice", "7.0.0")
            version("jakartaInject", "2.0.1")

            version("shadow", "9.4.2")
            version("runPaper", "3.0.2")
            version("pluginYml", "0.8.0")
            version("modrinth", "2.+")
            version("hangar", "0.1.4")
            version("cyclonedx", "3.2.4")

            // Testing
            version("junit", "5.11.3")
            version("mockbukkit", "4.+")
            version("mockito", "5.14.2")
            version("assertj", "3.27.7")
            version("archunit", "1.4.2")

            // ----- Libraries -----
            library("paper-api", "io.papermc.paper", "paper-api").versionRef("paper")
            library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind").versionRef("jackson")
            library("jackson-yaml", "com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml").versionRef("jackson")
            library("jackson-toml", "com.fasterxml.jackson.dataformat", "jackson-dataformat-toml").versionRef("jackson")
            library("jsonschema-generator", "com.github.victools", "jsonschema-generator").versionRef("jsonSchemaGen")
            library("jsonschema-module-jackson", "com.github.victools", "jsonschema-module-jackson").versionRef("jsonSchemaGen")
            library("adventure-api", "net.kyori", "adventure-api").versionRef("adventure")
            library("guice", "com.google.inject", "guice").versionRef("guice")
            library("jakarta-inject", "jakarta.inject", "jakarta.inject-api").versionRef("jakartaInject")

            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit")
            library("junit-launcher", "org.junit.platform", "junit-platform-launcher").version("1.11.3")
            library("mockbukkit", "org.mockbukkit.mockbukkit", "mockbukkit-v26.1.2").versionRef("mockbukkit")
            library("mockito-core", "org.mockito", "mockito-core").versionRef("mockito")
            library("assertj-core", "org.assertj", "assertj-core").versionRef("assertj")
            library("archunit-junit5", "com.tngtech.archunit", "archunit-junit5").versionRef("archunit")

            // ----- Plugins -----
            plugin("shadow", "com.gradleup.shadow").versionRef("shadow")
            plugin("run-paper", "xyz.jpenilla.run-paper").versionRef("runPaper")
            plugin("plugin-yml-bukkit", "de.eldoria.plugin-yml.bukkit").versionRef("pluginYml")
            plugin("modrinth", "com.modrinth.minotaur").versionRef("modrinth")
            plugin("hangar", "io.papermc.hangar-publish-plugin").versionRef("hangar")
            plugin("cyclonedx", "org.cyclonedx.bom").versionRef("cyclonedx")
        }
    }
}
