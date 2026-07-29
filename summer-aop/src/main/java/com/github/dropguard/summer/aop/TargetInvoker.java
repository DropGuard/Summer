mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.aop;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
@Internal
 * Functional interface used to execute the target method without reflection. This allows the
mport com.github.dropguard.summer.core.Internal;
 * DefaultInvocationContext to be 100% reflection-free during invocation.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
@FunctionalInterface
mport com.github.dropguard.summer.core.Internal;
public interface TargetInvoker {
mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Executes the target method.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @return the result of the method execution
mport com.github.dropguard.summer.core.Internal;
     * @throws Throwable if the target method throws any exception
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    Object invoke() throws Throwable;
mport com.github.dropguard.summer.core.Internal;
}
