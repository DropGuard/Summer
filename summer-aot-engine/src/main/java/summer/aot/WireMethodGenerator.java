package summer.aot;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.IndexView;
import summer.core.bean.BeanDefinition;
import summer.core.bean.ConfigPropertiesBean;
import summer.core.bean.InjectionParameter;

/**
 * Generates the bean instantiation body of the AOT-created {@code create()}
 * method. Emits {@code builder.register(...)} calls for each bean.
 *
 * <p>
 * Handles constructor injection, {@code @Bean} method invocation,
 * {@code @ConfigurationProperties} binding, AOP proxy wrapping, and interface
 * registration.
 * </p>
 */
public final class WireMethodGenerator {

	private static final ClassName JDBC_TEMPLATE = ClassName.get("summer.data.jdbc", "JdbcTemplate");

	public WireMethodGenerator() {
		this(Map.of());
	}

	/**
	 * Emits inline {@code RowMapper} lambda registrations for all {@code @RowModel}
	 * records in the index. Mappers are registered directly on the
	 * {@code JdbcTemplate} singleton via {@code registerMapper()}.
	 */
	void emitRowMapperRegistrations(MethodSpec.Builder wire, IndexView index, java.util.Set<String> activeClassNames,
			List<BeanDefinition> sortedBeans) {

		if (index == null) {
			return;
		}

		List<summer.data.jdbc.RowModelMeta> metas = summer.data.jdbc.RowMapperFactory.scanJandex(index);

		if (activeClassNames != null) {
			metas = metas.stream().filter(m -> activeClassNames.contains(m.modelClassName())).toList();
		}

		if (metas.isEmpty()) {
			return;
		}

		wire.addCode("\n");
		wire.addComment("Register @RowModel mappers with JdbcTemplate");
		wire.addStatement("$T _jt = ($T) builder.peek($T.class)", JDBC_TEMPLATE, JDBC_TEMPLATE, JDBC_TEMPLATE);
		wire.beginControlFlow("if (_jt != null)");

		for (var meta : metas) {
			// scanJandex() already validates field types (fail-fast on both engines),
			// so no separate assertion is needed here.

			ClassName modelClass = safeClassName(meta.modelClassName());
			var mapperVar = meta.simpleName().toLowerCase(java.util.Locale.ROOT) + "Mapper";

			wire.addCode("\n");
			wire.addComment(meta.simpleName() + " RowMapper");
			wire.addStatement("$T<$T> $N = ($N, $N) -> {", ClassName.get("summer.data.jdbc", "RowMapper"), modelClass,
					mapperVar, "rs", "rowNum");
			for (var field : meta.fields()) {
				String colName = summer.data.jdbc.RowMapperFactory.camelToSnake(field.name());
				CodeBlock readExpr = TypeReads.jdbcRead(colName, field.typeName());
				wire.addStatement("    $T $N = $L", TypeReads.typeName(field.typeName()), field.name(), readExpr);
			}
			StringBuilder ctorArgs = new StringBuilder();
			for (int i = 0; i < meta.fields().size(); i++) {
				if (i > 0)
					ctorArgs.append(", ");
				ctorArgs.append(meta.fields().get(i).name());
			}
			wire.addStatement("    return new $T($L)", modelClass, ctorArgs.toString());
			wire.addStatement("}");
			wire.addStatement("_jt.registerMapper($T.class, $N)", modelClass, mapperVar);
		}

		wire.endControlFlow();
	}

