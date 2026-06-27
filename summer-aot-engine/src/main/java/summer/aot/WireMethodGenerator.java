package summer.aot;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import java.util.ArrayList;
import java.util.List;
import org.jboss.jandex.IndexView;
import summer.core.bean.BeanDefinition;
import summer.core.bean.ConfigPropertiesBean;

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
	}

	void generateWireMethod(MethodSpec.Builder wire, List<BeanDefinition> sortedBeans) {
		for (int i = 0; i < sortedBeans.size(); i++) {
			BeanDefinition bean = sortedBeans.get(i);
			ClassName beanClass = safeClassName(bean.qualifiedName);
			String varName = bean.variableName;

			if (i > 0) {
				wire.addCode("\n");
			}

			if (bean instanceof ConfigPropertiesBean cpb) {
				emitConfigPropertiesInstantiation(wire, cpb, beanClass, varName);
			} else if (bean.isFactoryMethod()) {
				emitFactoryProductInstantiation(wire, bean, varName);
			} else {
				emitComponentInstantiation(wire, bean, beanClass, varName);
			}

			if (bean instanceof ConfigPropertiesBean) {
				wire.addStatement("builder.register($T.class, $N)", beanClass, varName);
			} else {
				if (bean.needsProxy) {
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
	 * Emits inline {@code RowMapper} lambda registrations for all {@code @RowModel}
	 * records in the index. Mappers are registered directly on the
	 * {@code JdbcTemplate} singleton via {@code registerMapper()}.
	 */
	void emitRowMapperRegistrations(MethodSpec.Builder wire, IndexView index, java.util.Set<String> activeClassNames,
			List<BeanDefinition> sortedBeans) {

		if (index == null) {
			return;
		}

		List<summer.data.jdbc.RowMapperFactory.RowModelMeta> metas = summer.data.jdbc.RowMapperFactory
				.scanJandex(index);

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
			ClassName modelClass = safeClassName(meta.modelClassName());
			var mapperVar = meta.simpleName().toLowerCase(java.util.Locale.ROOT) + "Mapper";

			wire.addCode("\n");
			wire.addComment(meta.simpleName() + " RowMapper");
			wire.addStatement("$T<$T> $N = ($N, $N) -> {", ClassName.get("summer.data.jdbc", "RowMapper"), modelClass,
					mapperVar, "rs", "rowNum");
			for (var field : meta.fields()) {
				var fieldType = toTypeName(field.typeName());
				wire.addStatement("    $T $N = $L", fieldType, field.name(), field.jdbcGetter().replace("rs.", "rs."));
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

	private static com.palantir.javapoet.TypeName toTypeName(String typeName) {
		return switch (typeName) {
			case "int" -> com.palantir.javapoet.TypeName.INT;
			case "long" -> com.palantir.javapoet.TypeName.LONG;
			case "double" -> com.palantir.javapoet.TypeName.DOUBLE;
			case "boolean" -> com.palantir.javapoet.TypeName.BOOLEAN;
			case "float" -> com.palantir.javapoet.TypeName.FLOAT;
			case "short" -> com.palantir.javapoet.TypeName.SHORT;
			case "byte" -> com.palantir.javapoet.TypeName.BYTE;
			case "char" -> com.palantir.javapoet.TypeName.CHAR;
			default -> ClassName.bestGuess(typeName);
		};
	}

	private void emitComponentInstantiation(MethodSpec.Builder wire, BeanDefinition bean, ClassName beanClass,
			String varName) {
		CodeBlock args = buildConstructorArgs(bean);
		if (bean.needsProxy) {
			String implVar = varName + "_impl";
			if (bean.constructorParamTypes.isEmpty()) {
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
			if (bean.constructorParamTypes.isEmpty()) {
				wire.addStatement("$T $N = new $T()", beanClass, varName, beanClass);
			} else {
				wire.addStatement("$T $N = new $T($L)", beanClass, varName, beanClass, args);
			}
		}
	}

	private CodeBlock buildConstructorArgs(BeanDefinition bean) {
		CodeBlock.Builder args = CodeBlock.builder();
		int depIdx = 0;
		List<String> paramTypes = bean.constructorParamTypes;
		for (int i = 0; i < paramTypes.size(); i++) {
			if (i > 0)
				args.add(", ");
			String type = paramTypes.get(i);
			if (type.equals("summer.core.BeanContainer")) {
				// BeanContainer is not available during AOT build phase
				args.add("null");
			} else if (type.equals("java.util.List") && bean.listElementTypes.containsKey(i)) {
				String elementType = bean.listElementTypes.get(i);
				CodeBlock listExpr = buildListExpression(bean, elementType);
				args.add(listExpr);
				long consumed = bean.resolvedDependencies.stream().skip(depIdx)
						.filter(d -> d.qualifiedName.equals(elementType) || d.interfaceNames.contains(elementType))
						.count();
				depIdx += (int) consumed;
			} else {
				if (depIdx < bean.resolvedDependencies.size()) {
					args.add("$N", bean.resolvedDependencies.get(depIdx).variableName);
					depIdx++;
				} else {
					args.add("null");
				}
			}
		}
		return args.build();
	}

	private CodeBlock buildListExpression(BeanDefinition bean, String elementType) {
		List<BeanDefinition> listDeps = bean.resolvedDependencies.stream()
				.filter(d -> d.qualifiedName.equals(elementType) || d.interfaceNames.contains(elementType)).toList();
		if (listDeps.isEmpty()) {
			return CodeBlock.of("java.util.List.of()");
		}
		CodeBlock.Builder cb = CodeBlock.builder();
		cb.add("java.util.List.of(");
		for (int j = 0; j < listDeps.size(); j++) {
			if (j > 0)
				cb.add(", ");
			cb.add("$N", listDeps.get(j).variableName);
		}
		cb.add(")");
		return cb.build();
	}

	private void emitFactoryProductInstantiation(MethodSpec.Builder wire, BeanDefinition bean, String varName) {
		ClassName producedClass = safeClassName(bean.qualifiedName);
		String configVar = bean.configBeanDefinition.variableName;
		String methodName = bean.producerMethodName;
		CodeBlock args = buildArgs(bean.resolvedDependencies);

		if (bean.producerParamTypes.isEmpty()) {
			wire.addStatement("$T $N = $N.$N()", producedClass, varName, configVar, methodName);
		} else {
			wire.addStatement("$T $N = $N.$N($L)", producedClass, varName, configVar, methodName, args);
		}
	}

	private void emitConfigPropertiesInstantiation(MethodSpec.Builder wire, ConfigPropertiesBean bean,
			ClassName beanClass, String varName) {
		ClassName configBinder = ClassName.get("summer.core.config", "ConfigBinder");
		wire.addStatement("$T $N = $T.bind($S, $T.class)", beanClass, varName, configBinder,
				bean.configPropertiesPrefix != null ? bean.configPropertiesPrefix : "", beanClass);
	}

	private CodeBlock buildArgs(List<BeanDefinition> deps) {
		CodeBlock.Builder args = CodeBlock.builder();
		for (int i = 0; i < deps.size(); i++) {
			if (i > 0)
				args.add(", ");
			args.add("$N", deps.get(i).variableName);
		}
		return args.build();
	}

	/**
	 * Converts a JVM type name to a JavaPoet
	 * {@link com.palantir.javapoet.TypeName}. Handles primitives and nested-class
	 * {@code $} separators.
	 */
	static com.palantir.javapoet.TypeName parseTypeName(String typeName) {
		if (typeName.startsWith("["))
			return ClassName.get(Object.class);
		return switch (typeName) {
			case "int" -> com.palantir.javapoet.TypeName.INT;
			case "long" -> com.palantir.javapoet.TypeName.LONG;
			case "double" -> com.palantir.javapoet.TypeName.DOUBLE;
			case "boolean" -> com.palantir.javapoet.TypeName.BOOLEAN;
			case "float" -> com.palantir.javapoet.TypeName.FLOAT;
			case "short" -> com.palantir.javapoet.TypeName.SHORT;
			case "byte" -> com.palantir.javapoet.TypeName.BYTE;
			case "char" -> com.palantir.javapoet.TypeName.CHAR;
			default -> ClassName.bestGuess(typeName.replace('$', '.'));
		};
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
