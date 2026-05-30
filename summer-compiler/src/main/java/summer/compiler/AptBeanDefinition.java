package summer.compiler;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Bean discovered via annotation processing. Uses javax.lang.model APIs for
 * type information.
 */
final class AptBeanDefinition {

	enum Kind {
		COMPONENT, CONFIGURATION, FACTORY_PRODUCT
	}

	final Kind kind;
	final TypeElement typeElement;

	String variableName;

	// Constructor info
	ExecutableElement constructor;
	final List<TypeMirror> constructorParamTypeMirrors = new ArrayList<>();

	// Factory info
	TypeElement configClass;
	ExecutableElement producerMethod;
	TypeMirror producedType;
	final List<TypeMirror> producerParamTypeMirrors = new ArrayList<>();

	// AOP
	boolean needsProxy;
	final List<AptBeanDefinition> interceptors = new ArrayList<>();
	final List<TypeMirror> interfaceMirrors = new ArrayList<>();

	// Lifecycle
	boolean isAutoCloseable;

	// Dependency resolution
	final List<AptBeanDefinition> resolvedDependencies = new ArrayList<>();
	AptBeanDefinition configBeanDefinition;

	AptBeanDefinition(Kind kind, TypeElement typeElement) {
		this.kind = kind;
		this.typeElement = typeElement;
	}

	String qualifiedName() {
		return typeElement.getQualifiedName().toString();
	}

	String simpleName() {
		return typeElement.getSimpleName().toString();
	}

	List<String> constructorParamTypes() {
		return constructorParamTypeMirrors.stream().map(TypeMirror::toString).toList();
	}

	List<String> producerParamTypes() {
		return producerParamTypeMirrors.stream().map(TypeMirror::toString).toList();
	}

	List<String> interfaceNames() {
		return interfaceMirrors.stream().map(TypeMirror::toString).toList();
	}

	String superClassName() {
		TypeMirror superclass = typeElement.getSuperclass();
		if (superclass == null || superclass.toString().equals("java.lang.Object")) {
			return null;
		}
		return superclass.toString();
	}

	String configClassName() {
		return configClass != null ? configClass.getQualifiedName().toString() : null;
	}

	@Override
	public String toString() {
		return kind + ":" + qualifiedName();
	}
}
