package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import java.util.List;
import java.util.Map;

/**
 * Pre-computed exception handler metadata, registered as a synthetic bean during container build so
 * {@link RuntimeExceptionHandlerRegistrar} can receive it via constructor injection.
 */
@Internal
public final class HandlerMetadata {

    private final Map<String, List<BeanDefinition.ExceptionHandlerEntry>> entries;

    public HandlerMetadata(Map<String, List<BeanDefinition.ExceptionHandlerEntry>> entries) {
        this.entries = Map.copyOf(entries);
    }

    public Map<String, List<BeanDefinition.ExceptionHandlerEntry>> entries() {
        return entries;
    }
}
