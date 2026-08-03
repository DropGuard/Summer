package com.github.dropguard.summer.core.bean;

import java.util.HashMap;
import java.util.Map;

/**
 * Bean discovered via {@code @ConfigMapping}. Bound from {@code application.yml} at runtime.
 *
 * <p>Enrichment fields ({@link #configPropertiesPrefix}, {@link #defaultValues}, {@link
 * #fieldTypes}) are populated by the discovery phase immediately after construction.
 */
public final class ConfigPropertiesBean extends BeanDefinition {

    /** YAML prefix for property binding. */
    public String configPropertiesPrefix;

    /** field name → raw @WithDefault string. Populated by BeanDiscovery. */
    public final Map<String, String> defaultValues = new HashMap<>();

    /** field name → target type (e.g. Integer.class, Boolean.class). */
    public final Map<String, String> fieldTypes = new HashMap<>();

    public ConfigPropertiesBean(String qualifiedName, String simpleName) {
        super(qualifiedName, simpleName);
    }
}
