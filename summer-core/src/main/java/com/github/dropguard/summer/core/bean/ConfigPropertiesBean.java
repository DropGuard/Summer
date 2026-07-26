package com.github.dropguard.summer.core.bean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bean discovered via {@code @ConfigurationProperties}. Bound from
 * {@code application.yml} at runtime.
 *
 * <p>
 * Enrichment fields ({@link #configPropertiesPrefix}, {@link #defaultValues},
 * {@link #fieldTypes}) are populated by the discovery phase immediately after
 * construction.
 * </p>
 */
public final class ConfigPropertiesBean extends BeanDefinition {

	/** YAML prefix for property binding. */
	public String configPropertiesPrefix;

	/** field name → raw @DefaultValue string. Populated by BeanDiscovery. */
	public final Map<String, String> defaultValues = new LinkedHashMap<>();

	/** field name → target type (e.g. Integer.class, Boolean.class). */
	public final Map<String, String> fieldTypes = new LinkedHashMap<>();

	public ConfigPropertiesBean(String qualifiedName, String simpleName) {
		super(qualifiedName, simpleName);
	}
}
