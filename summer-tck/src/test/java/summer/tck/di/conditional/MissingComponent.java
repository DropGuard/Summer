package summer.tck.di.conditional;

/**
 * A component that is NOT registered in the context.
 * Used to test that @ConditionalOnBean correctly skips beans when the condition is not met.
 */
public class MissingComponent {
}
