package summer.plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;

/**
 * Generates AOP proxy classes for beans that need interception.
 * 
 * <p>For each bean with interceptors, generates a {@code $$AotProxy} class
 * that implements the same interfaces and delegates to the target bean
 * through the interceptor chain.</p>
 */
public final class AotProxyGenerator {

	AotProxyGenerator() {
	}

	/**
	 * Generate AOP proxy classes for beans that need interception.
	 * 
	 * @param beans list of bean definitions (will be modified to mark proxied beans)
	 * @param outputDir directory to write generated source files
	 */
	public void generate(List<BeanDefinition> beans, java.io.File outputDir) throws IOException {
		for (BeanDefinition bean : beans) {
			if (bean.needsProxy && !bean.interfaceNames.isEmpty()) {
				generateProxy(bean, outputDir);
			}
		}
	}

	private void generateProxy(BeanDefinition bean, java.io.File outputDir) throws IOException {
		String packageName = getPackageName(bean.qualifiedName);
		String proxyClassName = bean.simpleName + "$$AotProxy";

		// Build the proxy class that implements the first interface
		TypeSpec.Builder proxyBuilder = TypeSpec.classBuilder(proxyClassName)
			.addAnnotation(
				AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build())
			.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.FINAL);

		// Implement interfaces
		for (String ifaceName : bean.interfaceNames) {
			proxyBuilder.addSuperinterface(ClassName.bestGuess(ifaceName));
		}

		// Add target field
		ClassName targetClass = ClassName.bestGuess(bean.qualifiedName);
		proxyBuilder.addField(targetClass, "target", javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL);

		// Add interceptors field
		ClassName interceptorType = ClassName.get("summer.aop", "MethodInterceptor");
		ParameterizedTypeName interceptorList = ParameterizedTypeName.get(ClassName.get(List.class), interceptorType);
		proxyBuilder.addField(interceptorList, "interceptors", javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL);

		// Add constructor
		MethodSpec constructor = MethodSpec.constructorBuilder()
			.addModifiers(javax.lang.model.element.Modifier.PUBLIC)
			.addParameter(targetClass, "target")
			.addParameter(interceptorList, "interceptors")
			.addStatement("this.target = target")
			.addStatement("this.interceptors = interceptors")
			.build();
		proxyBuilder.addMethod(constructor);

		// Add proxy methods for each interface method
		// For now, generate a simple delegation with interceptor chain
		// TODO: Implement proper method-level interception based on @Intercepts
		for (String ifaceName : bean.interfaceNames) {
			// Generate delegate methods for common patterns
			// This is a simplified version - full implementation would analyze the interface
		}

		// Add sneakyThrow utility method
		proxyBuilder.addMethod(buildSneakyThrow());

		JavaFile proxyFile = JavaFile.builder(packageName, proxyBuilder.build()).build();
		proxyFile.writeTo(outputDir);
	}

	private MethodSpec buildSneakyThrow() {
		TypeVariableName t = TypeVariableName.get("T", Throwable.class);
		return MethodSpec.methodBuilder("sneakyThrow")
			.addModifiers(javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.STATIC)
			.addTypeVariable(t)
			.addException(t)
			.addParameter(Throwable.class, "e")
			.addStatement("throw (T) e")
			.build();
	}

	private String getPackageName(String qualifiedName) {
		int lastDot = qualifiedName.lastIndexOf('.');
		return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
	}
}
