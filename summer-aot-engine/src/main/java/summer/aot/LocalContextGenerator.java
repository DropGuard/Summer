package summer.aot;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.bean.BeanDefinition;

/**
 * Generates a minimal AOT {@code BeanContainer} for a test class annotated with
 * {@code @SummerTest(engine = AOT, value = {...})}.
 *
 * <p>
 * Computes the transitive dependency closure from entry beans using pure Jandex
 * BFS (mirrors the runtime {@code transitiveExpand}), then generates a
 * {@code LocalContext_<testClassName>} class containing only the beans in that
 * closure.
 * </p>
 */
public final class LocalContextGenerator {

	private static final Logger log = LoggerFactory.getLogger(LocalContextGenerator.class);

	private static final String PACKAGE = "summer.core.aot";

	private final WireMethodGenerator wireGen;
	private final BuildContext ctx;

	public LocalContextGenerator(WireMethodGenerator wireGen, BuildContext ctx) {
		this.wireGen = wireGen;
		this.ctx = ctx;
	}

	private static final DotName COMPONENT = DotName.createSimple("summer.core.Component");
	private static final DotName CONFIGURATION = DotName.createSimple("summer.core.annotation.Configuration");
	private static final DotName BEAN = DotName.createSimple("summer.core.annotation.Bean");
	private static final DotName CONFIGURATION_PROPERTIES = DotName
			.createSimple("summer.core.config.ConfigurationProperties");
	private static final DotName REPLACES = DotName.createSimple("summer.core.annotation.Replaces");
	private static final DotName BEAN_CONTAINER_DOT = DotName.createSimple("summer.core.BeanContainer");

	// ---- transitive closure (pure Jandex BFS, mirrors
	// RuntimeComponentScanner.transitiveExpand) ----

	/**
	 * Computes the transitive dependency closure of the given entry beans, walking
	 * constructor parameters, {@code @Bean} method return types, and
	 * {@code @Replaces} targets through the Jandex index.
	 *
	 * @param entryNames
	 *            qualified class names of the entry beans
	 * @param index
	 *            Jandex index of all available classes
	 * @return qualified class names of all beans in the transitive closure
	 */
	public static Set<String> transitiveClosure(Set<String> entryNames, IndexView index) {
		Set<String> closure = new LinkedHashSet<>(entryNames);
		Deque<String> queue = new ArrayDeque<>(entryNames);

		while (!queue.isEmpty()) {
			String currentName = queue.pollFirst();
			ClassInfo ci = index.getClassByName(DotName.createSimple(currentName));
			if (ci == null) {
				continue;
			}

			// Interfaces / abstract classes cannot be beans themselves
			if (ci.isInterface() || ci.isAbstract()) {
				// However, if annotated with @Component, we treat it as error-worthy
				// but still expand implementors for the dependency's sake
				for (ClassInfo impl : index.getKnownDirectImplementations(ci.name())) {
					if (isConcreteComponent(impl, index) && closure.add(impl.name().toString())) {
						queue.addLast(impl.name().toString());
					}
				}
				continue;
			}

			// @Replaces target must be in the closure
			AnnotationInstance replacesAnn = ci.annotation(REPLACES);
			if (replacesAnn != null) {
				String target = replacesAnn.value().asClass().name().toString();
				if (closure.add(target)) {
					queue.addLast(target);
				}
				expandImplementors(target, index, closure, queue);
			}

			// @ConfigurationProperties have no constructor dependencies
			if (ci.hasAnnotation(CONFIGURATION_PROPERTIES)) {
				continue;
			}

			// @Configuration: add @Bean method return types
			if (ci.hasAnnotation(CONFIGURATION)) {
				for (MethodInfo method : ci.methods()) {
					if (!method.hasAnnotation(BEAN)) {
						continue;
					}
					String returnType = method.returnType().name().toString();
					if (closure.add(returnType)) {
						queue.addLast(returnType);
					}

					// Method-level @Replaces
					AnnotationInstance beanReplaces = method.annotation(REPLACES);
					if (beanReplaces != null) {
						String beanTarget = beanReplaces.value().asClass().name().toString();
						if (closure.add(beanTarget)) {
							queue.addLast(beanTarget);
						}
						expandImplementors(beanTarget, index, closure, queue);
					}
				}
			}

			// Walk constructor parameters
			MethodInfo ctor = findConstructor(ci);
			if (ctor == null) {
				continue;
			}
			for (Type paramType : ctor.parameterTypes()) {
				String paramName = paramType.name().toString();

				// Skip BeanContainer
				if (BEAN_CONTAINER_DOT.equals(paramType.name())) {
					continue;
				}

				// Handle List<T>
				if ("java.util.List".equals(paramName) && paramType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
					var pType = paramType.asParameterizedType();
					if (!pType.arguments().isEmpty()) {
						String elementType = pType.arguments().get(0).name().toString();
						expandImplementors(elementType, index, closure, queue);
					}
					continue;
				}

				expandImplementors(paramName, index, closure, queue);
			}
		}

		log.debug("[Summer] transitiveClosure: {} seeds → {} closure beans", entryNames.size(), closure.size());
		return closure;
	}

	private static void expandImplementors(String typeName, IndexView index, Set<String> closure, Deque<String> queue) {
		DotName dotName = DotName.createSimple(typeName);
		ClassInfo ci = index.getClassByName(dotName);

		// Concrete class — just add it
		if (ci != null && !ci.isInterface() && !ci.isAbstract()) {
			if (closure.add(typeName)) {
				queue.addLast(typeName);
			}
			return;
		}

		// Interface or abstract — find all @Component concrete implementations
		for (ClassInfo impl : index.getKnownDirectImplementations(dotName)) {
			if (isConcreteComponent(impl, index)) {
				String implName = impl.name().toString();
				if (closure.add(implName)) {
					queue.addLast(implName);
				}
			}
		}
	}

