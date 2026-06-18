package summer.plugin;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;

/**
 * Generates AOP proxy classes for beans that need interception.
 *
 * <p>
 * For each bean with interceptors, generates a {@code $$AotProxy} class that
 * implements the same interfaces and delegates to the target bean through the
 * interceptor chain.
 * </p>
 */
public final class AotProxyGenerator {

	private static final ClassName PROXY_CHAIN = ClassName.get("summer.aop", "ProxyInterceptorChain");
	private static final ClassName RUNTIME_METADATA = ClassName.get("summer.runtime", "RuntimeMethodMetadata");
	private static final ClassName INVOCATION_TARGET_EX = ClassName.get("java.lang.reflect",
			"InvocationTargetException");

	AotProxyGenerator() {
	}

	/**
	 * Generate AOP proxy classes for beans that need interception.
	 *
	 * @param beans
	 *            list of bean definitions (will be modified to mark proxied beans)
	 * @param index
	 *            Jandex index for looking up interface method signatures
	 * @param outputDir
	 *            directory to write generated source files
	 */
	public void generate(List<BeanDefinition> beans, IndexView index, java.io.File outputDir) throws IOException {
		// Collect all binding annotations from interceptor beans
		Set<DotName> allBindingAnnotations = new HashSet<>();
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() && ci.hasAnnotation(DotName.createSimple("summer.aop.InterceptorBinding"))) {
				allBindingAnnotations.add(ci.name());
			}
		}

		for (BeanDefinition bean : beans) {
			if (bean instanceof ComponentBean cb && cb.needsProxy && !cb.interfaceNames.isEmpty()) {
				generateProxy(cb, index, outputDir, allBindingAnnotations);
			}
		}
	}
	private void generateProxy(ComponentBean bean, IndexView index, java.io.File outputDir,
			Set<DotName> bindingAnnotations) throws IOException {
		String packageName = getPackageName(bean.qualifiedName);
		String proxyClassName = bean.simpleName + "$$AotProxy";

		TypeSpec.Builder proxyBuilder = TypeSpec.classBuilder(proxyClassName)
				.addAnnotation(
						AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build())
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL);

