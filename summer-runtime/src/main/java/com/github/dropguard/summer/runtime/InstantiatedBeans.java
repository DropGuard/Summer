package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What each bean actually IS in this container: qualified name → the instance it was instantiated
 * as.
 *
 * <p>{@code BeanInstantiator} records one entry per bean right after instantiation: the proxy for
 * an AOP-bound bean (its only legal form under the one-bean-one-form contract), the instance itself
 * for an unbound bean. Framework registration hooks that need the bean itself — exception handler
 * registration, route registration — read from here instead of calling {@code getBean}, which by
 * contract fails loudly on AOP-bound concrete types and would otherwise hand registration a second
 * form.
 *
 * <p>Also carries the discovery-time exception-handler metadata (the former {@code
 * HandlerMetadata}'s job); the instance map is filled by the instantiation loop that follows.
 */
@Internal
public final class InstantiatedBeans {

    private final Map<String, List<BeanDefinition.ExceptionHandlerEntry>> exceptionHandlerEntries;
    private final Map<String, Object> instances = new HashMap<>();

    public InstantiatedBeans(
            Map<String, List<BeanDefinition.ExceptionHandlerEntry>> exceptionHandlerEntries) {
        this.exceptionHandlerEntries = Map.copyOf(exceptionHandlerEntries);
    }

    /** Records the form a bean was instantiated as. Called by the instantiation plane only. */
    public void record(String qualifiedName, Object instance) {
        instances.put(qualifiedName, instance);
    }

    /**
     * Returns the object a bean was instantiated as. A miss is metadata drift (present in
     * discovery, never instantiated) and fails fast.
     */
    public Object instanceOf(String qualifiedName) {
        Object instance = instances.get(qualifiedName);
        if (instance == null) {
            throw new BeanCreationException(
                    "Bean "
                            + qualifiedName
                            + " was recorded at discovery but never instantiated — stale"
                            + " index or metadata drift.");
        }
        return instance;
    }

    public Map<String, List<BeanDefinition.ExceptionHandlerEntry>> exceptionHandlerEntries() {
        return exceptionHandlerEntries;
    }
}