	private void emitComponentInstantiation(MethodSpec.Builder wire, BeanDefinition bean, ClassName beanClass,
			String varName) {
		CodeBlock args = buildConstructorArgs(bean);
		// Summer does not support class-based proxying -- JDK dynamic proxy
		// requires at least one interface. Fail fast.
		if (bean.needsProxy() && bean.interfaceNames.isEmpty()) {
			throw new summer.aop.SummerAopException(summer.core.ErrorCode.AOP_NO_INTERFACE, bean.qualifiedName
					+ " is annotated with AOP bindings but implements no interfaces. "
					+ "Summer uses JDK dynamic proxies -- extract an interface and inject it by the interface type instead.");
		}
		if (bean.needsProxy()) {
			String implVar = varName + "_impl";
			if (bean.parameters.isEmpty()) {
				wire.addStatement("$T $N = new $T()", beanClass, implVar, beanClass);
			} else {
				wire.addStatement("$T $N = new $T($L)", beanClass, implVar, beanClass, args);
			}

			String interceptorsListVar = varName + "_interceptors";
			wire.addStatement("$T<$T> $N = new $T<>()", ClassName.get(List.class),
					ClassName.get("summer.aop", "MethodInterceptor"), interceptorsListVar,
					ClassName.get(ArrayList.class));

			for (BeanDefinition interceptor : bean.interceptors) {
				wire.addStatement("$N.add($N)", interceptorsListVar, interceptor.variableName);
			}

			com.palantir.javapoet.TypeName proxyType = bean.interfaceNames.isEmpty()
					? beanClass
					: safeClassName(bean.interfaceNames.get(0));
			ClassName proxyClass = ClassName.get(beanClass.packageName(), beanClass.simpleName() + "$$AotProxy");
			wire.addStatement("$T $N = new $T($N, $N)", proxyType, varName, proxyClass, implVar, interceptorsListVar);
		} else {
			if (bean.parameters.isEmpty()) {
				wire.addStatement("$T $N = new $T()", beanClass, varName, beanClass);
			} else {
				wire.addStatement("$T $N = new $T($L)", beanClass, varName, beanClass, args);
			}
		}
	}

	private CodeBlock buildConstructorArgs(BeanDefinition bean) {
		CodeBlock.Builder args = CodeBlock.builder();
		boolean first = true;
		// Each parameter carries its own resolved dependencies (populated by
		// SharedDependencyResolver). Position is the list index, so no cursor
		// rebuilds the structure — we read parameters directly.
		for (InjectionParameter parameter : bean.parameters) {
			if (!first)
				args.add(", ");
			first = false;
			args.add(parameterArgument(parameter));
		}
		return args.build();
	}

	/**
	 * Builds the constructor/method argument expression for one injection
	 * parameter. A List<T> emits {@code List.of(dep1, dep2, ...)} straight from the
	 * parameter's own resolved list — no re-filtering by element type that could
	 * collide when two List<T> parameters share an element type.
	 */
	private CodeBlock parameterArgument(InjectionParameter parameter) {
		if (parameter.typeName().startsWith("java.util.List<")) {
			if (parameter.resolved().isEmpty()) {
				return CodeBlock.of("java.util.List.of()");
			}
			CodeBlock.Builder cb = CodeBlock.builder();
			cb.add("java.util.List.of(");
			boolean first = true;
			for (BeanDefinition dep : parameter.resolved()) {
				if (!first)
					cb.add(", ");
				first = false;
				cb.add("$N", dep.variableName);
			}
			cb.add(")");
			return cb.build();
		}
		// Scalar (non-List) parameter.
		if (parameter.typeName().equals("summer.core.BeanContainer")) {
			// BeanContainer is not available during AOT build phase
			return CodeBlock.of("null");
		}
		if (parameter.resolved().isEmpty()) {
			return CodeBlock.of("null");
		}
		return CodeBlock.of("$N", parameter.resolved().get(0).variableName);
	}

	private void emitFactoryProductInstantiation(MethodSpec.Builder wire, BeanDefinition bean, String varName) {
		ClassName producedClass = safeClassName(bean.qualifiedName);
		String configVar = bean.configBeanDefinition.variableName;
		String methodName = bean.producerMethodName;
		CodeBlock args = buildConstructorArgs(bean);

		if (bean.parameters.isEmpty()) {
			wire.addStatement("$T $N = $N.$N()", producedClass, varName, configVar, methodName);
		} else {
			wire.addStatement("$T $N = $N.$N($L)", producedClass, varName, configVar, methodName, args);
		}
	}

	// Override content is known at code-generation time (from @TestProfile), so it
	// is inlined into the generated wire() method as a BindingContext literal
	// rather than read from a runtime ThreadLocal.
	private final Map<String, Object> profileOverrides;