	private static boolean isConcreteComponent(ClassInfo ci, IndexView index) {
		if (ci == null) {
			return false;
		}
		if (ci.isInterface() || ci.isAbstract()) {
			return false;
		}
		return ci.hasAnnotation(COMPONENT) || hasMetaComponentAnnotation(ci, index, new HashSet<>());
	}

	private static boolean hasMetaComponentAnnotation(ClassInfo classInfo, IndexView index, Set<DotName> visited) {
		if (classInfo == null || !visited.add(classInfo.name())) {
			return false;
		}
		if (classInfo.hasAnnotation(COMPONENT)) {
			return true;
		}
		for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
			ClassInfo meta = index.getClassByName(ann.name());
			if (hasMetaComponentAnnotation(meta, index, visited)) {
				return true;
			}
		}
		return false;
	}

	private static MethodInfo findConstructor(ClassInfo ci) {
		for (MethodInfo method : ci.methods()) {
			if (method.name().equals("<init>") && (method.flags() & 0x0001) != 0) {
				return method;
			}
		}
		return null;
	}

	// ---- code generation (mirrors AotContextGenerator) ---------------------

	private static final String CORE_PACKAGE = "summer.core";
	private static final ClassName CN_BEAN_REGISTRY = ClassName.get(CORE_PACKAGE, "BeanRegistry");
	private static final ClassName CN_BEAN_CONTAINER = ClassName.get(CORE_PACKAGE, "BeanContainer");
	private static final ClassName CN_ENGINE = ClassName.get(CORE_PACKAGE, "Engine");
	private static final ClassName CN_AOT_DI_MARKER = ClassName.get(CORE_PACKAGE, "AotDiMarker");
	private static final ClassName CN_CONFIG_BINDER = ClassName.get("summer.core.config", "ConfigBinder");
	private static final ClassName CN_DEFAULT_VALUE_RESOLVER = ClassName.get("summer.core.config",
			"DefaultValueResolver");

	/**
	 * Generates a {@code LocalContext_<testClassName>} source file containing only
	 * the beans identified by the scoped discovery.
	 *
	 * @param testClassName
	 *            fully-qualified name of the test class
	 * @param sortedBeans
	 *            topologically-sorted beans in the closure
	 */
	public void generate(String testClassName, List<BeanDefinition> sortedBeans) throws IOException {

		String safeName = "LocalContext_" + testClassName.replace('.', '_').replace('$', '_');

		TypeSpec contextClass = TypeSpec.classBuilder(safeName)
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.FINAL)
				.addMethod(buildCreateMethod(sortedBeans))
				.addJavadoc("Generated by summer-maven-plugin for test class: " + testClassName + "\n").build();

		JavaFile javaFile = JavaFile.builder(PACKAGE, contextClass)
				.addFileComment("Auto-generated by summer-maven-plugin. Do not edit!").indent("    ").build();
		javaFile.writeTo(ctx.outputDir());

		log.debug("[Summer] Generated LocalContext for {} ({} beans)", testClassName, sortedBeans.size());
	}
	private MethodSpec buildCreateMethod(List<BeanDefinition> sortedBeans) {
		MethodSpec.Builder method = MethodSpec.methodBuilder("build")
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC)
				.returns(CN_BEAN_CONTAINER).addException(Exception.class);

		method.addStatement("$T registry = new $T()", CN_BEAN_REGISTRY, CN_BEAN_REGISTRY);
		method.addStatement("registry.registerSingleton($T.class, new $T())", CN_AOT_DI_MARKER, CN_AOT_DI_MARKER);

		method.addCode("\n");
		method.addComment("Setup @DefaultValue resolver");
		method.addStatement("$T.setDefaultValueResolver(($T) Class.forName($S).getField($S).get(null))",
				CN_CONFIG_BINDER, CN_DEFAULT_VALUE_RESOLVER, "summer.runtime.RuntimeDefaultValueResolver", "INSTANCE");
		method.addCode("\n");

		wireGen.generateWireMethod(method, sortedBeans);
		// Route adapter
		boolean hasControllers = sortedBeans.stream().anyMatch(b -> !b.routes.isEmpty());
		if (hasControllers) {
			method.addCode("\n");
			method.addComment("Register route adapter");
			method.addStatement("$T _routeAdapter = new $T()",
					ClassName.get("summer.core.aot", "GeneratedAnnotationRouterAdapter"),
					ClassName.get("summer.core.aot", "GeneratedAnnotationRouterAdapter"));
			method.addStatement("registry.registerSingleton($T.class, _routeAdapter)",
					ClassName.get("summer.web", "RouteRegistrar"));
		}

		// Exception handler adapter — always present (empty if no handlers)
		method.addCode("\n");
		method.addComment("Register exception handler adapter");
		method.addStatement("$T _ehAdapter = new $T()",
				ClassName.get("summer.core.aot", "GeneratedExceptionHandlerAdapter"),
				ClassName.get("summer.core.aot", "GeneratedExceptionHandlerAdapter"));
		method.addStatement("registry.registerSingleton($T.class, _ehAdapter)",
				ClassName.get("summer.web", "ExceptionHandlerRegistrar"));

		method.addCode("\n");
		method.addStatement("return $T.create(registry, $T.AOT)", CN_BEAN_CONTAINER, CN_ENGINE);

		return method.build();
	}
}
