package com.github.dropguard.summer.core.bean;

import com.github.dropguard.summer.core.Internal;

/**
 * Bean discovered via {@code @ConfigMapping}. Bound from {@code application.yml} at runtime.
 *
 * <p>{@link #configPropertiesPrefix} is populated by the discovery phase; {@code @WithDefault} /
 * {@code @WithName} resolution happens in the consumers (the AOT generator and the runtime config
 * binder) rather than being pre-extracted here.
 */
@Internal
public final class ConfigPropertiesBean extends BeanDefinition {

    /** YAML prefix for property binding. */
    public String configPropertiesPrefix;

    public ConfigPropertiesBean(String qualifiedName, String simpleName) {
        super(qualifiedName, simpleName);
    }
}