	WireMethodGenerator(Map<String, Object> profileOverrides) {
		this.profileOverrides = profileOverrides != null ? profileOverrides : Map.of();
	}

	void generateWireMethod(MethodSpec.Builder wire, List<BeanDefinition> sortedBeans) {
		generateWireMethod(wire, sortedBeans, profileOverrides);
	}

	void generateWireMethod(MethodSpec.Builder wire, List<BeanDefinition> sortedBeans, Map<String, Object> overrides) {
		for (int i = 0; i < sortedBeans.size(); i++) {
			BeanDefinition bean = sortedBeans.get(i);
			ClassName beanClass = safeClassName(bean.qualifiedName);
			String varName = bean.variableName;

			if (i > 0) {
				wire.addCode("\n");
			}

			// Engine-provided (synthetic) beans: declare a local var holding the
			// instance, then register it. The var name is the bean's own
			// variableName so @Bean methods that depend on it (e.g. EntityMetadataRegistrar
			// depending on IndexView) reference the same symbol. The construction
			// expression is supplied at the definition site via addSyntheticBean's
			// aotInstanceExpression, so this generator emits it verbatim without
			// knowing each synthetic type's construction.
			if (bean.syntheticInstance != null) {
				com.palantir.javapoet.CodeBlock instanceExpr = syntheticInstanceExpression(bean);
				wire.addStatement("$T $N = $L", beanClass, varName, instanceExpr);
				wire.addStatement("builder.register($T.class, $N)", beanClass, varName);
				continue;
			}

			if (bean instanceof ConfigPropertiesBean cpb) {
				emitConfigPropertiesInstantiation(wire, cpb, beanClass, varName, overrides);
			} else if (bean.isFactoryMethod()) {
				emitFactoryProductInstantiation(wire, bean, varName);
			} else {
				emitComponentInstantiation(wire, bean, beanClass, varName);
			}

			if (bean instanceof ConfigPropertiesBean) {
				wire.addStatement("builder.register($T.class, $N)", beanClass, varName);
			} else {
				if (bean.needsProxy() && !bean.interfaceNames.isEmpty()) {
					wire.addStatement("builder.register($T.class, $N)", beanClass, varName + "_impl");
				} else {
					wire.addStatement("builder.register($T.class, $N)", beanClass, varName);
				}
				for (String iface : bean.interfaceNames) {
					wire.addStatement("builder.register($T.class, $N)", parseTypeName(iface), varName);
				}
			}
		}

		// Validation Phase: run all Validator beans
		wire.addCode("\n");
		wire.addComment("Validation Phase");
		wire.beginControlFlow("for ($T bean : builder.singletons().values())", Object.class);
		wire.beginControlFlow("if (bean instanceof $T validator)",
				ClassName.get("summer.core.validation", "Validator"));
		wire.addStatement("$T target = builder.peek(validator.targetType())", Object.class);
		wire.beginControlFlow("if (target != null)");
		wire.addStatement("validator.validate(target)");
		wire.endControlFlow();
		wire.endControlFlow();
		wire.endControlFlow();
	}

	/**
	 * Code expression for a synthetic bean's pre-built instance, usable inside the
	 * generated static {@code build()} method (which has no generator state). The
	 * expression is supplied at the definition site via
	 * {@code BeanDeployment#addSyntheticBean}'s {@code aotInstanceExpression}, so
	 * this generator never branches on the synthetic type.
	 */
	private com.palantir.javapoet.CodeBlock syntheticInstanceExpression(BeanDefinition bean) {
		// The construction expression is supplied at the definition site
		// (BeanDeployment/RuntimeBeanContainerBuilder via addSyntheticBean), so this
		// generator stays ignorant of each synthetic type. Emit it verbatim.
		if (bean.aotInstanceExpression == null) {
			throw new IllegalStateException("Synthetic bean has no AOT instance expression: " + bean.qualifiedName);
		}
		return com.palantir.javapoet.CodeBlock.of(bean.aotInstanceExpression);
	}

