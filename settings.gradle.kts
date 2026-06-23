rootProject.name = "skygrid-ng"

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
            version("gson", "2.11.0")
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

            // ----- Libraries -----
            library("paper-api", "io.papermc.paper", "paper-api").versionRef("paper")
            library("gson", "com.google.code.gson", "gson").versionRef("gson")
            library("guice", "com.google.inject", "guice").versionRef("guice")
            library("jakarta-inject", "jakarta.inject", "jakarta.inject-api").versionRef("jakartaInject")

            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit")
            library("junit-launcher", "org.junit.platform", "junit-platform-launcher").version("1.11.3")
            library("mockbukkit", "org.mockbukkit.mockbukkit", "mockbukkit-v26.1.2").versionRef("mockbukkit")
            library("mockito-core", "org.mockito", "mockito-core").versionRef("mockito")
            library("assertj-core", "org.assertj", "assertj-core").versionRef("assertj")

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
