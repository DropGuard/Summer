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

	private static final DotName COMPONENT_DOT = DotName.createSimple("summer.core.Component");
	private static final DotName CONFIG_DOT = DotName.createSimple("summer.core.annotation.Configuration");
	private static final DotName BEAN_DOT = DotName.createSimple("summer.core.annotation.Bean");
	private static final DotName CONFIG_PROPERTIES_DOT = DotName.createSimple("summer.core.config.ConfigurationProperties");
	private static final DotName REST_CONTROLLER_DOT = DotName.createSimple("summer.web.annotation.RestController");
	private static final DotName GLOBAL_MIDDLEWARE_DOT = DotName.createSimple("summer.web.annotation.GlobalMiddleware");
	private static final DotName INTERCEPTOR_BINDING_DOT = DotName.createSimple("summer.aop.InterceptorBinding");
	private static final DotName PATH_PARAM_DOT = DotName.createSimple("summer.web.annotation.PathParam");
	private static final DotName QUERY_PARAM_DOT = DotName.createSimple("summer.web.annotation.QueryParam");
	private static final DotName VALID_DOT = DotName.createSimple("jakarta.validation.Valid");
	private static final DotName PAGEABLE_DOT = DotName.createSimple("summer.web.Pageable");
	private static final DotName REPLACES_DOT = DotName.createSimple("summer.core.annotation.Replaces");

	private static final java.util.Map<DotName, String> HTTP_ANNOTATIONS = java.util.Map.of(
			DotName.createSimple("summer.web.annotation.Get"), "GET",
			DotName.createSimple("summer.web.annotation.Post"), "POST",
			DotName.createSimple("summer.web.annotation.Put"), "PUT",
			DotName.createSimple("summer.web.annotation.Delete"), "DELETE");

	private BeanDiscovery() {
	}

	public static List<BeanDefinition> discoverBeans(IndexView index, String packagePrefix) {
		List<BeanDefinition> beans = new ArrayList<>();
		Set<String> collected = new HashSet<>();

		scanDirectComponents(index, packagePrefix, beans, collected);
		scanMetaAnnotatedComponents(index, packagePrefix, beans, collected);
		scanBeanFactoryMethods(index, beans, collected);
		scanConfigurationProperties(index, packagePrefix, beans, collected);
		scanMethodLevelReplaces(index, beans);
		collectRouteMetadata(beans, index);
		detectAopBindings(beans, index);

		return beans;
	}

	private static void scanDirectComponents(IndexView index, String packagePrefix,
			List<BeanDefinition> beans, Set<String> collected) {
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() || !matchesPackage(ci, packagePrefix))
				continue;

			boolean isComponent = ci.hasAnnotation(COMPONENT_DOT);
			boolean isConfig = ci.hasAnnotation(CONFIG_DOT);

			if (isComponent || isConfig) {
				addBean(ci, isConfig ? BeanDefinition.Kind.CONFIGURATION : BeanDefinition.Kind.COMPONENT,
						beans, collected, index);
			}
		}
	}

	private static void scanMetaAnnotatedComponents(IndexView index, String packagePrefix,
			List<BeanDefinition> beans, Set<String> collected) {
		for (ClassInfo ci : index.getKnownClasses()) {
			if (!ci.isAnnotation() || !ci.hasAnnotation(COMPONENT_DOT))
				continue;

			DotName metaAnnotationName = ci.name();
			for (ClassInfo usage : index.getKnownClasses()) {
				if (usage.isAnnotation() || !usage.hasAnnotation(metaAnnotationName))
					continue;
				if (!matchesPackage(usage, packagePrefix))
					continue;

				addBean(usage, BeanDefinition.Kind.COMPONENT, beans, collected, index);
			}
		}
	}

	private static void scanBeanFactoryMethods(IndexView index, List<BeanDefinition> beans, Set<String> collected) {
		for (BeanDefinition configBean : new ArrayList<>(beans)) {
			if (configBean.kind != BeanDefinition.Kind.CONFIGURATION)
				continue;

			ClassInfo configCi = index.getClassByName(DotName.createSimple(configBean.qualifiedName));
			if (configCi == null)
				continue;

			for (org.jboss.jandex.MethodInfo method : configCi.methods()) {
				if (!method.hasAnnotation(BEAN_DOT))
					continue;

				org.jboss.jandex.Type returnType = method.returnType();
				if (returnType == null)
					continue;

				String returnTypeName = returnType.name().toString();
				boolean hasReplaces = method.hasAnnotation(REPLACES_DOT);

				// Manual @Bean wins over auto-bound @ConfigurationProperties
				if (!hasReplaces) {
					BeanDefinition existing = findConfigProperties(beans, returnTypeName);
					if (existing != null) {
						beans.remove(existing);
						collected.remove(returnTypeName);
					}
				}

				if (collected.add(returnTypeName)) {
					// First @Bean with this return type - add normally
					BeanDefinition factoryBean = new BeanDefinition(BeanDefinition.Kind.FACTORY_PRODUCT,
							returnTypeName, returnType.name().withoutPackagePrefix());
					factoryBean.configClassName = configBean.qualifiedName;
					factoryBean.producerMethodName = method.name();

					for (int i = 0; i < method.parametersCount(); i++) {
						factoryBean.producerParamTypes.add(method.parameterType(i).name().toString());
					}
					beans.add(factoryBean);
				} else if (hasReplaces) {
				} else if (hasReplaces) {
					// Duplicate return type with @Replaces - replace the existing FACTORY_PRODUCT in-place
					// No need to set replacesReturnType: the replacement IS the new provider of this type
					BeanDefinition existing = findFactoryProduct(beans, returnTypeName);
					if (existing != null) {
						existing.configClassName = configBean.qualifiedName;
						existing.producerMethodName = method.name();
						existing.producerParamTypes.clear();
						for (int i = 0; i < method.parametersCount(); i++) {
							existing.producerParamTypes.add(method.parameterType(i).name().toString());
						}
					}
				}
			}
		}
	}

	private static BeanDefinition findFactoryProduct(List<BeanDefinition> beans, String returnTypeName) {
		for (BeanDefinition bean : beans) {
			if (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT && bean.qualifiedName.equals(returnTypeName)) {
				return bean;
			}
		}
		return null;
	}

	private static BeanDefinition findConfigProperties(List<BeanDefinition> beans, String returnTypeName) {
		for (BeanDefinition bean : beans) {
			if (bean.kind == BeanDefinition.Kind.CONFIG_PROPERTIES && bean.qualifiedName.equals(returnTypeName)) {
				return bean;
			}
		}
		return null;
	}


	/**
	 * Scans for {@code @ConfigurationProperties}-annotated records and registers
	 * them as {@code CONFIG_PROPERTIES} beans.
	 */
	private static void scanConfigurationProperties(IndexView index, String packagePrefix,
			List<BeanDefinition> beans, Set<String> collected) {
		for (org.jboss.jandex.AnnotationInstance ann : index.getAnnotations(CONFIG_PROPERTIES_DOT)) {
			ClassInfo ci = ann.target().asClass();
			if (ci.isInterface() || ci.isAbstract() || !matchesPackage(ci, packagePrefix))
				continue;

			String className = ci.name().toString();
			if (!collected.add(className))
				continue;

			String prefix = "";
			if (ann.value() != null) {
				prefix = ann.value().asString();
			}

			BeanDefinition bean = new BeanDefinition(BeanDefinition.Kind.CONFIG_PROPERTIES,
					className, ci.name().withoutPackagePrefix());
			bean.configPropertiesPrefix = prefix;
			beans.add(bean);
		}
	}


	private static boolean matchesPackage(ClassInfo ci, String packagePrefix) {
		return packagePrefix == null || ci.name().toString().startsWith(packagePrefix);
	}

	/**
	 * Scans @Bean methods for @Replaces annotations and populates the
	 * replacesReturnType and replacesTargetClass fields on BeanDefinition.
	 */
	private static void scanMethodLevelReplaces(IndexView index, List<BeanDefinition> beans) {
		for (BeanDefinition bean : beans) {
			if (bean.kind != BeanDefinition.Kind.FACTORY_PRODUCT)
				continue;
			if (bean.configClassName == null)
				continue;

			ClassInfo configCi = index.getClassByName(DotName.createSimple(bean.configClassName));
			if (configCi == null)
				continue;

			for (org.jboss.jandex.MethodInfo method : configCi.methods()) {
				if (!method.name().equals(bean.producerMethodName))
					continue;
				if (!method.hasAnnotation(REPLACES_DOT))
					continue;

				org.jboss.jandex.AnnotationInstance replacesAnn = method.annotation(REPLACES_DOT);
				if (replacesAnn == null)
					continue;

				org.jboss.jandex.Type valueType = replacesAnn.value().asClass();
				if (valueType == null)
					continue;

				String targetTypeName = valueType.name().toString();

				// Skip if the bean IS the target (already replaced in-place by scanBeanFactoryMethods)
				if (bean.qualifiedName.equals(targetTypeName))
					continue;

				bean.replacesReturnType = targetTypeName;

				// Check if the target type is a class (not an interface)
				ClassInfo targetCi = index.getClassByName(DotName.createSimple(targetTypeName));
				if (targetCi != null && !targetCi.isInterface()) {
					// If target is a concrete class, it's an explicit target class
					bean.replacesTargetClass = targetTypeName;
				}
			}
		}
	}

	private static void addBean(ClassInfo ci, BeanDefinition.Kind kind,
			List<BeanDefinition> beans, Set<String> collected, IndexView index) {
		String qualifiedName = ci.name().toString();
		if (collected.add(qualifiedName)) {
			BeanDefinition bean = new BeanDefinition(kind, qualifiedName, ci.simpleName());
			collectConstructorParams(bean, ci);
			collectInterfaces(bean, ci, index);
			beans.add(bean);
		}
	}

	/**
	 * Collect route metadata from @RestController beans.
	 * Scans for @Get, @Post, @Put, @Delete annotations and extracts
	 * path patterns and parameter bindings.
	 */
	private static void collectRouteMetadata(List<BeanDefinition> beans, IndexView index) {
		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null || !ci.hasAnnotation(REST_CONTROLLER_DOT))
				continue;

			String basePath = extractBasePath(ci);

			for (org.jboss.jandex.MethodInfo method : ci.methods()) {
				String httpMethod = resolveHttpMethod(method);
				if (httpMethod == null)
					continue;

				String methodPath = extractMethodPath(method);
				String fullPath = combinePaths(basePath, methodPath);
				String returnType = method.returnType().name().toString();

				RouteInfo route = new RouteInfo(httpMethod, fullPath, bean.qualifiedName, method.name(), returnType);
				collectParameters(method, route);
				bean.routes.add(route);
			}
		}
	}

	private static String extractBasePath(ClassInfo ci) {
		org.jboss.jandex.AnnotationInstance ann = ci.annotation(REST_CONTROLLER_DOT);
		if (ann != null && ann.value() != null) {
			return ann.value().asString();
		}
		return "";
	}

	private static String resolveHttpMethod(org.jboss.jandex.MethodInfo method) {
		for (var entry : HTTP_ANNOTATIONS.entrySet()) {
			if (method.hasAnnotation(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static String extractMethodPath(org.jboss.jandex.MethodInfo method) {
		for (DotName annotation : HTTP_ANNOTATIONS.keySet()) {
			if (method.hasAnnotation(annotation)) {
				org.jboss.jandex.AnnotationInstance ann = method.annotation(annotation);
				return ann != null && ann.value() != null ? ann.value().asString() : "";
			}
		}
		return "";
	}

	private static void collectParameters(org.jboss.jandex.MethodInfo method, RouteInfo route) {
		for (org.jboss.jandex.MethodParameterInfo param : method.parameters()) {
			String paramName = param.name();
			String paramType = param.type().name().toString();
			boolean hasValid = param.hasAnnotation(VALID_DOT);

			if (param.hasAnnotation(PATH_PARAM_DOT)) {
				String bindingName = extractBindingName(param, PAGEABLE_DOT, paramName);
				route.params.add(new RouteInfo.ParamInfo(bindingName, paramType, RouteInfo.ParamBinding.PATH, hasValid));
			} else if (param.hasAnnotation(QUERY_PARAM_DOT)) {
				String bindingName = extractBindingName(param, QUERY_PARAM_DOT, paramName);
				route.params.add(new RouteInfo.ParamInfo(bindingName, paramType, RouteInfo.ParamBinding.QUERY, hasValid));
			} else if (paramType.equals("summer.web.Pageable") || param.type().name().equals(PAGEABLE_DOT)) {
				route.params.add(new RouteInfo.ParamInfo(paramName, paramType, RouteInfo.ParamBinding.PAGEABLE, false));
			} else if (!paramType.equals("summer.web.WebContext") && !paramType.equals("summer.web.HttpContext")) {
				route.params.add(new RouteInfo.ParamInfo(paramName, paramType, RouteInfo.ParamBinding.BODY, hasValid));
			}
		}
	}

	private static String extractBindingName(org.jboss.jandex.MethodParameterInfo param, DotName annotation, String defaultName) {
		org.jboss.jandex.AnnotationInstance ann = param.annotation(annotation);
		return ann != null && ann.value() != null ? ann.value().asString() : defaultName;
	}

	private static String combinePaths(String base, String method) {
		if (base.isEmpty())
			return method;
		if (method.isEmpty())
			return base;

		String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		String normalizedMethod = method.startsWith("/") ? method : "/" + method;

		return normalizedBase + normalizedMethod;
	}

	/**
	 * Detects beans that need AOP proxies based on @InterceptorBinding annotations.
	 * 
	 * <p>
	 * A bean needs a proxy if it has any annotation that is itself annotated with
	 * {@code @InterceptorBinding}. This enables compile-time proxy generation for
	 * AOT mode.
	 * </p>
	 */
	private static void detectAopBindings(List<BeanDefinition> beans, IndexView index) {
		// First, collect all annotations that are @InterceptorBinding meta-annotations
		Set<DotName> bindingAnnotations = new HashSet<>();
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() && ci.hasAnnotation(INTERCEPTOR_BINDING_DOT)) {
				bindingAnnotations.add(ci.name());
			}
		}

		// Then, check each bean for those annotations
		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;

			// Check class-level annotations
			for (org.jboss.jandex.AnnotationInstance ann : ci.classAnnotations()) {
				if (bindingAnnotations.contains(ann.name())) {
					bean.needsProxy = true;
					break;
				}
			}

			// Check method-level annotations if class-level not found
			if (!bean.needsProxy) {
				for (org.jboss.jandex.MethodInfo method : ci.methods()) {
					for (org.jboss.jandex.AnnotationInstance ann : method.annotations()) {
						if (bindingAnnotations.contains(ann.name())) {
							bean.needsProxy = true;
							break;
						}
					}
					if (bean.needsProxy)
						break;
				}
			}
		}
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
