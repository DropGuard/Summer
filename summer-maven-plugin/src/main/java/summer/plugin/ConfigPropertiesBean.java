package summer.plugin;

/**
 * Bean discovered via {@code @ConfigurationProperties}. Bound from
 * {@code application.yml} at runtime.
 */
public final class ConfigPropertiesBean extends BeanDefinition {

	public String configPropertiesPrefix;

	public ConfigPropertiesBean(String qualifiedName, String simpleName) {
		super(qualifiedName, simpleName);
	}
}
