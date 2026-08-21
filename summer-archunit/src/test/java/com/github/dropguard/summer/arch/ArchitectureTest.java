package com.github.dropguard.summer.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.DisplayName;

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
@AnalyzeClasses(
        packages = "com.github.dropguard.summer",
        importOptions = {ImportOption.DoNotIncludeTests.class, ProductionOnlyImportOption.class})
class ArchitectureTest {

    private static final String[] PACKAGES = {
        "com.github.dropguard.summer.core",
        "com.github.dropguard.summer.web",
        "com.github.dropguard.summer.aop",
        "com.github.dropguard.summer.tx",
        "com.github.dropguard.summer.runtime",
        "com.github.dropguard.summer.engine",
        "com.github.dropguard.summer.plugin",
        "com.github.dropguard.summer.data",
        "com.github.dropguard.summer.boot",
        "com.github.dropguard.summer.web.server",
        "com.github.dropguard.summer.grpc",
        "com.github.dropguard.summer.core.validation",
        "com.github.dropguard.summer.aot",
        "com.github.dropguard.summer.test",
        "com.github.dropguard.summer.tck",
        "com.github.dropguard.summer.arch"
    };

    /**
     * Production packages only — excludes test, tck, arch, benchmark, and demo modules. Single
     * source of truth for rules that differ between production and non-production code.
     */
    private static final String[] PRODUCTION = {
        "com.github.dropguard.summer.core..",
        "com.github.dropguard.summer.web..",
        "com.github.dropguard.summer.aop..",
        "com.github.dropguard.summer.tx..",
        "com.github.dropguard.summer.runtime..",
        "com.github.dropguard.summer.engine..",
        "com.github.dropguard.summer.plugin..",
        "com.github.dropguard.summer.data..",
        "com.github.dropguard.summer.boot..",
        "com.github.dropguard.summer.web.server..",
        "com.github.dropguard.summer.grpc..",
        "com.github.dropguard.summer.core.validation..",
        "com.github.dropguard.summer.aot.."
    };

    // --- Layered Architecture ---

