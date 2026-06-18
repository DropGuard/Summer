package summer.aot.generator;

import java.util.ArrayList;
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

/**
 * Enriches discovered bean definitions with constructor params, interface
 * names, route metadata, and AOP binding information.
 *
 * <p>
 * This is Phase 4 of the discovery pipeline — runs after condition evaluation.
 * </p>
 */
public final class BeanEnrichment {

	private static final DotName REST_CONTROLLER_DOT = DotName.createSimple("summer.web.annotation.RestController");
	private static final DotName PATH_PARAM_DOT = DotName.createSimple("summer.web.annotation.PathParam");
	private static final DotName QUERY_PARAM_DOT = DotName.createSimple("summer.web.annotation.QueryParam");
	private static final DotName VALID_DOT = DotName.createSimple("jakarta.validation.Valid");
	private static final DotName PAGEABLE_DOT = DotName.createSimple("summer.web.Pageable");
	private static final DotName INTERCEPTOR_BINDING_DOT = DotName.createSimple("summer.aop.InterceptorBinding");
	private static final DotName INTERCEPTOR_DOT = DotName.createSimple("summer.aop.Interceptor");

	private static final Map<DotName, String> HTTP_ANNOTATIONS = Map.of(
			DotName.createSimple("summer.web.annotation.Get"), "GET",
			DotName.createSimple("summer.web.annotation.Post"), "POST",
			DotName.createSimple("summer.web.annotation.Put"), "PUT",
			DotName.createSimple("summer.web.annotation.Delete"), "DELETE");

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
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;
			if (bean instanceof ComponentBean cb) {
				collectConstructorParams(cb, ci);
			}
			collectInterfaces(bean, ci);
		}
		collectRouteMetadata(beans);
		detectAopBindings(beans);
	}

	// ── Constructor Params ────────────────────────────────────────────

	private void collectConstructorParams(ComponentBean bean, ClassInfo ci) {
		List<MethodInfo> publicCtors = ci.methods().stream()
				.filter(m -> m.name().equals("<init>") && (m.flags() & 0x0001) != 0).toList();
		if (publicCtors.isEmpty()) {
			throw new summer.core.exception.BeanCreationException(summer.core.ErrorCode.BEAN_CREATION_FAILED,
					"Component " + bean.qualifiedName + " must have exactly ONE public constructor. Found: 0");
		}
		if (publicCtors.size() > 1) {
			throw new summer.core.exception.BeanCreationException(summer.core.ErrorCode.BEAN_CREATION_FAILED,
					"Component " + bean.qualifiedName + " must have exactly ONE public constructor. Found: "
							+ publicCtors.size());
		}
		MethodInfo ctor = publicCtors.get(0);
		for (int i = 0; i < ctor.parametersCount(); i++) {
			bean.constructorParamTypes.add(ctor.parameterType(i).name().toString());

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

	// ── Interfaces ────────────────────────────────────────────────────

	private void collectInterfaces(BeanDefinition bean, ClassInfo ci) {
		collectInterfacesRecursive(bean, ci, new HashSet<>());
	}

	private void collectInterfacesRecursive(BeanDefinition bean, ClassInfo ci, Set<String> visited) {
		for (org.jboss.jandex.Type iface : ci.interfaceTypes()) {
			String ifaceName = iface.name().toString();
			if (visited.add(ifaceName)) {
				bean.interfaceNames.add(ifaceName);
				ClassInfo ifaceCi = index.getClassByName(iface.name());
				if (ifaceCi != null) {
					collectInterfacesRecursive(bean, ifaceCi, visited);
				}
			}
		}
	}

	// ── Route Metadata ────────────────────────────────────────────────

	private void collectRouteMetadata(List<BeanDefinition> beans) {
		for (BeanDefinition bean : beans) {
			if (!(bean instanceof ComponentBean cb))
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
				cb.routes.add(route);
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

	private void collectParameters(MethodInfo method, RouteInfo route) {
		for (org.jboss.jandex.MethodParameterInfo param : method.parameters()) {
			String paramName = param.name();
			String paramType = param.type().name().toString();
			boolean hasValid = param.hasAnnotation(VALID_DOT);

			if (paramType.equals("summer.web.WebContext") || paramType.equals("summer.web.HttpContext")) {
				// ctx is the handler lambda parameter — always available, always passed last
			} else if (param.hasAnnotation(PATH_PARAM_DOT)) {
				String bindingName = extractBindingName(param, PATH_PARAM_DOT, paramName);
				route.params
						.add(new RouteInfo.ParamInfo(bindingName, paramType, RouteInfo.ParamBinding.PATH, hasValid));
			} else if (param.hasAnnotation(QUERY_PARAM_DOT)) {
				String bindingName = extractBindingName(param, QUERY_PARAM_DOT, paramName);
				route.params
						.add(new RouteInfo.ParamInfo(bindingName, paramType, RouteInfo.ParamBinding.QUERY, hasValid));
			} else if (paramType.equals("summer.web.Pageable") || param.type().name().equals(PAGEABLE_DOT)) {
				route.params.add(new RouteInfo.ParamInfo(paramName, paramType, RouteInfo.ParamBinding.PAGEABLE, false));
			} else {
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

		// Step 3: For each ComponentBean, check if it needs a proxy, match
		// interceptors, and collect proxy method signatures
		for (BeanDefinition bean : beans) {
			if (!(bean instanceof ComponentBean cb))
				continue;

			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;

			// Skip interceptors — they are not proxy targets
			if (ci.hasAnnotation(INTERCEPTOR_DOT))
				continue;

			// Check class-level bindings
			Set<DotName> targetBindings = new HashSet<>();
			for (AnnotationInstance ann : ci.declaredAnnotations()) {
				if (bindingAnnotations.contains(ann.name())) {
					targetBindings.add(ann.name());
					cb.needsProxy = true;
					cb.classLevelBinding = true;
				}
			}

			// Check method-level bindings
			Set<String> methodLevelBindingMethods = new HashSet<>();
			if (!cb.needsProxy) {
				for (MethodInfo method : ci.methods()) {
					for (AnnotationInstance ann : method.annotations()) {
						if (bindingAnnotations.contains(ann.name())) {
							targetBindings.add(ann.name());
							methodLevelBindingMethods.add(method.name());
							cb.needsProxy = true;
							break;
						}
					}
				}
			}

			// Match interceptors
			if (cb.needsProxy) {
				for (var entry : interceptorBindings.entrySet()) {
					for (DotName binding : entry.getValue()) {
						if (targetBindings.contains(binding)) {
							cb.interceptors.add(entry.getKey());
							break;
						}
					}
				}
			}

			// Collect proxy method signatures from interfaces
			if (cb.needsProxy) {
				collectProxyMethods(cb, ci, bindingAnnotations, methodLevelBindingMethods);
			}
		}
	}

	private void collectProxyMethods(ComponentBean cb, ClassInfo targetCi, Set<DotName> bindingAnnotations,
			Set<String> methodLevelBindingMethods) {
		Set<String> visitedMethods = new HashSet<>();
		for (String ifaceName : cb.interfaceNames) {
			ClassInfo ifaceCi = index.getClassByName(DotName.createSimple(ifaceName));
			if (ifaceCi == null)
				continue;

			for (MethodInfo method : ifaceCi.methods()) {
				if (!method.isAbstract())
					continue;
				String key = method.name() + method.parametersCount();
				if (!visitedMethods.add(key))
					continue;

				boolean shouldIntercept = cb.classLevelBinding || methodLevelBindingMethods.contains(method.name());

				List<ComponentBean.ProxyMethodInfo.ParamInfo> params = new ArrayList<>();
				int paramIdx = 0;
				for (var param : method.parameters()) {
					String name = param.name();
					if (name == null)
						name = "arg" + paramIdx;
					params.add(new ComponentBean.ProxyMethodInfo.ParamInfo(name, param.type().name().toString()));
					paramIdx++;
				}

				List<String> exceptions = new ArrayList<>();
				for (var ex : method.exceptions()) {
					exceptions.add(ex.name().toString());
				}

				String returnType = method.returnType() != null ? method.returnType().name().toString() : "void";
				cb.proxyMethods.add(new ComponentBean.ProxyMethodInfo(method.name(), returnType, params, exceptions,
						shouldIntercept));
			}
		}
	}
}