// Implement interfaces
            for (String ifaceName : bean.interfaceNames) {
                proxyBuilder.addSuperinterface(safeClassName(ifaceName));
            }

            // Add target field
            ClassName targetClass = safeClassName(bean.qualifiedName);
		proxyBuilder.addField(targetClass, "target", Modifier.PRIVATE, Modifier.FINAL);

		// Add interceptors field
		ClassName interceptorType = ClassName.get("summer.aop", "MethodInterceptor");
		ParameterizedTypeName interceptorList = ParameterizedTypeName.get(ClassName.get(List.class), interceptorType);
		proxyBuilder.addField(interceptorList, "interceptors", Modifier.PRIVATE, Modifier.FINAL);

		// Add constructor
		MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
				.addParameter(targetClass, "target").addParameter(interceptorList, "interceptors")
				.addStatement("this.target = target").addStatement("this.interceptors = interceptors").build();
		proxyBuilder.addMethod(constructor);

		// Determine if class-level binding applies (all methods intercepted)
		ClassInfo targetCi = index.getClassByName(DotName.createSimple(bean.qualifiedName));
		boolean classLevelBinding = false;
		if (targetCi != null) {
			for (AnnotationInstance ann : targetCi.declaredAnnotations()) {
				if (bindingAnnotations.contains(ann.name())) {
					classLevelBinding = true;
					break;
				}
			}
		}

		// Collect method-level binding annotations (for per-method filtering)
		Set<String> methodLevelBindingMethods = new HashSet<>();
		if (!classLevelBinding && targetCi != null) {
			for (MethodInfo method : targetCi.methods()) {
				for (AnnotationInstance ann : method.annotations()) {
					if (bindingAnnotations.contains(ann.name())) {
						methodLevelBindingMethods.add(method.name());
						break;
					}
				}
			}
		}

		// Generate proxy methods for each interface
		for (String ifaceName : bean.interfaceNames) {
			ClassInfo ifaceCi = index.getClassByName(DotName.createSimple(ifaceName));
			if (ifaceCi == null)
				continue;

			for (MethodInfo method : ifaceCi.methods()) {
				if (!method.isAbstract())
					continue;

				boolean shouldIntercept = classLevelBinding || methodLevelBindingMethods.contains(method.name());
				if (shouldIntercept) {
					proxyBuilder.addMethod(buildProxyMethod(method));
				} else {
					proxyBuilder.addMethod(buildDirectDelegate(method));
				}
			}
		}

		// Add sneakyThrow utility
		proxyBuilder.addMethod(buildSneakyThrow());

		JavaFile proxyFile = JavaFile.builder(packageName, proxyBuilder.build()).indent("    ").build();
		proxyFile.writeTo(outputDir);
	}

	/**
	 * Builds a proxy method that delegates through the interceptor chain.
	 */
	private MethodSpec buildProxyMethod(MethodInfo method) {
		MethodSpec.Builder builder = MethodSpec.methodBuilder(method.name()).addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC);

		// Return type
		TypeName returnType = toTypeName(method.returnType());
		builder.returns(returnType);

		// Parameters — names may be null if compiled without -parameters
		List<String> paramNames = new java.util.ArrayList<>();
		int paramIdx = 0;
		for (MethodParameterInfo param : method.parameters()) {
			String name = param.name();
			if (name == null) {
				name = "arg" + paramIdx;
			}
			paramNames.add(name);
			builder.addParameter(toTypeName(param.type()), name);
			paramIdx++;
		}

		// Exceptions
		for (org.jboss.jandex.Type exception : method.exceptions()) {
			builder.addException(toTypeName(exception));
		}

		// Build the getMethod arguments: "methodName", ParamType1.class,
		// ParamType2.class
		CodeBlock getMethodArgs = buildGetMethodArgs(method);

		// Build the args array: new Object[]{p1, p2}
		CodeBlock argsArray = buildArgsArray(method, paramNames);

		// Build the lambda body: try { return m.invoke(target, args); } catch
		// (InvocationTargetException e) { throw e.getCause(); }
		CodeBlock lambda = CodeBlock.of(
				"() -> {\n" + "                    try { return m.invoke(target, args); }\n"
						+ "                    catch ($T e) { throw e.getCause(); }\n" + "                }",
				INVOCATION_TARGET_EX);

		// Build the full method body
		CodeBlock.Builder body = CodeBlock.builder();
		body.beginControlFlow("try");
		body.addStatement("var m = target.getClass().getMethod($L)", getMethodArgs);
		body.addStatement("var args = $L", argsArray);
		body.addStatement("var chain = new $T(target, new $T(m), args, interceptors, $L)", PROXY_CHAIN,
				RUNTIME_METADATA, lambda);

		if (returnType.equals(TypeName.VOID)) {
			body.addStatement("chain.proceed()");
		} else {
			body.addStatement("return ($T) chain.proceed()", returnType);
		}
		body.nextControlFlow("catch ($T e)", Throwable.class);

		if (returnType.equals(TypeName.VOID)) {
			body.addStatement("sneakyThrow(e)");
		} else if (returnType.isPrimitive()) {
			body.addStatement("sneakyThrow(e)");
			body.addStatement("return $L", defaultValue(returnType));
		} else {
			body.addStatement("sneakyThrow(e)");
			body.addStatement("return null");
		}
		body.endControlFlow();

		builder.addCode(body.build());
		return builder.build();
	}

	/**
	 * Builds a direct delegate method that bypasses the interceptor chain. Used for
	 * methods that have no binding annotations.
	 */
	private MethodSpec buildDirectDelegate(MethodInfo method) {
		MethodSpec.Builder builder = MethodSpec.methodBuilder(method.name()).addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC);

		TypeName returnType = toTypeName(method.returnType());
		builder.returns(returnType);

		// Parameters
		List<String> paramNames = new java.util.ArrayList<>();
		int paramIdx = 0;
		for (MethodParameterInfo param : method.parameters()) {
			String name = param.name();
			if (name == null) {
				name = "arg" + paramIdx;
			}
			paramNames.add(name);
			builder.addParameter(toTypeName(param.type()), name);
			paramIdx++;
		}

		// Exceptions
		for (org.jboss.jandex.Type exception : method.exceptions()) {
			builder.addException(toTypeName(exception));
		}

		// Direct delegation: target.method(args)
		StringBuilder call = new StringBuilder("target.").append(method.name()).append("(");
		for (int i = 0; i < paramNames.size(); i++) {
			if (i > 0)
				call.append(", ");
			call.append(paramNames.get(i));
		}
		call.append(")");

		if (returnType.equals(TypeName.VOID)) {
			builder.addStatement(call.toString());
		} else {
			builder.addStatement("return ($T) " + call.toString(), returnType);
		}

		return builder.build();
	}

	/**
	 * Builds the argument list for Class.getMethod("name", Type1.class,
	 * Type2.class).
	 */
	private CodeBlock buildGetMethodArgs(MethodInfo method) {
		CodeBlock.Builder args = CodeBlock.builder();
		args.add("$S", method.name());
		for (MethodParameterInfo param : method.parameters()) {
			args.add(", $T.class", toTypeName(param.type()));
		}
		return args.build();
	}

	/**
	 * Builds the Object[] args array from method parameters.
	 */
	private CodeBlock buildArgsArray(MethodInfo method, List<String> paramNames) {
		if (method.parametersCount() == 0) {
			return CodeBlock.of("new Object[]{}");
		}
		CodeBlock.Builder array = CodeBlock.builder();
		array.add("new Object[]{");
		boolean first = true;
		for (String name : paramNames) {
			if (!first)
				array.add(", ");
			array.add("$N", name);
			first = false;
		}
		array.add("}");
		return array.build();
	}

	private static TypeName toTypeName(org.jboss.jandex.Type type) {
		if (type.name() == null)
			return ClassName.get(Object.class);
		String name = type.name().toString();
		return switch (name) {
			case "void" -> TypeName.VOID;
			case "int" -> TypeName.INT;
			case "long" -> TypeName.LONG;
			case "double" -> TypeName.DOUBLE;
			case "boolean" -> TypeName.BOOLEAN;
			case "float" -> TypeName.FLOAT;
			case "byte" -> TypeName.BYTE;
			case "short" -> TypeName.SHORT;
			case "char" -> TypeName.CHAR;
			default -> ClassName.bestGuess(name);
		};
	}

	private static String defaultValue(TypeName type) {
		if (type.equals(TypeName.INT) || type.equals(TypeName.LONG) || type.equals(TypeName.SHORT)
				|| type.equals(TypeName.BYTE) || type.equals(TypeName.CHAR)) {
			return "0";
		}
		if (type.equals(TypeName.FLOAT) || type.equals(TypeName.DOUBLE)) {
			return "0.0";
		}
		if (type.equals(TypeName.BOOLEAN)) {
			return "false";
		}
		return "null";
	}

	private MethodSpec buildSneakyThrow() {
		TypeVariableName t = TypeVariableName.get("T", Throwable.class);
		return MethodSpec.methodBuilder("sneakyThrow").addModifiers(Modifier.PRIVATE, Modifier.STATIC)
				.addTypeVariable(t).addException(t).addParameter(Throwable.class, "e").addStatement("throw (T) e")
				.build();
	}

private String getPackageName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
    }

    private static ClassName safeClassName(String qualifiedName) {
        return ClassName.bestGuess(qualifiedName.replace('$', '.'));
    }
}
