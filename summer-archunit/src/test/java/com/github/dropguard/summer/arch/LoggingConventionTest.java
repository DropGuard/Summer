package com.github.dropguard.summer.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Logging convention for the Summer framework.
 *
 * <p>Production code must route diagnostics through the logging facade (SLF4J) rather than writing
 * directly to the console. This keeps a single, configurable output channel — the deployer owns the
 * logging backend and aggregation (Logback / Log4j / Loki / cloud logging), exactly as they own
 * health probes and graceful shutdown. Direct {@code System.out}/{@code System.err} writes and
 * {@code Throwable.printStackTrace()} bypass that channel, so they are banned.
 *
 * <p>The sole exception is {@code SummerApplication}, whose startup banner is printed to {@code
 * System.out} by design — a deliberate, class-scoped carve-out (ArchUnit predicates cannot target a
 * single line, only a class).
 */
class LoggingConventionTest {

    // Framework packages only — demos (summer-issue-tracker/realworld/twitter)
    // and fixtures are intentionally excluded; they are applications, not the
    // framework, and are free to print.
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
        classes =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(PACKAGES);
    }

    @Test
    @DisplayName(
            "No direct console writes (System.out / System.err / printStackTrace) outside"
                    + " SummerApplication")
    void noConsoleWrites() {
        // @formatter:off
        noClasses()
                .that()
                .doNotHaveSimpleName("SummerApplication")
                .should()
                .accessField(System.class, "out")
                .orShould()
                .accessField(System.class, "err")
                .orShould()
                .callMethod(Throwable.class, "printStackTrace")
                .check(classes);
        // @formatter:on
    }
}
