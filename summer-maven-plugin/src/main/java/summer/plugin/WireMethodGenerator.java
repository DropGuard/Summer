package summer.plugin;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the {@code wire()} method body for {@code GeneratedAotContext}.
 * 
 * <p>
 * Handles bean instantiation, dependency injection, and AOP proxy wiring.
 * </p>
 */
final class WireMethodGenerator {

	private final AotContextGenerator context;

	WireMethodGenerator(AotContextGenerator context) {
		this.context = context;
	}

	void generateWireMethod(MethodSpec.Builder wire, List<BeanDefinition> sortedBeans) {
		for (int i = 0; i < sortedBeans.size(); i++) {
			BeanDefinition bean = sortedBeans.get(i);
			ClassName beanClass = ClassName.bestGuess(bean.qualifiedName);
			String varName = bean.variableName;

			if (i > 0) {
				wire.addCode("\n");
			}

			if (bean instanceof ComponentBean cb) {
				emitComponentInstantiation(wire, cb, beanClass, varName);
			} else if (bean instanceof FactoryBean fb) {
				emitFactoryProductInstantiation(wire, fb, varName);
			} else if (bean instanceof ConfigPropertiesBean cpb) {
				emitConfigPropertiesInstantiation(wire, cpb, beanClass, varName);
			}

			if (bean instanceof ComponentBean cb) {
				if (cb.needsProxy) {
					wire.addStatement("singletons.put($T.class, $N)", beanClass, varName + "_impl");
				} else {
					wire.addStatement("singletons.put($T.class, $N)", beanClass, varName);
				}
				for (String iface : cb.interfaceNames) {
					wire.addStatement("singletons.putIfAbsent($T.class, $N)", context.parseTypeName(iface), varName);
				}
				if (cb.isAutoCloseable) {
					wire.addStatement("closeables.add($N)", varName);
				}
			} else {
				wire.addStatement("singletons.put($T.class, $N)", beanClass, varName);
			}
		}

		// Validation Phase: run all Validator beans
		wire.addCode("\n");
		wire.addComment("Validation Phase");
		wire.beginControlFlow("for (Object bean : singletons.values())");
		wire.beginControlFlow("if (bean instanceof $T validator)",
				ClassName.get("summer.core.validation", "Validator"));
		wire.addStatement("$T target = singletons.get(validator.targetType())", ClassName.get(Object.class));
		wire.beginControlFlow("if (target != null)");
		wire.addStatement("validator.validate(target)");
		wire.endControlFlow();
		wire.endControlFlow();
		wire.endControlFlow();
	}

	private void emitComponentInstantiation(MethodSpec.Builder wire, BeanDefinition bean, ClassName beanClass,
			String varName) {
		if (bean instanceof ComponentBean cb) {
			CodeBlock args = buildConstructorArgs(cb);
			if (cb.needsProxy) {
				String implVar = varName + "_impl";
				if (cb.constructorParamTypes.isEmpty()) {
					wire.addStatement("$T $N = new $T()", beanClass, implVar, beanClass);
				} else {
					wire.addStatement("$T $N = new $T($L)", beanClass, implVar, beanClass, args);
				}

				String interceptorsListVar = varName + "_interceptors";
				wire.addStatement("$T<$T> $N = new $T<>()", ClassName.get(List.class),
						ClassName.get("summer.aop", "MethodInterceptor"), interceptorsListVar,
						ClassName.get(ArrayList.class));

				for (BeanDefinition interceptor : cb.interceptors) {
					wire.addStatement("$N.add($N)", interceptorsListVar, interceptor.variableName);
				}

				com.palantir.javapoet.TypeName proxyType = cb.interfaceNames.isEmpty()
						? beanClass
						: ClassName.bestGuess(cb.interfaceNames.get(0));
				ClassName proxyClass = ClassName.get(beanClass.packageName(), beanClass.simpleName() + "$$AotProxy");
				wire.addStatement("$T $N = new $T($N, $N)", proxyType, varName, proxyClass, implVar,
						interceptorsListVar);
			} else {
				if (cb.constructorParamTypes.isEmpty()) {
					wire.addStatement("$T $N = new $T()", beanClass, varName, beanClass);
				} else {
					wire.addStatement("$T $N = new $T($L)", beanClass, varName, beanClass, args);
				}
			}
		} else {
			// No constructor params — no-arg constructor
			wire.addStatement("$T $N = new $T()", beanClass, varName, beanClass);
		}
	}

	private CodeBlock buildConstructorArgs(ComponentBean bean) {
		CodeBlock.Builder args = CodeBlock.builder();
		int depIdx = 0;
		List<String> paramTypes = bean.constructorParamTypes;
		for (int i = 0; i < paramTypes.size(); i++) {
			if (i > 0)
				args.add(", ");
			String type = paramTypes.get(i);
			if (type.equals("summer.core.ApplicationContext")) {
				args.add("this");
			} else if (type.equals("java.util.List") && bean.listElementTypes.containsKey(i)) {
				String elementType = bean.listElementTypes.get(i);
				CodeBlock listExpr = buildListExpression(bean, elementType);
				args.add(listExpr);
				long consumed = bean.resolvedDependencies.stream().skip(depIdx)
						.filter(d -> d.qualifiedName.equals(elementType)
								|| (d instanceof ComponentBean cb && cb.interfaceNames.contains(elementType)))
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

	private CodeBlock buildListExpression(ComponentBean bean, String elementType) {
		List<BeanDefinition> listDeps = bean.resolvedDependencies.stream()
				.filter(d -> d.qualifiedName.equals(elementType)
						|| (d instanceof ComponentBean cb && cb.interfaceNames.contains(elementType)))
				.toList();
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

	private void emitFactoryProductInstantiation(MethodSpec.Builder wire, FactoryBean bean, String varName) {
		ClassName producedClass = ClassName.bestGuess(bean.qualifiedName);
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
}
