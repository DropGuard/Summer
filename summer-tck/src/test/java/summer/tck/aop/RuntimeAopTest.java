package summer.tck.aop;

/**
 * Runs the full AOP TCK against the Runtime (reflection + JDK proxy) engine.
 * Expected: ALL tests pass immediately, since RuntimeBeanContainerBuilder
 * already applies AOP proxies during bean instantiation.
 */
public class RuntimeAopTest extends AbstractAopTCK {
}
