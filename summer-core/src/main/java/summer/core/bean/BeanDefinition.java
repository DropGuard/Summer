package summer.core.bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean definition — the single source of metadata for every bean in the
 * container.
 *
 * <p>
 * Covers both {@code @Component} (constructor injection) and {@code @Bean}
 * (factory method) paths. {@link ConfigPropertiesBean} extends this for
 * YAML-bound configuration beans.
 * </p>
 */
public sealed class BeanDefinition permits ConfigPropertiesBean {

	public final String qualifiedName;
	public final String simpleName;
	public String variableName;

	// Routes (for @RestController beans)
	public final List<RouteInfo> routes = new ArrayList<>();

	// @Replaces
	public String replacesReturnType;
	public String replacesTargetClass;

	// Dependency resolution
	public final List<BeanDefinition> resolvedDependencies = new ArrayList<>();
	public BeanDefinition configBeanDefinition;

	// Constructor injection fields (@Component path)
	public final List<String> constructorParamTypes = new ArrayList<>();
	public final Map<Integer, String> listElementTypes = new HashMap<>();

	// Factory method fields (@Bean path, null = constructor-created)
	public String configClassName;
	public String producerMethodName;
	public final List<String> producerParamTypes = new ArrayList<>();

	// Interface and AOP metadata
	public final List<String> interfaceNames = new ArrayList<>();
	public boolean needsProxy;
	public final List<BeanDefinition> interceptors = new ArrayList<>();
	public boolean isAutoCloseable;

	public BeanDefinition(String qualifiedName, String simpleName) {
		this.qualifiedName = qualifiedName;
		this.simpleName = simpleName;
		this.variableName = toVariableName(simpleName);
	}

	/**
	 * Returns {@code true} if this bean is produced by a {@code @Bean} factory
	 * method.
	 */
	public boolean isFactoryMethod() {
		return configClassName != null;
	}

	private static String toVariableName(String simpleName) {
		if (simpleName.isEmpty()) {
			return "bean";
		}
		return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ":" + qualifiedName;
	}
}
