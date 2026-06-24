plugins {
    id("skygrid.java-conventions")
}

// The ports: the driving in-ports, the driven out-ports, and the terrain algorithm SPI that the
// adapters compile against. Framework free; depends only on the public api (the model it exposes in
// signatures). The core implements these ports; the adapters depend on them, never on the core.
dependencies {
    api(project(":api"))
}
