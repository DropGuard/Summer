package summer.aot;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import summer.core.bean.BeanDefinition;
import summer.core.bean.ConfigPropertiesBean;
import summer.core.bean.RouteInfo;

/**
 * Enriches discovered bean definitions with constructor params, interface
 * names, route metadata, and AOP binding information.
 *
 * <p>
 * This is Phase 4 of the discovery pipeline — runs after condition evaluation.
 * </p>
 */
final class BeanEnrichment {

	private static final DotName REST_CONTROLLER_DOT = DotName.createSimple("summer.web.annotation.RestController");
	private static final DotName PATH_PARAM_DOT = DotName.createSimple("summer.web.annotation.PathParam");
	private static final DotName QUERY_PARAM_DOT = DotName.createSimple("summer.web.annotation.QueryParam");
	private static final DotName VALID_DOT = DotName.createSimple("jakarta.validation.Valid");
	private static final DotName INTERCEPTOR_BINDING_DOT = DotName.createSimple("summer.aop.InterceptorBinding");
	private static final DotName INTERCEPTOR_DOT = DotName.createSimple("summer.aop.Interceptor");

	private static final Map<DotName, String> HTTP_ANNOTATIONS = Map.of(
			DotName.createSimple("summer.web.annotation.Get"), "GET",
			DotName.createSimple("summer.web.annotation.Post"), "POST",
			DotName.createSimple("summer.web.annotation.Put"), "PUT",
			DotName.createSimple("summer.web.annotation.Delete"), "DELETE");

	private static final DotName EXCEPTION_HANDLER_DOT = DotName.createSimple("summer.web.annotation.ExceptionHandler");
	private static final DotName CONDITIONAL_ON_BEAN_DOT = DotName.createSimple("summer.core.annotation.ConditionalOnBean");

	private final IndexView index;

	BeanEnrichment(IndexView index) {
		this.index = index;
	}

