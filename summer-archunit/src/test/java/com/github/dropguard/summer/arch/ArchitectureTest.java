package com.github.dropguard.summer.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for Summer framework.
 *
 * <p>These rules enforce the layered architecture and core design principles:
 *
 * <ul>
 *   <li>Layered dependency direction
 *   <li>No circular dependencies
 *   <li>No ClassGraph / CGLIB / ByteBuddy dependencies
 * </ul>
 */
class ArchitectureTest {

    private static final String[] PACKAGES = {
        "com.github.dropguard.summer.core",
        "com.github.dropguard.summer.web",
        "com.github.dropguard.summer.aop",
        "com.github.dropguard.summer.tx",
        "com.github.dropguard.summer.runtime",
        "com.github.dropguard.summer.plugin",
        "com.github.dropguard.summer.data",
        "com.github.dropguard.summer.boot",
        "com.github.dropguard.summer.web.netty",
        "com.github.dropguard.summer.grpc",
        "com.github.dropguard.summer.validation",
        "com.github.dropguard.summer.aot",
        "com.github.dropguard.summer.test",
        "com.github.dropguard.summer.tck",
        "com.github.dropguard.summer.arch"
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages(PACKAGES);
    }

    // --- Layered Architecture ---

    @Test
    @DisplayName("Layered architecture: dependencies flow downward only")
    void layeredArchitecture() {
        // @formatter:off
        Architectures.LayeredArchitecture rule =
                Architectures.layeredArchitecture()
                        .consideringOnlyDependenciesInAnyPackage("..com.github.dropguard.summer..")
                        .ignoreDependency("com.github.dropguard.summer.realworld..", "..")
                        .ignoreDependency("com.github.dropguard.summer.benchmark..", "..")
                        .ignoreDependency("..", "com.github.dropguard.summer.realworld..")
                        .ignoreDependency("..", "com.github.dropguard.summer.benchmark..")

                        // layer definitions
                        .layer("Core")
                        .definedBy("..com.github.dropguard.summer.core..")
                        .layer("Infrastructure")
                        .definedBy(
                                "..com.github.dropguard.summer.runtime..",
                                "..com.github.dropguard.summer.plugin..",
                                "..com.github.dropguard.summer.aot..")
                        .layer("Web")
                        .definedBy(
                                "..com.github.dropguard.summer.web..",
                                "..com.github.dropguard.summer.boot..")
                        .layer("Data")
                        .definedBy("..com.github.dropguard.summer.data..")
                        .layer("CrossCutting")
                        .definedBy(
                                "..com.github.dropguard.summer.aop..",
                                "..com.github.dropguard.summer.tx..",
                                "..com.github.dropguard.summer.validation..")
                        .layer("Server")
                        .definedBy(
                                "..com.github.dropguard.summer.web.netty..",
                                "..com.github.dropguard.summer.grpc..")
                        .layer("Test")
                        .definedBy(
                                "..com.github.dropguard.summer.test..",
                                "..com.github.dropguard.summer.tck..",
                                "..com.github.dropguard.summer.arch..")

                        // access constraints
                        .whereLayer("Core")
                        .mayNotAccessAnyLayer()
                        .whereLayer("Infrastructure")
                        .mayOnlyBeAccessedByLayers("Web", "Data", "CrossCutting", "Server", "Test")
                        .whereLayer("Web")
                        .mayOnlyBeAccessedByLayers("Infrastructure", "Server", "Test")
                        .whereLayer("Data")
                        .mayOnlyBeAccessedByLayers("Infrastructure", "Test")
                        .whereLayer("CrossCutting")
                        .mayOnlyBeAccessedByLayers(
                                "Web", "Data", "Infrastructure", "Server", "Test")
                        .whereLayer("Server")
                        .mayOnlyBeAccessedByLayers("Test")
                        .whereLayer("Test")
                        .mayOnlyAccessLayers(
                                "Core", "Infrastructure", "Web", "Data", "CrossCutting", "Server");
        // @formatter:on
        rule.check(classes);
    }

    // --- Core Design Principles ---

    @Test
    @DisplayName("No circular dependencies between packages")
    void noCircularDependencies() {
        ArchRule rule =
                com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
                        .matching("com.github.dropguard.summer.(*)")
                        .should()
                        .beFreeOfCycles()
                        .ignoreDependency("com.github.dropguard.summer.realworld..", "..")
                        .ignoreDependency("com.github.dropguard.summer.benchmark..", "..")
                        .ignoreDependency("..", "com.github.dropguard.summer.realworld..")
                        .ignoreDependency("..", "com.github.dropguard.summer.benchmark..");
        rule.check(classes);
    }

