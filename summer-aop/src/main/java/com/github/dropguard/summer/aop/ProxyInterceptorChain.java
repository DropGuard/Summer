mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.aop;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;

@Internal
mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Default implementation of {@link InterceptorChain}. Holds invocation data and executes the
mport com.github.dropguard.summer.core.Internal;
 * interceptor chain.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class ProxyInterceptorChain implements InterceptorChain {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final Object target;
mport com.github.dropguard.summer.core.Internal;
    private final InterceptedMethod targetMethod;
mport com.github.dropguard.summer.core.Internal;
    private final Object[] args;
mport com.github.dropguard.summer.core.Internal;
    private final List<MethodInterceptor> interceptors;
mport com.github.dropguard.summer.core.Internal;
    private final TargetInvoker targetInvoker;
mport com.github.dropguard.summer.core.Internal;
    private int currentIndex = -1;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public ProxyInterceptorChain(
mport com.github.dropguard.summer.core.Internal;
            Object target,
mport com.github.dropguard.summer.core.Internal;
            InterceptedMethod targetMethod,
mport com.github.dropguard.summer.core.Internal;
            Object[] args,
mport com.github.dropguard.summer.core.Internal;
            List<MethodInterceptor> interceptors,
mport com.github.dropguard.summer.core.Internal;
            TargetInvoker targetInvoker) {
mport com.github.dropguard.summer.core.Internal;
        this.target = target;
mport com.github.dropguard.summer.core.Internal;
        this.targetMethod = targetMethod;
mport com.github.dropguard.summer.core.Internal;
        this.args = args;
mport com.github.dropguard.summer.core.Internal;
        this.interceptors = interceptors;
mport com.github.dropguard.summer.core.Internal;
        this.targetInvoker = targetInvoker;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object getTarget() {
mport com.github.dropguard.summer.core.Internal;
        return target;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public InterceptedMethod method() {
mport com.github.dropguard.summer.core.Internal;
        return targetMethod;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object[] getArguments() {
mport com.github.dropguard.summer.core.Internal;
        return args;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object proceed() throws Throwable {
mport com.github.dropguard.summer.core.Internal;
        currentIndex++;
mport com.github.dropguard.summer.core.Internal;
        if (currentIndex < interceptors.size()) {
mport com.github.dropguard.summer.core.Internal;
            MethodInterceptor interceptor = interceptors.get(currentIndex);
mport com.github.dropguard.summer.core.Internal;
            return interceptor.intercept(this);
mport com.github.dropguard.summer.core.Internal;
        } else {
mport com.github.dropguard.summer.core.Internal;
            return targetInvoker.invoke();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
