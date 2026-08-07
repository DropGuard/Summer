package com.github.dropguard.summer.core.config;

import com.github.dropguard.summer.core.Engine;

/**
 * Bootstrap configuration for the Summer framework itself (as opposed to user application config).
 *
 * <p>The DI engine is the one framework-level decision that must be made before the container can
 * be built. It is resolved from {@code application.yml} under the {@code summer} prefix, with a
 * safe default of {@link Engine#RUNTIME} so a bare project (no {@code application.yml} at all)
 * still starts. Production builds flip this to {@link Engine#AOT} at build time via {@code
 * summer-maven-plugin}, and {@code -Dsummer.engine} overrides it on the command line for debugging.
 * No environment sniffing (debugger attach, stack-frame location, IDE variables) is used — the
 * engine is always an explicit, auditable choice.
 */
@ConfigMapping(prefix = "summer")
public interface FrameworkConfig {

    /**
     * The {@code @WithDefault("runtime")} value as an {@link Engine} — the single source for the
     * boot layer's pre-container engine resolution ({@code SummerApplication} is reflection-free,
     * so it cannot read the annotation itself). The annotation is a compile-time literal and cannot
     * reference this constant, so keep the two in lock-step here in one file.
     */
    Engine FALLBACK_ENGINE = Engine.RUNTIME;

    /** DI engine used to build the container. Defaults to RUNTIME; production builds set AOT. */
    @WithDefault("runtime")
    Engine engine();
}
