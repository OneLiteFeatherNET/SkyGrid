import xyz.jpenilla.runpaper.task.RunServer

plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.plugin.yml.bukkit)
    alias(libs.plugins.modrinth)
    alias(libs.plugins.hangar)
    alias(libs.plugins.cyclonedx)
}

// group and version come from gradle.properties (version is managed by release-please).

val javaVersion = libs.versions.java.get().toInt()

// Minecraft versions this build targets, reused by Modrinth and Hangar metadata.
val supportedMinecraftVersions = listOf("26.1")

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.gson)
    implementation(libs.guice)
    implementation(libs.jakarta.inject)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

tasks {
    compileJava {
        options.release = javaVersion
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")
        // Relocate Gson so it cannot clash with other plugins on the server classpath.
        relocate("com.google.gson", "net.onelitefeather.skygridng.libs.gson")
    }

    build {
        dependsOn(shadowJar)
    }

    withType<RunServer> {
        minecraftVersion("26.1.2")
    }
}

// Generates plugin.yml from the build (de.eldoria.plugin-yml.bukkit).
bukkit {
    main = "net.onelitefeather.skygridng.bootstrap.SkyGridNgPlugin"
    apiVersion = "1.21"
    name = "SkyGrid-ng"
    version = project.version.toString()
    description = "SkyGrid and WFC chunk generator for Paper (Java 25, JSON config, in game admin, i18n)."
    authors = listOf("TheMeinerLP", "OneLiteFeatherNET")
    website = "https://github.com/OneLiteFeatherNET/SkyGrid-ng"

    commands {
        register("skygrid") {
            description = "Manages the generator configuration at runtime."
            usage = "/skygrid help"
            permission = "skygridng.admin"
        }
    }

    permissions {
        register("skygridng.admin") {
            description = "Allows managing the generator configuration."
        }
    }
}

// ----- Publishing: Modrinth (com.modrinth.minotaur) -----
// Fill in the Modrinth project id/slug after creating the project; token comes from CI.
modrinth {
    token = providers.environmentVariable("MODRINTH_TOKEN")
    projectId = "REPLACE_WITH_MODRINTH_PROJECT_ID"
    versionNumber = project.version.toString()
    versionType = "release"
    uploadFile = tasks.shadowJar
    gameVersions.addAll(supportedMinecraftVersions)
    loaders.addAll("paper", "purpur")
    syncBodyFrom = provider { rootProject.file("README.md").readText() }
}

// ----- Publishing: Hangar (io.papermc.hangar-publish-plugin) -----
hangarPublish {
    publications.register("plugin") {
        version = project.version.toString()
        channel = "Release"
        // Hangar project slug, set after creating the project on hangar.papermc.io.
        id = "SkyGrid-ng"
        apiKey = providers.environmentVariable("HANGAR_API_TOKEN")
        platforms {
            paper {
                jar = tasks.shadowJar.flatMap { it.archiveFile }
                platformVersions.addAll(supportedMinecraftVersions)
            }
        }
    }
}

// ----- Supply chain: CycloneDX SBOM (org.cyclonedx.bom) -----
// Produces build/reports/cyclonedx/bom.xml, uploaded to Dependency-Track in CI.
tasks.cyclonedxBom {
    setIncludeConfigs(listOf("runtimeClasspath"))
    setOutputFormat("xml")
}
