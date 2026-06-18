package summer.aot.generator;

import com.palantir.javapoet.CodeBlock;
import java.util.ArrayList;
import java.util.List;

/**
 * Sealed bean definition hierarchy. Each {@link Kind} maps to a concrete
 * subclass with only the fields it needs.
 *
 * <p>
 * Shared fields live here; kind-specific fields live in subclasses.
 * </p>
 */
public sealed class BeanDefinition permits ComponentBean, FactoryBean, ConfigPropertiesBean {

	public final String qualifiedName;
	public final String simpleName;
	public String variableName;
	public String moduleId;

	// Routes (for @RestController beans)
	public final List<RouteInfo> routes = new ArrayList<>();

	// Interfaces implemented by this bean's type
	public final List<String> interfaceNames = new ArrayList<>();

	// @Replaces
	public String replacesReturnType;
	public String replacesTargetClass;

	// @ConditionalOnBean (type name, null if not conditional)
	public String conditionalOnTypeName;

	// Dependency resolution
	public final List<BeanDefinition> resolvedDependencies = new ArrayList<>();
	public final List<CodeBlock> argExpressions = new ArrayList<>();
	public BeanDefinition configBeanDefinition;

	protected BeanDefinition(String qualifiedName, String simpleName) {
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
		return getClass().getSimpleName() + ":" + qualifiedName;
	}
}
