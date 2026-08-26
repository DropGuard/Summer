package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.aop.Interceptor;
import com.github.dropguard.summer.aop.InterceptorChain;
import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.core.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An interceptor that decides purely from {@code InterceptedMethod.isAnnotationPresent} whether a
 * call is in scope — the same decision shape as {@code TransactionInterceptor}.
 *
 * <p>This makes the fixture sensitive to binding <em>metadata</em>, not just to chain routing: an
 * engine may correctly route every class-level-bound method through the chain yet still hand the
 * interceptor an empty annotation set, in which case nothing is recorded. That is exactly the
 * divergence this fixture exists to catch.
 */
@Component
@Interceptor
@ClassMetadataTagged
public class MetadataRecordingInterceptor implements MethodInterceptor {

    private final List<String> callLog = new ArrayList<>();

    @Override
    public Object intercept(InterceptorChain chain) throws Throwable {
        if (chain.method().isAnnotationPresent(ClassMetadataTagged.class)) {
            callLog.add("record:" + chain.method().name());
        }
        return chain.proceed();
    }

    public List<String> getCallLog() {
        return Collections.unmodifiableList(callLog);
    }

    public void clearLog() {
        callLog.clear();
    }
}
