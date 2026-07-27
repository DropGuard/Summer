package com.github.dropguard.summer.core;

/**
 * Marker bean that signals the AOT-based DI engine is active.
 *
 * <p>This is a framework infrastructure bean registered by the AOT-generated {@code
 * GeneratedAotContext} (or {@code AppBootstrap}) during the {@code wire()} phase. It is NOT
 * annotated with {@code @Component} because framework code generates it programmatically.
 *
 * <p>Downstream configurations use {@code @ConditionalOnBean(AotDiMarker.class)} to activate only
 * when the AOT engine is in use, as opposed to the runtime/reflection engine.
 */
public class AotDiMarker {}