	private void emitConfigPropertiesInstantiation(MethodSpec.Builder wire, ConfigPropertiesBean bean,
			ClassName beanClass, String varName, Map<String, Object> overrides) {
		ClassName configBinder = ClassName.get("summer.core.config", "ConfigBinder");
		String prefix = bean.configPropertiesPrefix != null ? bean.configPropertiesPrefix : "";
		// Overrides and @DefaultValue results are baked into the generated context as
		// a BindingContext literal, so the same container identity (derived from
		// override content) and binding read from one explicit source and never
		// drift. No ThreadLocal, no remove(). @DefaultValue metadata was collected
		// from Jandex at discovery time and stored on the bean.
		CodeBlock ctxLiteral;
		if (bean.defaultValues.isEmpty() && overrides.isEmpty()) {
			ctxLiteral = CodeBlock.of("$T.BindingContext.of()", configBinder);
		} else if (bean.defaultValues.isEmpty()) {
			ctxLiteral = CodeBlock.of("$T.BindingContext.of($L)", configBinder, buildOverridesLiteral(overrides));
		} else if (overrides.isEmpty()) {
			ctxLiteral = CodeBlock.of("$T.BindingContext.of($L)", configBinder,
					buildDefaultsLiteral(bean.defaultValues, bean.fieldTypes));
		} else {
			ctxLiteral = CodeBlock.of("$T.BindingContext.of($L, $L)", configBinder,
					buildDefaultsLiteral(bean.defaultValues, bean.fieldTypes), buildOverridesLiteral(overrides));
		}
		wire.addStatement("$T $N = $T.bind($L, $S, $T.class)", beanClass, varName, configBinder, ctxLiteral, prefix,
				beanClass);
	}

	private static CodeBlock buildOverridesLiteral(Map<String, Object> overrides) {
		CodeBlock.Builder cb = CodeBlock.builder();
		cb.add("java.util.Map.of(");
		boolean first = true;
		for (Map.Entry<String, Object> e : overrides.entrySet()) {
			if (!first) {
				cb.add(", ");
			}
			cb.add("$S, $S", e.getKey(), String.valueOf(e.getValue()));
			first = false;
		}
		cb.add(")");
		return cb.build();
	}

	/**
	 * Builds a {@code Map.of(name, TypeConverter.convert(raw, Type.class), ...)}
	 * literal. Each default is converted at generated-code time (statically typed),
	 * so the runtime bind call applies defaults with zero reflection.
	 */
	private static CodeBlock buildDefaultsLiteral(Map<String, String> defaults, Map<String, String> typeNames) {
		CodeBlock.Builder cb = CodeBlock.builder();
		cb.add("java.util.Map.of(");
		boolean first = true;
		for (Map.Entry<String, String> e : defaults.entrySet()) {
			if (!first) {
				cb.add(", ");
			}
			cb.add("$S, $T.convert($S, $T.class)", e.getKey(), ClassName.get("summer.core.config", "TypeConverter"),
					e.getValue(), TypeReads.typeName(typeNames.get(e.getKey())));
			first = false;
		}
		cb.add(")");
		return cb.build();
	}

	/**
	 * Converts a JVM type name to a JavaPoet
	 * {@link com.palantir.javapoet.TypeName}. The primitive mapping is delegated to
	 * {@link TypeReads#typeName(String)}; this method adds array / nested-class
	 * ({@code $}) preprocessing that only applies to Jandex-derived type names.
	 */
	static com.palantir.javapoet.TypeName parseTypeName(String typeName) {
		if (typeName.startsWith("["))
			return ClassName.get(Object.class);
		if (PrimitiveTypes.isPrimitive(typeName))
			return TypeReads.typeName(typeName);
		return ClassName.bestGuess(typeName.replace('$', '.'));
	}

	/**
	 * Creates a {@link ClassName} from a qualified name that may contain
	 * JVM-internal {@code $} nested-class separators. Replaces {@code $} with
	 * {@code .} so the generated source uses valid Java syntax.
	 */
	private static ClassName safeClassName(String qualifiedName) {
		return ClassName.bestGuess(qualifiedName.replace('$', '.'));
	}
}
