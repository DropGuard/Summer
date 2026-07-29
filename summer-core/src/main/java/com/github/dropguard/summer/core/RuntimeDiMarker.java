package com.github.dropguard.summer.core;

/**
 * Marker bean that signals the reflection-based DI engine is active.
 *
 * <p>This is a framework infrastructure bean registered programmatically by {@code
 * RuntimeContainer}'s constructor. It is NOT annotated with {@code @Component} because framework
 * code must use {@code @Configuration + @Bean} instead.
 *
 * <p>Downstream configurations use {@code @ConditionalOnBean(RuntimeDiMarker.class)} to activate
 * only when the runtime/reflection engine is in use, as opposed to AOT.
 */
public class RuntimeDiMarker {}