    @Test
    @DisplayName("No ClassGraph dependency")
    void noClassGraphDependency() {
        ArchRule rule =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("io.github.classgraph..")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("No CGLIB dependency")
    void noCglibDependency() {
        ArchRule rule =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("net.sf.cglib..", "org.springframework.cglib..")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("No ByteBuddy dependency")
    void noByteBuddyDependency() {
        ArchRule rule =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("net.bytebuddy..")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    // --- @Replaces usage rules ---

    @Test
    @DisplayName("AOT engine must not depend on the runtime engine")
    void aotMustNotDependOnRuntime() {
        // The two DI engines are peers: the AOT path is compile-time
        // code generation (reflection-free), the runtime path is reflective
        // bean discovery. Neither may compile-depend on the other, or the
        // engine boundary collapses and the AOT path silently re-introduces
        // runtime coupling.
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..com.github.dropguard.summer.aot..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..com.github.dropguard.summer.runtime..");
        rule.check(classes);
    }

    @Test
    @DisplayName("Runtime engine must not depend on the AOT engine")
    void runtimeMustNotDependOnAot() {
        // Symmetric counterpart of {@link #aotMustNotDependOnRuntime}: the
        // runtime engine must not pull in the compile-time generator either.
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..com.github.dropguard.summer.runtime..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..com.github.dropguard.summer.aot..");
        rule.check(classes);
    }

    @Test
    @DisplayName("@Replaces must be on @Configuration in framework packages (not plain @Component)")
    void replacesRequiresConfigurationInFramework() {
        // Rule 1: framework/middleware code must use @Configuration for @Replaces,
        // never on a plain @Component. Business logic and test cases are exempt.
        String[] frameworkPackages = {
            "com.github.dropguard.summer.core..",
            "com.github.dropguard.summer.web..",
            "com.github.dropguard.summer.aop..",
            "com.github.dropguard.summer.tx..",
            "com.github.dropguard.summer.runtime..",
            "com.github.dropguard.summer.plugin..",
            "com.github.dropguard.summer.data..",
            "com.github.dropguard.summer.boot..",
            "com.github.dropguard.summer.web.netty..",
            "com.github.dropguard.summer.grpc..",
            "com.github.dropguard.summer.validation.."
        };

        ArchRule rule =
                com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                        .that()
                        .areAnnotatedWith("com.github.dropguard.summer.core.annotation.Replaces")
                        .and()
                        .resideInAnyPackage(frameworkPackages)
                        .should()
                        .beAnnotatedWith(
                                "com.github.dropguard.summer.core.annotation.Configuration")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    // --- Framework component restrictions ---

    @Test
    @DisplayName("Framework code must use @Configuration + @Bean, not @Component")
    void frameworkCodeMustUseConfigurationNotComponent() {
        String[] frameworkPackages = {
            "com.github.dropguard.summer.core..",
            "com.github.dropguard.summer.web..",
            "com.github.dropguard.summer.aop..",
            "com.github.dropguard.summer.tx..",
            "com.github.dropguard.summer.runtime..",
            "com.github.dropguard.summer.plugin..",
            "com.github.dropguard.summer.data..",
            "com.github.dropguard.summer.boot..",
            "com.github.dropguard.summer.web.netty..",
            "com.github.dropguard.summer.grpc..",
            "com.github.dropguard.summer.validation.."
        };
        // Exclude annotation packages (meta-annotations like @RestController,
        //
        // are @Component by design) and specific marker beans
        String[] annotationPackages = {
            "com.github.dropguard.summer.core.annotation..",
            "com.github.dropguard.summer.web.annotation.."
        };
        ArchRule rule =
                com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                        .that()
                        .resideInAnyPackage(frameworkPackages)
                        .and()
                        .doNotHaveSimpleName("Configuration")
                        .and()
                        .doNotHaveSimpleName("RestController")
                        .and()
                        .doNotHaveSimpleName("MiddlewareRegistry")
                        .and()
                        .areNotAnnotatedWith(
                                "com.github.dropguard.summer.core.annotation.Configuration")
                        .should()
                        .notBeAnnotatedWith("com.github.dropguard.summer.core.Component")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    // --- ServiceLoader restrictions ---

    @Test
    @DisplayName("No ConcurrentHashMap usage")
    void noConcurrentHashMap() {
        String[] productionPackages = {
            "com.github.dropguard.summer.core..",
            "com.github.dropguard.summer.web..",
            "com.github.dropguard.summer.aop..",
            "com.github.dropguard.summer.tx..",
            "com.github.dropguard.summer.runtime..",
            "com.github.dropguard.summer.plugin..",
            "com.github.dropguard.summer.data..",
            "com.github.dropguard.summer.boot..",
            "com.github.dropguard.summer.web.netty..",
            "com.github.dropguard.summer.grpc..",
            "com.github.dropguard.summer.validation.."
        };
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(productionPackages)
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("java.util.concurrent.ConcurrentHashMap")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("No ServiceLoader usage in production code")
    void noServiceLoaderInProduction() {
        String[] productionPackages = {
            "com.github.dropguard.summer.core..",
            "com.github.dropguard.summer.web..",
            "com.github.dropguard.summer.aop..",
            "com.github.dropguard.summer.tx..",
            "com.github.dropguard.summer.runtime..",
            "com.github.dropguard.summer.plugin..",
            "com.github.dropguard.summer.data..",
            "com.github.dropguard.summer.boot..",
            "com.github.dropguard.summer.web.netty..",
            "com.github.dropguard.summer.grpc..",
            "com.github.dropguard.summer.validation.."
        };
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(productionPackages)
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("java.util.ServiceLoader")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    // --- DTO Record constraints ---

    @Test
    @DisplayName("@ConfigurationProperties must be on Record classes")
    void configurationPropertiesRequiresRecord() {
        ArchRule rule =
                com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                        .that()
                        .areAnnotatedWith(
                                "com.github.dropguard.summer.core.config.ConfigurationProperties")
                        .should()
                        .beRecords()
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("@RowModel must be on Record classes")
    void rowModelRequiresRecord() {
        ArchRule rule =
                com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                        .that()
                        .areAnnotatedWith(
                                "com.github.dropguard.summer.data.jdbc.annotation.RowModel")
                        .should()
                        .beRecords()
                        .allowEmptyShould(true);
        rule.check(classes);
    }
}
