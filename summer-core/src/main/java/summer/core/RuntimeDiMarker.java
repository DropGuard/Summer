package summer.core;

/**
 * Marker bean that signals the reflection-based DI engine is active.
 *
 * <p>
 * Registered by {@code RuntimeDiEngine} during bootstrap. Downstream components
 * (e.g. {@code MapRouter}) use
 * {@code @ConditionalOnBean(RuntimeDiMarker.class)} to activate only when the
 * runtime/reflection engine is in use, as opposed to AOT.
 * </p>
 */
@Component
public class RuntimeDiMarker {
}