    @ArchTest
    @DisplayName("Layered architecture: dependencies flow downward only")
    void layeredArchitecture(JavaClasses classes) {
        // @formatter:off
        Architectures.LayeredArchitecture rule =
                Architectures.layeredArchitecture()
                        .consideringOnlyDependenciesInAnyPackage("..com.github.dropguard.summer..")
                        .ignoreDependency("com.github.dropguard.summer.realworld..", "..")
                        .ignoreDependency("com.github.dropguard.summer.benchmark..", "..")
                        .ignoreDependency("..", "com.github.dropguard.summer.realworld..")
                        .ignoreDependency("..", "com.github.dropguard.summer.benchmark..")
                        .ignoreDependency("..com.github.dropguard.summer.fixtures..", "..")
                        .ignoreDependency("..", "..com.github.dropguard.summer.fixtures..")
                        .ignoreDependency("..com.github.dropguard.summer.twitter..", "..")
                        .ignoreDependency("..", "..com.github.dropguard.summer.twitter..")
                        .ignoreDependency("..com.github.dropguard.summer.issue_tracker..", "..")
                        .ignoreDependency("..", "..com.github.dropguard.summer.issue_tracker..")

                        // layer definitions
                        .layer("Core")
                        .definedBy("..com.github.dropguard.summer.core..")
                        .layer("Infrastructure")
                        .definedBy(
                                "..com.github.dropguard.summer.runtime..",
                                "..com.github.dropguard.summer.engine..",
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
                                "..com.github.dropguard.summer.core.validation..")
                        .layer("Server")
                        .definedBy(
                                "..com.github.dropguard.summer.web.server..",
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

    @ArchTest
    @DisplayName("No circular dependencies between packages")
    void noCircularDependencies(JavaClasses classes) {
        ArchRule rule =
                com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
                        .matching("com.github.dropguard.summer.(*)")
                        .should()
                        .beFreeOfCycles()
                        .ignoreDependency("com.github.dropguard.summer.realworld..", "..")
                        .ignoreDependency("com.github.dropguard.summer.benchmark..", "..")
                        .ignoreDependency("..", "com.github.dropguard.summer.realworld..")
                        .ignoreDependency("..", "com.github.dropguard.summer.benchmark..")
                        .ignoreDependency("..com.github.dropguard.summer.fixtures..", "..")
                        .ignoreDependency("..", "..com.github.dropguard.summer.fixtures..")
                        .ignoreDependency("..com.github.dropguard.summer.twitter..", "..")
                        .ignoreDependency("..", "..com.github.dropguard.summer.twitter..")
                        .ignoreDependency("..com.github.dropguard.summer.issue_tracker..", "..")
                        .ignoreDependency("..", "..com.github.dropguard.summer.issue_tracker..");
        rule.check(classes);
    }

    @ArchTest
    @DisplayName("No ClassGraph dependency")
    void noClassGraphDependency(JavaClasses classes) {
        ArchRule rule =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("io.github.classgraph..")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    @ArchTest
    @DisplayName("No CGLIB dependency")
    void noCglibDependency(JavaClasses classes) {
        ArchRule rule =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("net.sf.cglib..", "org.springframework.cglib..")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    @ArchTest
    @DisplayName("No ByteBuddy dependency")
    void noByteBuddyDependency(JavaClasses classes) {
        ArchRule rule =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("net.bytebuddy..")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    // --- @Replaces usage rules ---

    @ArchTest
    @DisplayName("AOT engine must not depend on the runtime engine")
    void aotMustNotDependOnRuntime(JavaClasses classes) {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..com.github.dropguard.summer.aot..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..com.github.dropguard.summer.runtime..");
        rule.check(classes);
    }

    @ArchTest
    @DisplayName("Runtime engine must not depend on the AOT engine")
    void runtimeMustNotDependOnAot(JavaClasses classes) {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..com.github.dropguard.summer.runtime..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..com.github.dropguard.summer.aot..");
        rule.check(classes);
    }

    @ArchTest
    @DisplayName("Runtime engine must not depend on the web module (except the runtime-web bridge)")
    void runtimeMustNotDependOnWeb(JavaClasses classes) {
        // The 2.1 decoupling: summer-runtime is a pure DI engine. All web awareness
        // lives in the runtime-web bridge module (com.github.dropguard.summer.runtime.web),
        // which may depend on summer-web.
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..com.github.dropguard.summer.runtime..")
                        .and()
                        .resideOutsideOfPackages("..com.github.dropguard.summer.runtime.web..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..com.github.dropguard.summer.web..");
        rule.check(classes);
    }

    @ArchTest
    @DisplayName("@Replaces must be on @Configuration in framework packages (not plain @Component)")
    void replacesRequiresConfigurationInFramework(JavaClasses classes) {
        ArchRule rule =
                com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                        .that()
                        .areAnnotatedWith("com.github.dropguard.summer.core.annotation.Replaces")
                        .and()
                        .resideInAnyPackage(PRODUCTION)
                        .should()
                        .beAnnotatedWith(
                                "com.github.dropguard.summer.core.annotation.Configuration")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    // --- Framework component restrictions ---

    @ArchTest
    @DisplayName("Framework code must use @Configuration + @Bean, not @Component")
    void frameworkCodeMustUseConfigurationNotComponent(JavaClasses classes) {
        // Exclude annotation packages (meta-annotations like @RestController,
        // are @Component by design) and specific marker beans
        String[] annotationPackages = {
            "com.github.dropguard.summer.core.annotation..",
            "com.github.dropguard.summer.web.annotation.."
        };
        ArchRule rule =
                com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                        .that()
                        .resideInAnyPackage(PRODUCTION)
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

    // --- Library bans ---
    @ArchTest
    @DisplayName("Netty allocators must not be used outside EventLoop boundary classes")
    void restrictNettyAllocatorUsage(JavaClasses classes) {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(PRODUCTION)
                        .and()
                        .doNotHaveSimpleName("NettyHttpServerHandler")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("io.netty.buffer.ByteBufAllocator")
                        .allowEmptyShould(true);
        rule.check(classes);
    }


    @ArchTest
    @DisplayName("No ConcurrentHashMap usage in production code (class-level exceptions)")
    void noConcurrentHashMap(JavaClasses classes) {
        // Class-level whitelist, granted on a per-class basis after review:
        //  - GrpcChannelManager: single-connection-pool holder per target, called concurrently
        //    from client threads. CHM.computeIfAbsent guarantees one channel per key.
        //  - NettyWebSocketBroadcaster: per-room ChannelGroup map, mutated by concurrent websocket
        //    threads on join(). CHM.computeIfAbsent guarantees one group per room.
        // Keep this list minimal — a new class wanting CHM must be reviewed,
        // not silently allowed.
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(PRODUCTION)
                        .and()
                        .doNotHaveFullyQualifiedName(
                                "com.github.dropguard.summer.grpc.client.GrpcChannelManager")
                        .and()
                        .doNotHaveFullyQualifiedName(
                                "com.github.dropguard.summer.web.server.NettyWebSocketBroadcaster")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("java.util.concurrent.ConcurrentHashMap")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    @ArchTest
    @DisplayName("No LinkedHashMap usage in production code")
    void noLinkedHashMap(JavaClasses classes) {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(PRODUCTION)
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("java.util.LinkedHashMap")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    @ArchTest
    @DisplayName("No ServiceLoader usage in production code")
    void noServiceLoaderInProduction(JavaClasses classes) {
        // ServiceLoader discovery is permitted only at the framework's SPI discovery points:
        // core.spi (RouteRegistrarLoader), engine.spi (AotProductConstructors), and
        // ContainerEngines (the engine-layer ServiceLoader seam for ContainerEngine). Everywhere
        // else in production code it is banned.
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(PRODUCTION)
                        .and()
                        .haveSimpleNameNotContaining("ContainerEngines")
                        .and()
                        .resideOutsideOfPackages(
                                "..com.github.dropguard.summer.core.spi..",
                                "..com.github.dropguard.summer.engine.spi..")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("java.util.ServiceLoader")
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    // --- DTO Record constraints ---

    @ArchTest
    @DisplayName("@ConfigMapping must be on interface types")
    void configMappingRequiresInterface(JavaClasses classes) {
        ArchRule rule =
                com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                        .that()
                        .areAnnotatedWith("com.github.dropguard.summer.core.config.ConfigMapping")
                        .should()
                        .beInterfaces()
                        .allowEmptyShould(true);
        rule.check(classes);
    }

    @ArchTest
    @DisplayName("@RowModel must be on Record classes")
    void rowModelRequiresRecord(JavaClasses classes) {
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
