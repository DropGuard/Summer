package summer.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified bean definition for Maven plugin. Uses strings for type information
 * to avoid dependencies on TypeElement or ClassInfo.
 */
public final class BeanDefinition {

	public enum Kind {
		COMPONENT, CONFIGURATION, FACTORY_PRODUCT, CONFIG_PROPERTIES
	}

	public final Kind kind;
	public final String qualifiedName;
	public final String simpleName;

	public String variableName;

	// Constructor info
	public final List<String> constructorParamTypes = new ArrayList<>();
	// Generic type info for List<T> parameters: maps index to element type
	public final Map<Integer, String> listElementTypes = new HashMap<>();

	// Factory info (only for FACTORY_PRODUCT)
	public String configClassName;
	public String producerMethodName;
	public final List<String> producerParamTypes = new ArrayList<>();

	// Interfaces (for dependency resolution and AOP)
	public final List<String> interfaceNames = new ArrayList<>();

	// AOP
	boolean needsProxy;
	final List<BeanDefinition> interceptors = new ArrayList<>();

	// Routes (for @RestController beans)
	public final List<RouteInfo> routes = new ArrayList<>();

	// Lifecycle
	public boolean isAutoCloseable;

	// @Replaces (method-level)
	public String replacesReturnType; // The return type to replace (e.g., "javax.sql.DataSource")
	public String replacesTargetClass; // Optional: explicit target class for method-level @Replaces

	// @ConfigurationProperties
	public String configPropertiesPrefix;

	// Dependency resolution
	public final List<BeanDefinition> resolvedDependencies = new ArrayList<>();
	public BeanDefinition configBeanDefinition;

	public BeanDefinition(Kind kind, String qualifiedName, String simpleName) {
		this.kind = kind;
		this.qualifiedName = qualifiedName;
		this.simpleName = simpleName;
		this.variableName = toVariableName(simpleName);
	}

	private static String toVariableName(String simpleName) {
		if (simpleName.isEmpty()) {
			return "bean";
		}
		return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
	}

	@Override
	public String toString() {
		return kind + ":" + qualifiedName;
	}
}
