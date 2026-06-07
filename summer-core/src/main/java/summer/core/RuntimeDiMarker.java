package summer.core;

/**
 * Marker bean that signals the reflection-based DI engine is active.
 *
 * <p>
 * This is a framework infrastructure bean registered programmatically by
 * {@code RuntimeApplicationContext.create()}. It is NOT annotated with
 * {@code @Component} because framework code must use
 * {@code @Configuration + @Bean} instead.
 * </p>
 *
 * <p>
 * Downstream configurations use
 * {@code @ConditionalOnBean(RuntimeDiMarker.class)} to activate only when the
 * runtime/reflection engine is in use, as opposed to AOT.
 * </p>
 */
public class RuntimeDiMarker {
}
