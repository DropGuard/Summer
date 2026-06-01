package summer.plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/**
 * Shared bean discovery logic used by both Maven plugin and tests.
 */
public final class BeanDiscovery {

	private BeanDiscovery() {
	}

	/**
	 * Discover all beans from a Jandex index.
	 * 
	 * @param index
	 *            the Jandex index to scan
	 * @param packagePrefix
	 *            only discover beans in this package (null = all)
	 * @return list of discovered bean definitions
	 */
	public static List<BeanDefinition> discoverBeans(IndexView index, String packagePrefix) {
		List<BeanDefinition> beans = new ArrayList<>();
		Set<String> collected = new HashSet<>();

		DotName componentDot = DotName.createSimple("summer.core.Component");
		DotName configDot = DotName.createSimple("summer.core.annotation.Configuration");
		DotName beanDot = DotName.createSimple("summer.core.annotation.Bean");

		// Phase 1: @Component and @Configuration beans
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation())
				continue;
			if (packagePrefix != null && !ci.name().toString().startsWith(packagePrefix))
				continue;

			boolean isComponent = ci.hasAnnotation(componentDot);
			boolean isConfig = ci.hasAnnotation(configDot);

			if (isComponent || isConfig) {
				String qualifiedName = ci.name().toString();
				if (collected.add(qualifiedName)) {
					BeanDefinition bean = new BeanDefinition(
							isConfig ? BeanDefinition.Kind.CONFIGURATION : BeanDefinition.Kind.COMPONENT, qualifiedName,
							ci.simpleName());

					collectConstructorParams(bean, ci);
					collectInterfaces(bean, ci, index);
					beans.add(bean);
				}
			}
		}

		// Phase 2: Meta-annotated components (e.g., @RestController)
		for (ClassInfo ci : index.getKnownClasses()) {
			if (!ci.isAnnotation() || !ci.hasAnnotation(componentDot))
				continue;

			DotName metaAnnotationName = ci.name();
			for (ClassInfo usage : index.getKnownClasses()) {
				if (usage.isAnnotation() || !usage.hasAnnotation(metaAnnotationName))
					continue;
				if (packagePrefix != null && !usage.name().toString().startsWith(packagePrefix))
					continue;

				String qualifiedName = usage.name().toString();
				if (collected.add(qualifiedName)) {
					BeanDefinition bean = new BeanDefinition(BeanDefinition.Kind.COMPONENT, qualifiedName,
							usage.simpleName());

					collectConstructorParams(bean, usage);
					collectInterfaces(bean, usage, index);
					beans.add(bean);
				}
			}
		}

		// Phase 3: @Bean factory methods in @Configuration classes
		for (BeanDefinition configBean : new ArrayList<>(beans)) {
			if (configBean.kind != BeanDefinition.Kind.CONFIGURATION)
				continue;

			ClassInfo configCi = index.getClassByName(DotName.createSimple(configBean.qualifiedName));
			if (configCi == null)
				continue;

			for (org.jboss.jandex.MethodInfo method : configCi.methods()) {
				if (!method.hasAnnotation(beanDot))
					continue;

				org.jboss.jandex.Type returnType = method.returnType();
				if (returnType == null)
					continue;

				String returnTypeName = returnType.name().toString();
				if (collected.add(returnTypeName)) {
					BeanDefinition factoryBean = new BeanDefinition(BeanDefinition.Kind.FACTORY_PRODUCT, returnTypeName,
							returnType.name().withoutPackagePrefix());
					factoryBean.configClassName = configBean.qualifiedName;
					factoryBean.producerMethodName = method.name();

					for (int i = 0; i < method.parametersCount(); i++) {
						factoryBean.producerParamTypes.add(method.parameterType(i).name().toString());
					}
					beans.add(factoryBean);
				}
			}
		}

		return beans;
	}

	private static void collectConstructorParams(BeanDefinition bean, ClassInfo ci) {
		org.jboss.jandex.MethodInfo ctor = ci.firstMethod("<init>");
		if (ctor == null)
			return;

		for (int i = 0; i < ctor.parametersCount(); i++) {
			bean.constructorParamTypes.add(ctor.parameterType(i).name().toString());

			// Detect List<T>
			org.jboss.jandex.Type paramType = ctor.parameterType(i);
			if (paramType.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
				org.jboss.jandex.ParameterizedType pt = paramType.asParameterizedType();
				if (pt.name().toString().equals("java.util.List") && pt.arguments().size() == 1) {
					String elementType = pt.arguments().get(0).name().toString();
					bean.listElementTypes.put(bean.constructorParamTypes.size() - 1, elementType);
				}
			}
		}
	}

	private static void collectInterfaces(BeanDefinition bean, ClassInfo ci, IndexView index) {
		collectInterfacesRecursive(bean, ci, index, new HashSet<>());
	}

	private static void collectInterfacesRecursive(BeanDefinition bean, ClassInfo ci, IndexView index,
			Set<String> visited) {
		for (org.jboss.jandex.Type iface : ci.interfaceTypes()) {
			String ifaceName = iface.name().toString();
			if (visited.add(ifaceName)) {
				bean.interfaceNames.add(ifaceName);
				// Recursively collect parent interfaces
				ClassInfo ifaceCi = index.getClassByName(iface.name());
				if (ifaceCi != null) {
					collectInterfacesRecursive(bean, ifaceCi, index, visited);
				}
			}
		}
	}
}
