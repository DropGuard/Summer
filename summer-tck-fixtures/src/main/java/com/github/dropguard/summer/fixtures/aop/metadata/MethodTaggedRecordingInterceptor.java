package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.aop.Interceptor;
import com.github.dropguard.summer.aop.InterceptorChain;
import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.core.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata-driven interceptor for the {@code @MetadataTagged} binding — the method-level twin of
 * {@link MetadataRecordingInterceptor}. Same decision shape as TransactionInterceptor: it only
 * records when {@code InterceptedMethod.isAnnotationPresent} reports the binding, so it is
 * sensitive to metadata materialisation, not merely to chain routing.
 */
@Component
@Interceptor
@MetadataTagged
public class MethodTaggedRecordingInterceptor implements MethodInterceptor {

    private final List<String> callLog = new ArrayList<>();

    @Override
    public Object intercept(InterceptorChain chain) throws Throwable {
        if (chain.method().isAnnotationPresent(MetadataTagged.class)) {
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
