package summer.compiler;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Bean discovered via annotation processing (application beans). Uses
 * javax.lang.model APIs for type information.
 */
final class AptBeanDefinition implements BeanDefinition {

	final Kind kind;
	final TypeElement typeElement;

	private String variableName;

	// Constructor info
	ExecutableElement constructor;
	final List<TypeMirror> constructorParamTypeMirrors = new ArrayList<>();

	// Factory info
	TypeElement configClass;
	ExecutableElement producerMethod;
	TypeMirror producedType;
	final List<TypeMirror> producerParamTypeMirrors = new ArrayList<>();

	// AOP
	private boolean needsProxy;
	final List<BeanDefinition> interceptors = new ArrayList<>();
	final List<TypeMirror> interfaceMirrors = new ArrayList<>();

	// Lifecycle
	boolean isAutoCloseable;

	// Dependency resolution
	final List<BeanDefinition> resolvedDependencies = new ArrayList<>();
	BeanDefinition configBeanDefinition;

	AptBeanDefinition(Kind kind, TypeElement typeElement) {
		this.kind = kind;
		this.typeElement = typeElement;
	}

	@Override
	public Kind kind() {
		return kind;
	}
	@Override
	public String qualifiedName() {
		return typeElement.getQualifiedName().toString();
	}
	@Override
	public String simpleName() {
		return typeElement.getSimpleName().toString();
	}
	@Override
	public String variableName() {
		return variableName;
	}
	@Override
	public void setVariableName(String name) {
		this.variableName = name;
	}

	@Override
	public List<String> constructorParamTypes() {
		return constructorParamTypeMirrors.stream().map(TypeMirror::toString).toList();
	}

	@Override
	public List<String> producerParamTypes() {
		return producerParamTypeMirrors.stream().map(TypeMirror::toString).toList();
	}

	@Override
	public List<String> interfaceNames() {
		return interfaceMirrors.stream().map(TypeMirror::toString).toList();
	}

	@Override
	public String superClassName() {
		TypeMirror superclass = typeElement.getSuperclass();
		if (superclass == null || superclass.toString().equals("java.lang.Object")) {
			return null;
		}
		return superclass.toString();
	}

	@Override
	public String configClassName() {
		return configClass != null ? configClass.getQualifiedName().toString() : null;
	}

	@Override
	public boolean needsProxy() {
		return needsProxy;
	}
	@Override
	public void setNeedsProxy(boolean needsProxy) {
		this.needsProxy = needsProxy;
	}
	@Override
	public List<BeanDefinition> interceptors() {
		return interceptors;
	}
	@Override
	public List<BeanDefinition> resolvedDependencies() {
		return resolvedDependencies;
	}
	@Override
	public BeanDefinition configBeanDefinition() {
		return configBeanDefinition;
	}
	@Override
	public void setConfigBeanDefinition(BeanDefinition config) {
		this.configBeanDefinition = config;
	}
	@Override
	public boolean isAutoCloseable() {
		return isAutoCloseable;
	}
	@Override
	public void setAutoCloseable(boolean autoCloseable) {
		this.isAutoCloseable = autoCloseable;
	}

	@Override
	public String toString() {
		return kind + ":" + qualifiedName();
	}
}
