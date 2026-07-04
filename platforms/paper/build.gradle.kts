import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("skygrid.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.plugin.yml.bukkit)
    alias(libs.plugins.modrinth)
    alias(libs.plugins.hangar)
    alias(libs.plugins.cyclonedx)
}

// Minecraft versions this build targets, reused by Modrinth and Hangar metadata.
val supportedMinecraftVersions = listOf("26.1")

dependencies {
    // The shared composition root brings the whole object graph (adapters, core, ports, api).
    implementation(project(":platforms:common"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    // Paper API is compileOnly for main, so tests need it explicitly (MockBukkit does not export it).
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}

tasks {
    shadowJar {
        // Keep the published artifact name stable (skygrid-ng-<version>.jar) regardless of the
        // producing module path (platforms/paper).
        archiveBaseName.set("skygrid-ng")
        archiveClassifier.set("")
        // Relocate Jackson (and the SnakeYAML its YAML backend pulls in) so they cannot clash with
        // the copies the server or another plugin ships. Bukkit bundles its own SnakeYAML.
        relocate("com.fasterxml.jackson", "net.onelitefeather.skygridng.libs.jackson")
        relocate("org.yaml.snakeyaml", "net.onelitefeather.skygridng.libs.snakeyaml")
        // JSON Schema: victools (plus its classmate) generates the schema files for editors; relocate
        // both so they cannot clash with the copies the server or another plugin ships.
        relocate("com.github.victools", "net.onelitefeather.skygridng.libs.victools")
        relocate("com.fasterxml.classmate", "net.onelitefeather.skygridng.libs.classmate")
        mergeServiceFiles()
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
    main = "net.onelitefeather.skygridng.platforms.common.SkyGridNgPlugin"
    apiVersion = "1.21"
    name = "SkyGrid-ng"
    // World generators must exist before worlds are loaded, so enable at server startup.
    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
    version = project.version.toString()
    description = "SkyGrid and WFC chunk generator for Paper (Java 25, JSON/YAML/TOML config, in game admin, i18n)."
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
    uploadFile.set(tasks.shadowJar)
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
// CycloneDX 3.x moved per-project options to the cyclonedxDirectBom task and removed the
// set*/get* accessors and the outputFormat/destination properties. The aggregate cyclonedxBom
// task still writes build/reports/cyclonedx/bom.{xml,json}, which CI uploads to Dependency-Track.
tasks.cyclonedxDirectBom {
    includeConfigs = listOf("runtimeClasspath")
}
