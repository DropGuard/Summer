package summer.aot;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import summer.core.bean.BeanDefinition;

/**
 * Generates {@code GeneratedExceptionHandlerAdapter} — the AOT counterpart of
 * {@code RuntimeExceptionHandlerRegistrar}.
 *
 * <p>
 * Discovers {@code @ExceptionHandler} methods via Jandex and generates direct
 * registration code. No reflection at runtime.
 * </p>
 */
public final class ExceptionHandlerAdapterGenerator {

	private static final DotName EXCEPTION_HANDLER_DOT = DotName.createSimple("summer.web.annotation.ExceptionHandler");
	private static final DotName HTTP_CONTEXT_DOT = DotName.createSimple("summer.web.HttpContext");
	private static final ClassName EXCEPTION_REGISTRY = ClassName.get("summer.web", "ExceptionRegistry");
	private static final ClassName BEAN_CONTAINER = ClassName.get("summer.core", "BeanContainer");
	private static final ClassName EXCEPTION_HANDLER_REGISTRAR = ClassName.get("summer.web",
			"ExceptionHandlerRegistrar");
	private static final ClassName REQUEST_ATTRIBUTES = ClassName.get("summer.web", "RequestAttributes");
	private static final String PACKAGE = "summer.core.aot";
	private static final String CLASS_NAME = "GeneratedExceptionHandlerAdapter";

	public void generate(List<BeanDefinition> beans, IndexView index, java.io.File outputDir) throws IOException {
		CodeBlock body = buildRegisterHandlersBody(beans, index);

		TypeSpec adapter = TypeSpec.classBuilder(CLASS_NAME)
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.FINAL)
				.addSuperinterface(EXCEPTION_HANDLER_REGISTRAR)
				.addMethod(MethodSpec.methodBuilder("registerHandlers").addAnnotation(Override.class)
						.addModifiers(javax.lang.model.element.Modifier.PUBLIC)
						.addParameter(EXCEPTION_REGISTRY, "registry").addParameter(BEAN_CONTAINER, "context")
						.addCode(body).build())
				.build();

		JavaFile.builder(PACKAGE, adapter).indent("    ").build().writeTo(outputDir);
	}

	private CodeBlock buildRegisterHandlersBody(List<BeanDefinition> beans, IndexView index) {
		CodeBlock.Builder code = CodeBlock.builder();
		if (index == null) {
			return code.build();
		}

		Set<String> declaredHandlers = new HashSet<>();

		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null) {
				continue;
			}
			for (MethodInfo method : ci.methods()) {
				AnnotationInstance ann = method.annotation(EXCEPTION_HANDLER_DOT);
				if (ann == null) {
					continue;
				}

				String exceptionClassName = ann.value().asClass().name().toString();
				ClassName handlerClass = safeClassName(bean.qualifiedName);
				ClassName exceptionType = safeClassName(exceptionClassName);
				String handlerVar = bean.variableName;

				// Build args: Throwable params get 'ex', HttpContext params get 'httpCtx'
				StringBuilder args = new StringBuilder();
				for (org.jboss.jandex.MethodParameterInfo param : method.parameters()) {
					if (args.length() > 0) {
						args.append(", ");
					}
					boolean isContext = param.type().name() != null && param.type().name().equals(HTTP_CONTEXT_DOT);
					args.append(isContext ? "httpCtx" : "ex");
				}

				if (declaredHandlers.add(bean.qualifiedName)) {
					code.addStatement("$T $N = context.getBean($T.class)", handlerClass, handlerVar, handlerClass);
				}
				code.beginControlFlow("registry.register($T.class, httpCtx ->", exceptionType);
				code.addStatement("$T ex = ($T) httpCtx.request().getAttribute($T.LAST_EXCEPTION)", exceptionType,
						exceptionType, REQUEST_ATTRIBUTES);
				code.addStatement("$N.$N($N)", handlerVar, method.name(), args);
				code.endControlFlow(")");
			}
		}
		return code.build();
	}

	private static ClassName safeClassName(String qualifiedName) {
		return ClassName.bestGuess(qualifiedName.replace('$', '.'));
	}
}