	/**
	 * Enriches beans with constructor params, interface names, route metadata, and
	 * AOP bindings.
	 */
	void enrich(List<BeanDefinition> beans) {
		for (BeanDefinition bean : beans) {
			if (bean instanceof ConfigPropertiesBean)
				continue;
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci != null) {
				if (!bean.isFactoryMethod()) {
					collectConstructorParams(bean, ci);
				}
				// interfaces are now collected during BeanDiscovery.createBaseDefinition
				// collectInterfaces(bean, ci);
				collectExceptionHandlers(bean, ci);
				collectConditions(bean, ci);
			}
		}
		collectRouteMetadata(beans);
		detectAopBindings(beans);
	}

	// ── Constructor Params ────────────────────────────────────────────

	private void collectConstructorParams(BeanDefinition bean, ClassInfo ci) {
		List<MethodInfo> publicCtors = ci.methods().stream()
				.filter(m -> m.name().equals("<init>") && (m.flags() & 0x0001) != 0).toList();
		if (publicCtors.isEmpty()) {
			throw new summer.core.exception.BeanCreationException(
					"Component " + bean.qualifiedName + " must have exactly ONE public constructor. Found: 0");
		}
		if (publicCtors.size() > 1) {
			throw new summer.core.exception.BeanCreationException("Component " + bean.qualifiedName
					+ " must have exactly ONE public constructor. Found: " + publicCtors.size());
		}
		MethodInfo ctor = publicCtors.get(0);
		for (int i = 0; i < ctor.parametersCount(); i++) {
			bean.constructorParamTypes.add(ctor.parameterType(i).name().toString());

			org.jboss.jandex.Type paramType = ctor.parameterType(i);
			if (paramType.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
				org.jboss.jandex.ParameterizedType pt = paramType.asParameterizedType();
				if (pt.name().toString().equals("java.util.List") && pt.arguments().size() == 1) {
					org.jboss.jandex.Type elementTypeObj = pt.arguments().get(0);
					if (elementTypeObj.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
						throw new summer.core.exception.UnsupportedInjectionException(
								"Nested generic type injection is not supported: List<" + elementTypeObj.toString()
										+ "> in " + bean.qualifiedName);
					}
					String elementType = pt.arguments().get(0).name().toString();
					bean.listElementTypes.put(bean.constructorParamTypes.size() - 1, elementType);
				}
			}
		}
	}

	// ── Interfaces ────────────────────────────────────────────────────
	// (Interfaces are collected natively in BeanDiscovery during creation)

	// ── Route Metadata ────────────────────────────────────────────────

	private void collectRouteMetadata(List<BeanDefinition> beans) {
		for (BeanDefinition bean : beans) {
			if (bean instanceof ConfigPropertiesBean)
				continue;

			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null || !ci.hasAnnotation(REST_CONTROLLER_DOT))
				continue;

			String basePath = extractBasePath(ci);

			for (MethodInfo method : ci.methods()) {
				String httpMethod = resolveHttpMethod(method);
				if (httpMethod == null)
					continue;

				String methodPath = extractMethodPath(method);
				String fullPath = combinePaths(basePath, methodPath);
				String returnType = method.returnType().name().toString();

				RouteInfo route = new RouteInfo(httpMethod, fullPath, bean.qualifiedName, method.name(), returnType);
				collectParameters(method, route);

				// Enforce Gin-style contract: first parameter MUST be HttpContext
				var params = method.parameters();
				if (params.isEmpty() || !params.get(0).type().name().toString().equals("summer.web.HttpContext")) {
					throw new IllegalStateException(bean.qualifiedName + "." + method.name()
							+ "() must declare HttpContext as its first parameter. "
							+ "All controller methods follow the Gin pattern: " + "first arg is always the context.");
				}

				bean.routes.add(route);
			}
		}
	}

	private String extractBasePath(ClassInfo ci) {
		AnnotationInstance ann = ci.annotation(REST_CONTROLLER_DOT);
		return (ann != null && ann.value() != null) ? ann.value().asString() : "";
	}

	private String resolveHttpMethod(MethodInfo method) {
		for (var entry : HTTP_ANNOTATIONS.entrySet()) {
			if (method.hasAnnotation(entry.getKey()))
				return entry.getValue();
		}
		return null;
	}

	private String extractMethodPath(MethodInfo method) {
		for (DotName annotation : HTTP_ANNOTATIONS.keySet()) {
			if (method.hasAnnotation(annotation)) {
				AnnotationInstance ann = method.annotation(annotation);
				return (ann != null && ann.value() != null) ? ann.value().asString() : "";
			}
		}
		return "";
	}

	private boolean isScrollRequest(String paramType) {
		if (paramType.equals("summer.web.ScrollRequest")
				|| paramType.equals("summer.realworld.common.LimitOffsetPageable")
				|| paramType.equals("summer.twitter.common.CursorPageable")) {
			return true;
		}
		ClassInfo ci = index.getClassByName(DotName.createSimple(paramType));
		if (ci != null) {
			for (DotName iface : ci.interfaceNames()) {
				if (isScrollRequest(iface.toString())) {
					return true;
				}
			}
			if (ci.superName() != null && !ci.superName().toString().equals("java.lang.Object")) {
				return isScrollRequest(ci.superName().toString());
			}
		}
		return false;
	}

	private void collectParameters(MethodInfo method, RouteInfo route) {
		for (org.jboss.jandex.MethodParameterInfo param : method.parameters()) {
			String paramName = param.name();
			String paramType = param.type().name().toString();
			boolean hasValid = param.hasAnnotation(VALID_DOT);

			if (param.hasAnnotation(PATH_PARAM_DOT)) {
				String bindingName = extractBindingName(param, PATH_PARAM_DOT, paramName);
				route.params
						.add(new RouteInfo.ParamInfo(bindingName, paramType, RouteInfo.ParamBinding.PATH, hasValid));
			} else if (param.hasAnnotation(QUERY_PARAM_DOT)) {
				String bindingName = extractBindingName(param, QUERY_PARAM_DOT, paramName);
				route.params
						.add(new RouteInfo.ParamInfo(bindingName, paramType, RouteInfo.ParamBinding.QUERY, hasValid));
			} else if (isScrollRequest(paramType)) {
				route.params.add(new RouteInfo.ParamInfo(paramName, paramType, RouteInfo.ParamBinding.PAGEABLE, false));
			} else if (!paramType.equals("summer.web.WebContext") && !paramType.equals("summer.web.HttpContext")) {
				route.params.add(new RouteInfo.ParamInfo(paramName, paramType, RouteInfo.ParamBinding.BODY, hasValid));
			}
		}
	}

	private String extractBindingName(org.jboss.jandex.MethodParameterInfo param, DotName annotation,
			String defaultName) {
		AnnotationInstance ann = param.annotation(annotation);
		return (ann != null && ann.value() != null) ? ann.value().asString() : defaultName;
	}

	private String combinePaths(String base, String method) {
		if (base.isEmpty())
			return method;
		if (method.isEmpty())
			return base;
		String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		String normalizedMethod = method.startsWith("/") ? method : "/" + method;
		return normalizedBase + normalizedMethod;
	}

	// ── AOP Bindings ──────────────────────────────────────────────────

	private void detectAopBindings(List<BeanDefinition> beans) {
		// Step 1: Collect all binding annotations (@InterceptorBinding-annotated)
		Set<DotName> bindingAnnotations = new HashSet<>();
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() && ci.hasAnnotation(INTERCEPTOR_BINDING_DOT)) {
				bindingAnnotations.add(ci.name());
			}
		}

		// Step 2: Identify interceptor beans and their binding annotations
		Map<BeanDefinition, Set<DotName>> interceptorBindings = new LinkedHashMap<>();
		for (BeanDefinition bean : beans) {
			if (bean instanceof ConfigPropertiesBean)
				continue;
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null || !ci.hasAnnotation(INTERCEPTOR_DOT))
				continue;
			Set<DotName> bindings = new HashSet<>();
			for (AnnotationInstance ann : ci.declaredAnnotations()) {
				if (bindingAnnotations.contains(ann.name())) {
					bindings.add(ann.name());
				}
			}
			if (!bindings.isEmpty()) {
				interceptorBindings.put(bean, bindings);
			}
		}

		// Step 3: Populate interceptorBindingAnnotations and match interceptors
		for (BeanDefinition bean : beans) {
			if (bean instanceof ConfigPropertiesBean)
				continue;

			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;

			// Skip interceptors — they are not proxy targets
			if (ci.hasAnnotation(INTERCEPTOR_DOT))
				continue;

			// Collect class-level bindings
			Set<String> bindings = new HashSet<>();
			Set<DotName> targetBindings = new HashSet<>();
			for (AnnotationInstance ann : ci.declaredAnnotations()) {
				if (bindingAnnotations.contains(ann.name())) {
					String name = ann.name().toString();
					bindings.add(name);
					targetBindings.add(ann.name());
				}
			}

			// Collect method-level bindings
			Map<String, Set<String>> methodBindings = new java.util.LinkedHashMap<>();
			if (targetBindings.isEmpty()) {
				for (MethodInfo method : ci.methods()) {
					Set<String> methodAnnNames = new HashSet<>();
					for (AnnotationInstance ann : method.annotations()) {
						if (bindingAnnotations.contains(ann.name())) {
							String name = ann.name().toString();
							methodAnnNames.add(name);
							bindings.add(name);
						}
					}
					if (!methodAnnNames.isEmpty()) {
						methodBindings.put(method.name(), methodAnnNames);
					}
				}
			}

			// Populate BeanDefinition enrichment fields (sets needsProxy() implicitly)
			bean.interceptorBindingAnnotations = bindings.isEmpty() ? Set.of() : Set.copyOf(bindings);
			if (!methodBindings.isEmpty()) {
				bean.methodBindingAnnotations = methodBindings;
			}

			// Match interceptors when bindings exist
			if (!bindings.isEmpty()) {
				for (var entry : interceptorBindings.entrySet()) {
					for (DotName binding : entry.getValue()) {
						if (targetBindings.contains(binding) || methodBindings.values().stream()
								.anyMatch(ms -> ms.contains(binding.toString()))) {
							bean.interceptors.add(entry.getKey());
							break;
						}
					}
				}
			}
		}
	}

	// ── @ExceptionHandler collection ───────────────────────────────────

	private void collectExceptionHandlers(BeanDefinition bean, ClassInfo ci) {
		for (MethodInfo method : ci.methods()) {
			AnnotationInstance ann = method.annotation(EXCEPTION_HANDLER_DOT);
			if (ann != null) {
				String exClass = ann.value().asClass().name().toString();
				bean.exceptionHandlerMethods.add(
						new BeanDefinition.ExceptionHandlerEntry(method.name(), exClass,
								method.parametersCount()));
			}
		}
	}

	// ── @ConditionalOnBean collection ──────────────────────────────────

	private void collectConditions(BeanDefinition bean, ClassInfo ci) {
		AnnotationInstance ann = ci.annotation(CONDITIONAL_ON_BEAN_DOT);
		if (ann != null) {
			bean.conditionalOnBeanType = ann.value().asClass().name().toString();
		}
	}
}
