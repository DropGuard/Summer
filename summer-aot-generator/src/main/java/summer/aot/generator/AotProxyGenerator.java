package summer.aot.generator;

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
import java.util.List;
import javax.lang.model.element.Modifier;

/**
 * Generates AOP proxy classes for beans that need interception.
 *
 * <p>
 * For each bean with interceptors, generates a {@code $$AotProxy} class that
 * implements the same interfaces and delegates to the target bean through the
 * interceptor chain. Reads all metadata from {@link ComponentBean} descriptors
 * — no Jandex index access at generation time.
 * </p>
 */
public final class AotProxyGenerator {

	private static final ClassName PROXY_CHAIN = ClassName.get("summer.aop", "ProxyInterceptorChain");
	private static final ClassName RUNTIME_METADATA = ClassName.get("summer.runtime", "RuntimeMethodMetadata");
	private static final ClassName INVOCATION_TARGET_EX = ClassName.get("java.lang.reflect",
			"InvocationTargetException");

	public AotProxyGenerator() {
	}

	public void generate(List<BeanDefinition> beans, java.io.File outputDir) throws IOException {
		for (BeanDefinition bean : beans) {
			if (bean instanceof ComponentBean cb && cb.needsProxy && !cb.interfaceNames.isEmpty()) {
				generateProxy(cb, outputDir);
			}
		}
	}

	private void generateProxy(ComponentBean bean, java.io.File outputDir) throws IOException {
		String packageName = TypeNameUtil.packageName(bean.qualifiedName);
		String proxyClassName = bean.simpleName + "$$AotProxy";

		TypeSpec.Builder proxyBuilder = TypeSpec.classBuilder(proxyClassName)
				.addAnnotation(
						AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build())
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL);

		for (String ifaceName : bean.interfaceNames) {
			proxyBuilder.addSuperinterface(TypeNameUtil.classNameFromString(ifaceName));
		}

		ClassName targetClass = TypeNameUtil.classNameFromString(bean.qualifiedName);
		proxyBuilder.addField(targetClass, "target", Modifier.PRIVATE, Modifier.FINAL);

		ClassName interceptorType = ClassName.get("summer.aop", "MethodInterceptor");
		ParameterizedTypeName interceptorList = ParameterizedTypeName.get(ClassName.get(List.class), interceptorType);
		proxyBuilder.addField(interceptorList, "interceptors", Modifier.PRIVATE, Modifier.FINAL);

		MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
				.addParameter(targetClass, "target").addParameter(interceptorList, "interceptors")
				.addStatement("this.target = target").addStatement("this.interceptors = interceptors").build();
		proxyBuilder.addMethod(constructor);

		for (ComponentBean.ProxyMethodInfo method : bean.proxyMethods) {
			if (method.shouldIntercept()) {
				proxyBuilder.addMethod(buildProxyMethod(method));
			} else {
				proxyBuilder.addMethod(buildDirectDelegate(method));
			}
		}

		proxyBuilder.addMethod(buildSneakyThrow());

		JavaFile proxyFile = JavaFile.builder(packageName, proxyBuilder.build()).indent("    ").build();
		proxyFile.writeTo(outputDir);
	}

	private MethodSpec buildProxyMethod(ComponentBean.ProxyMethodInfo method) {
		MethodSpec.Builder builder = MethodSpec.methodBuilder(method.methodName()).addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC);

		TypeName returnType = TypeNameUtil.fromString(method.returnType());
		builder.returns(returnType);

		for (ComponentBean.ProxyMethodInfo.ParamInfo param : method.params()) {
			builder.addParameter(TypeNameUtil.fromString(param.type()), param.name());
		}

		for (String ex : method.exceptions()) {
			builder.addException(TypeNameUtil.fromString(ex));
		}

		CodeBlock getMethodArgs = buildGetMethodArgs(method);
		CodeBlock argsArray = buildArgsArray(method);

		CodeBlock lambda = CodeBlock.of(
				"() -> {\n" + "                    try { return m.invoke(target, args); }\n"
						+ "                    catch ($T e) { throw e.getCause(); }\n" + "                }",
				INVOCATION_TARGET_EX);

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

	private MethodSpec buildDirectDelegate(ComponentBean.ProxyMethodInfo method) {
		MethodSpec.Builder builder = MethodSpec.methodBuilder(method.methodName()).addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC);

		TypeName returnType = TypeNameUtil.fromString(method.returnType());
		builder.returns(returnType);

		for (ComponentBean.ProxyMethodInfo.ParamInfo param : method.params()) {
			builder.addParameter(TypeNameUtil.fromString(param.type()), param.name());
		}

		for (String ex : method.exceptions()) {
			builder.addException(TypeNameUtil.fromString(ex));
		}

		StringBuilder call = new StringBuilder("target.").append(method.methodName()).append("(");
		for (int i = 0; i < method.params().size(); i++) {
			if (i > 0)
				call.append(", ");
			call.append(method.params().get(i).name());
		}
		call.append(")");

		if (returnType.equals(TypeName.VOID)) {
			builder.addStatement(call.toString());
		} else {
			builder.addStatement("return ($T) " + call.toString(), returnType);
		}

		return builder.build();
	}

	private CodeBlock buildGetMethodArgs(ComponentBean.ProxyMethodInfo method) {
		CodeBlock.Builder args = CodeBlock.builder();
		args.add("$S", method.methodName());
		for (ComponentBean.ProxyMethodInfo.ParamInfo param : method.params()) {
			args.add(", $T.class", TypeNameUtil.fromString(param.type()));
		}
		return args.build();
	}

	private CodeBlock buildArgsArray(ComponentBean.ProxyMethodInfo method) {
		if (method.params().isEmpty()) {
			return CodeBlock.of("new Object[]{}");
		}
		CodeBlock.Builder array = CodeBlock.builder();
		array.add("new Object[]{");
		for (int i = 0; i < method.params().size(); i++) {
			if (i > 0)
				array.add(", ");
			array.add("$N", method.params().get(i).name());
		}
		array.add("}");
		return array.build();
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
}
