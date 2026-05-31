package summer.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified bean definition that supports both APT and Jandex sources. Uses
 * strings for type information to avoid dependencies on TypeElement or
 * ClassInfo.
 */
final class BeanDefinition {

	enum Kind {
		COMPONENT, CONFIGURATION, FACTORY_PRODUCT
	}

	final Kind kind;
	final String qualifiedName;
	final String simpleName;

	String variableName;

	// Constructor info
	final List<String> constructorParamTypes = new ArrayList<>();

	// Factory info (only for FACTORY_PRODUCT)
	String configClassName;
	String producerMethodName;
	final List<String> producerParamTypes = new ArrayList<>();

	// Interfaces (for dependency resolution and AOP)
	final List<String> interfaceNames = new ArrayList<>();

	// AOP
	boolean needsProxy;
	final List<BeanDefinition> interceptors = new ArrayList<>();

	// Lifecycle
	boolean isAutoCloseable;

	// Dependency resolution
	final List<BeanDefinition> resolvedDependencies = new ArrayList<>();
	BeanDefinition configBeanDefinition;

	BeanDefinition(Kind kind, String qualifiedName, String simpleName) {
		this.kind = kind;
		this.qualifiedName = qualifiedName;
		this.simpleName = simpleName;
	}

	@Override
	public String toString() {
		return kind + ":" + qualifiedName;
	}
}
