package summer.tck.di.replaces.conditional;

/**
 * Marker class that is NOT registered as a component. Used as a
 * {@code @ConditionalOnBean} target to test negative conditions.
 */
public class NonExistentMarker {
}
