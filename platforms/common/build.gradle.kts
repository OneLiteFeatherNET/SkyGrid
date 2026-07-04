plugins {
    id("skygrid.java-conventions")
}

// The shared Bukkit composition root: the plugin base class (SkyGridNgPlugin), i18n, and the Guice
// modules that wire the adapters to the ports. Both distributions (paper, paper-olf) build on it, so
// the whole object graph is exposed with the api configuration. This is a library: no shadow, no
// plugin-yml, no publishing. Bukkit is provided by the server at runtime, hence compileOnly.
dependencies {
    compileOnly(libs.paper.api)
    api(project(":core"))
    api(project(":ports"))
    api(project(":api"))
    api(project(":adapters:generator"))
    api(project(":adapters:command"))
    api(project(":adapters:config"))
    api(project(":adapters:spawn"))
    api(libs.guice)
    api(libs.jakarta.inject)
}
