mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.core.bean;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
@Internal
/**
mport com.github.dropguard.summer.core.Internal;
 * Bean discovered via {@code @ConfigMapping}. Bound from {@code application.yml} at runtime.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Enrichment fields ({@link #configPropertiesPrefix}, {@link #defaultValues}, {@link
mport com.github.dropguard.summer.core.Internal;
 * #fieldTypes}) are populated by the discovery phase immediately after construction.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class ConfigPropertiesBean extends BeanDefinition {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** YAML prefix for property binding. */
mport com.github.dropguard.summer.core.Internal;
    public String configPropertiesPrefix;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** field name → raw @WithDefault string. Populated by BeanDiscovery. */
mport com.github.dropguard.summer.core.Internal;
    public final Map<String, String> defaultValues = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** field name → target type (e.g. Integer.class, Boolean.class). */
mport com.github.dropguard.summer.core.Internal;
    public final Map<String, String> fieldTypes = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public ConfigPropertiesBean(String qualifiedName, String simpleName) {
mport com.github.dropguard.summer.core.Internal;
        super(qualifiedName, simpleName);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
