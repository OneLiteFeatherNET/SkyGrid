package net.onelitefeather.skygridng.verification;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the architecture of SkyGrid-ng with ArchUnit. The domain module (framework free) and the
 * plugin module (Paper, Guice, Jackson, JPA) are told apart by the jar they are compiled into, so the
 * rules do not depend on package names. Feature packages (generator, challenge, season, ...) are
 * checked for cycles as slices.
 */
@AnalyzeClasses(packages = "net.onelitefeather.skygridng", importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "net.onelitefeather.skygridng";

    /**
     * Packages no domain class may ever depend on. Adventure (net.kyori.adventure) is deliberately
     * NOT here: it is a cross-platform text API every supported platform ships, so the domain may use
     * it. Only its platform-specific bindings (net.kyori.adventure.platform) stay banned.
     */
    private static final String[] DOMAIN_FORBIDDEN_PACKAGES = {
            "org.bukkit..",
            "io.papermc..",
            "net.minecraft..",
            "com.fasterxml..",
            "com.google.gson..",
            "com.google.inject..",
            "jakarta.inject..",
            "jakarta.persistence..",
            "org.hibernate..",
            "net.kyori.adventure.platform..",
    };

    /** Classes compiled from the domain module. */
    private static final DescribedPredicate<JavaClass> IN_DOMAIN_MODULE =
            DescribedPredicate.describe("located in the domain module", javaClass -> isFromModule(javaClass, "domain"));

    @ArchTest
    static final ArchRule domainIsFrameworkFree =
            noClasses().that(IN_DOMAIN_MODULE)
                    .should().dependOnClassesThat().resideInAnyPackage(DOMAIN_FORBIDDEN_PACKAGES)
                    .because("the domain must stay platform-neutral so other platforms (Fabric, Forge, Sponge) "
                            + "can reuse it; resolve blocks via BlockId in the plugin module. Adventure text is "
                            + "allowed (it is cross-platform), its platform bindings are not. Jakarta Persistence "
                            + "and Hibernate types belong to the plugin module too");

    @ArchTest
    static final ArchRule domainReadsNoSystemClock =
            noClasses().that(IN_DOMAIN_MODULE)
                    .should().callMethod(Clock.class, "systemUTC")
                    .orShould().callMethod(Instant.class, "now")
                    .orShould().callMethod(System.class, "currentTimeMillis")
                    .because("time comes only through an injected java.time.Clock, so every scheduling decision is testable");

    @ArchTest
    static final ArchRule entitiesLiveInPluginModule =
            classes().that().areAnnotatedWith(Entity.class)
                    .should(new ArchCondition<JavaClass>("reside in the plugin module") {
                        @Override
                        public void check(JavaClass item, ConditionEvents events) {
                            boolean inPlugin = isFromModule(item, "plugin");
                            events.add(new SimpleConditionEvent(item, inPlugin,
                                    item.getName() + (inPlugin ? " is in the plugin module" : " is not in the plugin module")));
                        }
                    })
                    .because("@Entity classes are persistence mapping, which only the plugin module owns");

    private static final String JAVA_SQL_DRIVER_MANAGER = "java.sql.DriverManager";

    @ArchTest
    static final ArchRule noProductionClassUsesDriverManager =
            noClasses().should().dependOnClassesThat().haveFullyQualifiedName(JAVA_SQL_DRIVER_MANAGER)
                    .because("DriverManager only sees JDBC drivers registered with the class loader of its caller, and "
                            + "inside the shaded Paper plugin jar the PostgreSQL driver lives in the plugin class loader, "
                            + "so DriverManager.getConnection fails with 'No suitable driver found'; open connections "
                            + "through the DataSource that Flyway and Hikari already use");

    @ArchTest
    static final ArchRule noNativeQueryWithoutResultType =
            noClasses().should().callMethod(EntityManager.class, "createNativeQuery", String.class)
                    .because("the persistence adapters use JPQL named queries only; no native SQL string is built at runtime");

    @ArchTest
    static final ArchRule noNativeQueryWithResultClass =
            noClasses().should().callMethod(EntityManager.class, "createNativeQuery", String.class, Class.class)
                    .because("the persistence adapters use JPQL named queries only; no native SQL string is built at runtime");

    @ArchTest
    static final ArchRule noNativeQueryWithResultMapping =
            noClasses().should().callMethod(EntityManager.class, "createNativeQuery", String.class, String.class)
                    .because("the persistence adapters use JPQL named queries only; no native SQL string is built at runtime");

    @ArchTest
    static final ArchRule featureSlicesAreFreeOfCycles =
            slices().matching(ROOT + ".(*)..").should().beFreeOfCycles()
                    .because("feature packages depend on each other in one direction only; a cycle means two "
                            + "features cannot be understood, changed or tested apart");

    /**
     * Each module is consumed as its jar on the verification classpath, so the jar name identifies the
     * module. ArchUnit reports jar sources as opaque {@code jar:} URIs, hence the string match.
     */
    private static boolean isFromModule(JavaClass javaClass, String module) {
        return javaClass.getSource()
                .map(source -> source.getUri().toString())
                .map(uri -> uri.contains("/" + module + "/build/libs/") || uri.contains("/" + module + "/build/classes/"))
                .orElse(false);
    }
}
