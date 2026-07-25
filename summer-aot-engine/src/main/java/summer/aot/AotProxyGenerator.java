package summer.aot;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import java.io.IOException;
import java.util.ArrayList;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.bean.BeanDefinition;

/**
 * Generates AOP proxy classes for beans that need interception.
 *
 * <p>
 * For each bean with interceptors, generates a {@code $$AotProxy} class that
 * implements the same interfaces and delegates to the target bean through the
 * interceptor chain. Uses {@link summer.aop.InterceptedMethod} — a zero-
 * reflection, compile-time-constant method view — so proxies never touch
 * {@code java.lang.reflect.Method} at runtime.
 * </p>
 */
public final class AotProxyGenerator {

	private static final Logger log = LoggerFactory.getLogger(AotProxyGenerator.class);

	private static final ClassName PROXY_CHAIN = ClassName.get("summer.aop", "ProxyInterceptorChain");
	private static final ClassName INTERCEPTED_METHOD = ClassName.get("summer.aop", "InterceptedMethod");
	private static final ClassName SET = ClassName.get("java.util", "Set");

	public AotProxyGenerator() {
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
		Set<DotName> allBindingAnnotations = new HashSet<>();
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() && ci.hasAnnotation(DotName.createSimple("summer.aop.InterceptorBinding"))) {
				allBindingAnnotations.add(ci.name());
			}
		}

		for (BeanDefinition bean : beans) {
			if (bean.needsProxy() && !bean.interfaceNames.isEmpty()) {
				log.debug("[Summer] Generating AOP proxy for {}", bean.qualifiedName);
				generateProxy(bean, index, outputDir, allBindingAnnotations);
			}
		}
	}

	private void generateProxy(BeanDefinition bean, IndexView index, java.io.File outputDir,
			Set<DotName> bindingAnnotations) throws IOException {
		String packageName = getPackageName(bean.qualifiedName);
		String proxyClassName = bean.simpleName + "$$AotProxy";

		TypeSpec.Builder proxyBuilder = TypeSpec.classBuilder(proxyClassName)
				.addAnnotation(
						AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build())
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL);

		for (String ifaceName : bean.interfaceNames) {
			proxyBuilder.addSuperinterface(safeClassName(ifaceName));
		}

		ClassName targetClass = safeClassName(bean.qualifiedName);
		proxyBuilder.addField(targetClass, "target", Modifier.PRIVATE, Modifier.FINAL);

		ClassName interceptorType = ClassName.get("summer.aop", "MethodInterceptor");
		ParameterizedTypeName interceptorList = ParameterizedTypeName.get(ClassName.get(List.class), interceptorType);
		proxyBuilder.addField(interceptorList, "interceptors", Modifier.PRIVATE, Modifier.FINAL);

		MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
				.addParameter(targetClass, "target").addParameter(interceptorList, "interceptors")
				.addStatement("this.target = target").addStatement("this.interceptors = interceptors").build();
		proxyBuilder.addMethod(constructor);

		// Determine which methods should be intercepted from pre-computed
		// BeanDefinition.methodBindingAnnotations (key "" = class-level,
		// other keys = method-level annotations). No Jandex re-read.
		boolean classLevelBinding = bean.methodBindingAnnotations.containsKey("");
		Set<String> methodLevelBindingMethods;
		if (classLevelBinding) {
			methodLevelBindingMethods = Set.of();
		} else {
			methodLevelBindingMethods = bean.methodBindingAnnotations.keySet();
		}

		// Pass 1: collect intercepted methods and generate static metadata fields
		for (String ifaceName : bean.interfaceNames) {
			ClassInfo ifaceCi = index.getClassByName(DotName.createSimple(ifaceName));
			if (ifaceCi == null)
				continue;

			for (MethodInfo method : ifaceCi.methods()) {
				if (!method.isAbstract())
					continue;

				boolean shouldIntercept = classLevelBinding || methodLevelBindingMethods.contains(method.name());
				if (shouldIntercept) {
					proxyBuilder.addField(buildMetaField(method, bindingAnnotations));
				}
			}
		}

		// Pass 2: generate methods
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

		proxyBuilder.addMethod(buildSneakyThrow());

		JavaFile proxyFile = JavaFile.builder(packageName, proxyBuilder.build()).indent("    ").build();
		proxyFile.writeTo(outputDir);
	}

	// ── Metadata Field ────────────────────────────────────────────────

	private FieldSpec buildMetaField(MethodInfo method, Set<DotName> bindingAnnotations) {
		Set<ClassName> annotationClasses = new HashSet<>();
		for (AnnotationInstance ann : method.annotations()) {
			if (bindingAnnotations.contains(ann.name())) {
				annotationClasses.add(safeClassName(ann.name().toString()));
			}
		}

		// AOT emits the method view as compile-time constants: method name + the set
		// of binding annotation types. No reflection, no intermediate metadata object.
		CodeBlock init;
		if (annotationClasses.isEmpty()) {
			init = CodeBlock.of("new $T($S, $T.of())", INTERCEPTED_METHOD, method.name(), SET);
		} else {
			CodeBlock.Builder cb = CodeBlock.builder();
			cb.add("new $T($S, $T.of(", INTERCEPTED_METHOD, method.name(), SET);
			boolean first = true;
			for (ClassName annClass : annotationClasses) {
				if (!first)
					cb.add(", ");
				cb.add("$T.class", annClass);
				first = false;
			}
			cb.add("))");
			init = cb.build();
		}

		return FieldSpec
				.builder(INTERCEPTED_METHOD, metaFieldName(method), Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
				.initializer(init).build();
	}

	private static String metaFieldName(MethodInfo method) {
		return "META_" + method.name();
	}

	// ── Proxy Method (through interceptor chain) ──────────────────────

	private MethodSpec buildProxyMethod(MethodInfo method) {
		MethodSpec.Builder builder = MethodSpec.methodBuilder(method.name()).addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC);

		TypeName returnType = toTypeName(method.returnType());
		builder.returns(returnType);

		List<String> paramNames = new ArrayList<>();
		int paramIdx = 0;
		for (MethodParameterInfo param : method.parameters()) {
			String name = param.name();
			if (name == null)
				name = "arg" + paramIdx;
			paramNames.add(name);
			builder.addParameter(toTypeName(param.type()), name);
			paramIdx++;
		}

		for (org.jboss.jandex.Type exception : method.exceptions()) {
			builder.addException(toTypeName(exception));
		}

		CodeBlock argsArray = buildArgsArray(method, paramNames);

		// Direct invocation lambda — no reflection
		CodeBlock lambda;
		if (returnType.equals(TypeName.VOID)) {
			lambda = buildVoidLambda(method.name(), paramNames);
		} else {
			lambda = buildReturnLambda(method.name(), paramNames);
		}

		String metaName = metaFieldName(method);

		CodeBlock.Builder body = CodeBlock.builder();
		body.beginControlFlow("try");
		body.addStatement("var args = $L", argsArray);
		body.addStatement("var chain = new $T(target, $N, args, interceptors, $L)", PROXY_CHAIN, metaName, lambda);

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

	private CodeBlock buildVoidLambda(String methodName, List<String> paramNames) {
		StringBuilder call = new StringBuilder("target.").append(methodName).append("(");
		for (int i = 0; i < paramNames.size(); i++) {
			if (i > 0)
				call.append(", ");
			call.append("$N");
		}
		call.append(")");
		return CodeBlock.of("() -> { " + call + "; return null; }", paramNames.toArray());
	}

	private CodeBlock buildReturnLambda(String methodName, List<String> paramNames) {
		StringBuilder call = new StringBuilder("target.").append(methodName).append("(");
		for (int i = 0; i < paramNames.size(); i++) {
			if (i > 0)
				call.append(", ");
			call.append("$N");
		}
		call.append(")");
		return CodeBlock.of("() -> " + call, paramNames.toArray());
	}

	// ── Direct Delegate (no interceptor) ──────────────────────────────

	private MethodSpec buildDirectDelegate(MethodInfo method) {
		MethodSpec.Builder builder = MethodSpec.methodBuilder(method.name()).addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC);

		TypeName returnType = toTypeName(method.returnType());
		builder.returns(returnType);

		List<String> paramNames = new ArrayList<>();
		int paramIdx = 0;
		for (MethodParameterInfo param : method.parameters()) {
			String name = param.name();
			if (name == null)
				name = "arg" + paramIdx;
			paramNames.add(name);
			builder.addParameter(toTypeName(param.type()), name);
			paramIdx++;
		}

		for (org.jboss.jandex.Type exception : method.exceptions()) {
			builder.addException(toTypeName(exception));
		}

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

	// ── Args Array ────────────────────────────────────────────────────

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

	// ── Utilities ─────────────────────────────────────────────────────

	private static TypeName toTypeName(org.jboss.jandex.Type type) {
		if (type.name() == null)
			return ClassName.get(Object.class);
		String name = type.name().toString();
		if (name.equals("void"))
			return TypeName.VOID;
		// Primitive mapping is shared with the rest of the AOT codegen via TypeReads.
		if (PrimitiveTypes.isPrimitive(name))
			return TypeReads.typeName(name);
		return ClassName.bestGuess(name);
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
