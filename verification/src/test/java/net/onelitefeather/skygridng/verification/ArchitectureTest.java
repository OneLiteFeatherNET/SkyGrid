package net.onelitefeather.skygridng.verification;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the hexagonal architecture of SkyGrid-ng with ArchUnit, so the rules that the module
 * graph already encodes cannot rot inside a module (for example a stray Bukkit import in the core,
 * or an adapter reaching into a core service). Runs over every production module's classes.
 *
 * The dependency direction is: platforms -> adapters -> ports -> api, and core -> ports -> api.
 */
@AnalyzeClasses(packages = "net.onelitefeather.skygridng", importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String API = "net.onelitefeather.skygridng.api..";
    private static final String PORTS = "net.onelitefeather.skygridng.ports..";
    private static final String CORE = "net.onelitefeather.skygridng.core..";
    private static final String ADAPTERS = "net.onelitefeather.skygridng.adapters..";
    private static final String PLATFORMS = "net.onelitefeather.skygridng.platforms..";

    /**
     * Packages no platform-neutral layer may ever depend on. Adventure (net.kyori.adventure) is
     * deliberately NOT here: it is a cross-platform text API every supported platform ships, so the
     * api, ports, and core may use it. Only its platform-specific bindings
     * (net.kyori.adventure.platform, which wrap Bukkit/BungeeCord/etc.) stay banned.
     */
    private static final String[] PLATFORM_AND_FRAMEWORK_PACKAGES = {
            "org.bukkit..",
            "io.papermc..",
            "net.minecraft..",
            "com.google.gson..",
            "com.google.inject..",
            "jakarta.inject..",
            "net.kyori.adventure.platform..",
    };

    @ArchTest
    static final ArchRule apiPortsAndCoreAreFrameworkFree =
            noClasses().that().resideInAnyPackage(API, PORTS, CORE)
                    .should().dependOnClassesThat().resideInAnyPackage(PLATFORM_AND_FRAMEWORK_PACKAGES)
                    .because("the api, ports, and core must stay platform-neutral so other platforms "
                            + "(Fabric, Forge, Sponge) can reuse them; resolve blocks via BlockId in an "
                            + "adapter. Adventure text is allowed (it is cross-platform), its platform "
                            + "bindings are not");

    @ArchTest
    static final ArchRule adaptersDependOnPortsAndApiOnlyNotCore =
            noClasses().that().resideInAPackage(ADAPTERS)
                    .should().dependOnClassesThat().resideInAPackage(CORE)
                    .because("an adapter compiles against the ports and the api only, never the core");

    @ArchTest
    static final ArchRule layeredArchitectureIsRespected =
            layeredArchitecture().consideringOnlyDependenciesInLayers()
                    .layer("Api").definedBy(API)
                    .layer("Ports").definedBy(PORTS)
                    .layer("Core").definedBy(CORE)
                    .layer("Adapters").definedBy(ADAPTERS)
                    .layer("Platforms").definedBy(PLATFORMS)
                    .whereLayer("Platforms").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Platforms")
                    .whereLayer("Core").mayOnlyBeAccessedByLayers("Platforms")
                    .whereLayer("Ports").mayOnlyBeAccessedByLayers("Core", "Adapters", "Platforms")
                    .whereLayer("Api").mayOnlyBeAccessedByLayers("Ports", "Core", "Adapters", "Platforms");
}
