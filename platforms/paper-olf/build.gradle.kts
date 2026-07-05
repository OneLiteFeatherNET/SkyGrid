import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    id("skygrid.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml.bukkit)
}

// run-paper is applied only in :platforms:paper. Use :platforms:paper:runServer for local testing;
// the run-task build service cannot be registered by two modules at once.

// The OneLiteFeather network distribution. It is a sibling platform to :platforms:paper, not a
// dependent of it: both build on the shared :platforms:common composition root. This module adds the
// OLF-only mechanics (the OlfFeature seam) and repackages everything under the OLF identity. The
// whole object graph (adapters, core, ports, api, Guice, jakarta.inject) arrives transitively
// through :platforms:common, so the shaded jar is self contained.
dependencies {
    compileOnly(libs.paper.api)
    implementation(project(":platforms:common"))
    // OLF-only mechanic (challenges), never pulled into :platforms:paper.
    implementation(project(":adapters:challenge"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.assertj.core)
}

tasks {
    shadowJar {
        archiveBaseName.set("skygrid-ng-olf")
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
}

bukkit {
    // The OLF composition root extends the base SkyGridNgPlugin and layers the OLF feature module.
    main = "net.onelitefeather.skygridng.platforms.olf.OlfNetworkPlugin"
    apiVersion = "1.21"
    name = "SkyGrid-ng-OLF"
    // World generators must exist before worlds are loaded, so enable at server startup.
    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
    version = project.version.toString()
    description = "OneLiteFeather network distribution of the SkyGrid-ng chunk generator."
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
