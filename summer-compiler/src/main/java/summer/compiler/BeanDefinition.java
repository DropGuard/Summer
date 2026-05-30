package summer.compiler;

import java.util.List;

/**
 * Abstraction over a discovered bean, hiding its source (annotation processor
 * vs Jandex index).
 */
interface BeanDefinition {

	enum Kind {
		COMPONENT, CONFIGURATION, FACTORY_PRODUCT
	}

	Kind kind();

	/** Fully qualified name of the bean's type. */
	String qualifiedName();

	/** Simple name of the bean's type. */
	String simpleName();

	/** Camel-case variable name used in the generated wire() method. */
	String variableName();

	void setVariableName(String name);

	// --- Constructor / producer params (qualified names) ---

	List<String> constructorParamTypes();

	List<String> producerParamTypes();

	// --- Interfaces (qualified names) ---

	List<String> interfaceNames();

	// --- Class hierarchy ---

	/** Qualified name of the superclass, or null if Object. */
	String superClassName();

	// --- Factory info ---

	/** For FACTORY_PRODUCT: qualified name of the @Configuration class. */
	String configClassName();

	// --- AOP ---

	boolean needsProxy();

	void setNeedsProxy(boolean needsProxy);

	List<BeanDefinition> interceptors();

	// --- Dependency resolution (set by DependencyResolver) ---

	List<BeanDefinition> resolvedDependencies();

	BeanDefinition configBeanDefinition();

	void setConfigBeanDefinition(BeanDefinition config);

	// --- Lifecycle ---

	boolean isAutoCloseable();

	void setAutoCloseable(boolean autoCloseable);
}
