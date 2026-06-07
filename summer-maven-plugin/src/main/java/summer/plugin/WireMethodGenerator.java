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

			switch (bean.kind) {
				case COMPONENT, CONFIGURATION -> emitComponentInstantiation(wire, bean, beanClass, varName);
				case FACTORY_PRODUCT -> emitFactoryProductInstantiation(wire, bean, varName);
				case CONFIG_PROPERTIES -> emitConfigPropertiesInstantiation(wire, bean, beanClass, varName);
			}

			if (bean.needsProxy) {
				wire.addStatement("singletons.put($T.class, $N)", beanClass, varName + "_impl");
			} else {
				wire.addStatement("singletons.put($T.class, $N)", beanClass, varName);
			}
			for (String iface : bean.interfaceNames) {
				wire.addStatement("singletons.putIfAbsent($T.class, $N)", context.parseTypeName(iface), varName);
			}

			if (bean.isAutoCloseable) {
				wire.addStatement("closeables.add($N)", varName);
			}
		}
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
				wire.beginControlFlow("if ($N.supports($T.class))", interceptor.variableName, beanClass)
						.addStatement("$N.add($N)", interceptorsListVar, interceptor.variableName).endControlFlow();
			}

			com.palantir.javapoet.TypeName proxyType = bean.interfaceNames.isEmpty()
					? beanClass
					: ClassName.bestGuess(bean.interfaceNames.get(0));
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
			if (type.equals("summer.core.ApplicationContext")) {
				args.add("this");
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

	private void emitConfigPropertiesInstantiation(MethodSpec.Builder wire, BeanDefinition bean,
			ClassName beanClass, String varName) {
		ClassName binder = ClassName.get("summer.core.config", "ConfigurationBinder");
		String prefix = bean.configPropertiesPrefix;
		if (prefix == null || prefix.isEmpty()) {
			wire.addStatement("$T $N = $T.bind($S, $T.class)", beanClass, varName, binder, "application.yml", beanClass);
		} else {
			wire.addStatement("$T $N = $T.bind($S, $T.class, $S)", beanClass, varName, binder, "application.yml", beanClass, prefix);
		}
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
