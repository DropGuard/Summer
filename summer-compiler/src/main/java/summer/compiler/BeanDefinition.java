package summer.compiler;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Compile-time metadata for a single bean discovered by the APT processor.
 * Captures everything needed to generate instantiation code in the AOT context.
 */
final class BeanDefinition {

	enum Kind {
		/** A regular @Component or @RestController bean. */
		COMPONENT,
		/** A @Configuration class (itself a bean, may contain @Bean methods). */
		CONFIGURATION,
		/** A bean produced by a @Bean method on a @Configuration class. */
		FACTORY_PRODUCT
	}

	/** What kind of bean this is. */
	final Kind kind;

	/**
	 * The type element representing this bean's class. For COMPONENT/CONFIGURATION:
	 * the annotated class itself. For FACTORY_PRODUCT: the type element of the
	 * return type.
	 */
	final TypeElement typeElement;

	/** Camel-case variable name used in the generated wire() method. */
	String variableName;

	// --- Constructor info (for COMPONENT / CONFIGURATION) ---

	/** The constructor to invoke (single public constructor convention). */
	ExecutableElement constructor;

	/** Constructor parameter types, in order. */
	List<TypeMirror> constructorParamTypes = new ArrayList<>();

	// --- Factory info (for FACTORY_PRODUCT) ---

	/** The @Configuration class that declares this factory method. */
	TypeElement configClass;

	/** The @Bean method to call. */
	ExecutableElement producerMethod;

	/** The resolved return type of the @Bean method. */
	TypeMirror producedType;

	/** Parameter types of the @Bean method (dependencies to inject). */
	List<TypeMirror> producerParamTypes = new ArrayList<>();

	// --- AOP metadata ---

	/** Whether this bean should be wrapped with a JDK dynamic proxy. */
	boolean needsProxy;

	/**
	 * Interceptor beans to apply (references into the same BeanDefinition list).
	 */
	List<BeanDefinition> interceptors = new ArrayList<>();

	/** Interfaces implemented by this bean (for JDK proxy creation). */
	List<TypeMirror> interfaces = new ArrayList<>();

	// --- Lifecycle ---

	/** Whether this bean (or produced type) implements AutoCloseable. */
	boolean isAutoCloseable;

	// --- Dependency resolution bookkeeping ---

	/** Resolved bean references for each dependency, set by DependencyResolver. */
	List<BeanDefinition> resolvedDependencies = new ArrayList<>();

	/**
	 * For FACTORY_PRODUCT beans, the resolved BeanDefinition of the @Configuration
	 * class.
	 */
	BeanDefinition configBeanDefinition;

	BeanDefinition(Kind kind, TypeElement typeElement) {
		this.kind = kind;
		this.typeElement = typeElement;
	}

	/** Qualified name of the bean's type. */
	String qualifiedName() {
		return typeElement.getQualifiedName().toString();
	}

	/** Simple name of the bean's type. */
	String simpleName() {
		return typeElement.getSimpleName().toString();
	}

	@Override
	public String toString() {
		return kind + ":" + qualifiedName();
	}
}
