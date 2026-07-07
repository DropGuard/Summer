package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.RecordComponentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.bean.BeanDefinition;
import summer.core.bean.ConfigPropertiesBean;
import summer.core.bean.RouteInfo;

/**
 * Adapts runtime reflection objects (Class/Method) to the unified
 * BeanDefinition model.
 *
 * <p>
 * This is the bridge between the runtime engine and the unified metadata model.
 * The runtime engine discovers beans via reflection, but downstream algorithms
 * (condition evaluation, dependency resolution) operate on BeanDefinitions.
 * </p>
 */
public final class RuntimeBeanAdapter {

	private static final Logger log = LoggerFactory.getLogger(RuntimeBeanAdapter.class);

	private final IndexView index;

	public RuntimeBeanAdapter(IndexView index) {
		this.index = index;
	}

	/**
	 * Adapts a @Component class to a BeanDefinition.
	 */
	public BeanDefinition adaptComponent(Class<?> clazz) {
		BeanDefinition bean = new BeanDefinition(clazz.getName(), clazz.getSimpleName());

		// Constructor parameters
		Constructor<?> ctor = findSinglePublicConstructor(clazz);
		if (ctor != null) {
			for (Class<?> param : ctor.getParameterTypes()) {
				bean.constructorParamTypes.add(param.getName());
			}
			// Detect List<T> parameters
			detectListParameters(ctor, bean);
		}

		// Interfaces (recursive)
		collectInterfaces(clazz, bean.interfaceNames, new HashSet<>());

		// AOP binding flag
		bean.needsProxy = detectAopBinding(clazz);

		// Route metadata (if Controller)
		collectRoutes(clazz, bean);

		log.debug("[Summer] Adapted component: {} (interfaces={}, needsProxy={})", clazz.getSimpleName(),
				bean.interfaceNames.size(), bean.needsProxy);

		return bean;
	}

	/**
	 * Adapts a @Bean method to a BeanDefinition with factory method fields.
	 */
	public BeanDefinition adaptFactoryMethod(Method method) {
		BeanDefinition bean = new BeanDefinition(method.getReturnType().getName(),
				method.getReturnType().getSimpleName());
		bean.configClassName = method.getDeclaringClass().getName();
		bean.producerMethodName = method.getName();

		for (Class<?> param : method.getParameterTypes()) {
			bean.producerParamTypes.add(param.getName());
		}

		log.debug("[Summer] Adapted factory method: {}.{}() -> {}", method.getDeclaringClass().getSimpleName(),
				method.getName(), method.getReturnType().getSimpleName());

		return bean;
	}

	/**
	 * Adapts a @ConfigurationProperties class to a ConfigPropertiesBean.
	 */
	public ConfigPropertiesBean adaptConfigProperties(Class<?> clazz, String prefix) {
		ConfigPropertiesBean bean = new ConfigPropertiesBean(clazz.getName(), clazz.getSimpleName());
		bean.configPropertiesPrefix = prefix;

		// Extract @DefaultValue metadata from Jandex
		extractDefaultValues(clazz, bean);

		log.debug("[Summer] Adapted config properties: {} (prefix='{}')", clazz.getSimpleName(), prefix);

		return bean;
	}

	// ---- Private helpers ----

	private Constructor<?> findSinglePublicConstructor(Class<?> clazz) {
		Constructor<?>[] ctors = clazz.getConstructors();
		if (ctors.length != 1) {
			log.warn("[Summer] Component {} must have exactly ONE public constructor. Found: {}", clazz.getName(),
					ctors.length);
			return null;
		}
		return ctors[0];
	}

	private void detectListParameters(Constructor<?> ctor, BeanDefinition bean) {
		Type[] genericTypes = ctor.getGenericParameterTypes();
		for (int i = 0; i < genericTypes.length; i++) {
			if (genericTypes[i] instanceof ParameterizedType pt && pt.getRawType() == List.class) {
				Type elementType = pt.getActualTypeArguments()[0];
				if (elementType instanceof Class<?> ec) {
					bean.listElementTypes.put(i, ec.getName());
				} else if (elementType instanceof ParameterizedType) {
					throw new summer.core.exception.UnsupportedInjectionException(
							"Nested generic type injection is not supported: List<" + elementType.getTypeName() + "> in " + bean.qualifiedName);
				}
			}
		}
	}

	private void collectInterfaces(Class<?> clazz, List<String> target, Set<String> visited) {
		for (Class<?> iface : clazz.getInterfaces()) {
			if (visited.add(iface.getName())) {
				target.add(iface.getName());
				collectInterfaces(iface, target, visited);
			}
		}
		Class<?> superClass = clazz.getSuperclass();
		if (superClass != null && superClass != Object.class) {
			collectInterfaces(superClass, target, visited);
		}
	}

	private boolean detectAopBinding(Class<?> clazz) {
		// Check class-level annotations for @InterceptorBinding
		for (Annotation ann : clazz.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(summer.aop.InterceptorBinding.class)) {
				return true;
			}
		}
		// Check method-level annotations
		for (Method method : clazz.getMethods()) {
			for (Annotation ann : method.getAnnotations()) {
				if (ann.annotationType().isAnnotationPresent(summer.aop.InterceptorBinding.class)) {
					return true;
				}
			}
		}
		return false;
	}

	private void collectRoutes(Class<?> clazz, BeanDefinition bean) {
		// Check for @RestController annotation
		if (!clazz.isAnnotationPresent(summer.web.annotation.RestController.class)) {
			return;
		}

		summer.web.annotation.RestController restController = clazz
				.getAnnotation(summer.web.annotation.RestController.class);
		String basePath = restController.value();

		for (Method method : clazz.getMethods()) {
			String httpMethod = resolveHttpMethod(method);
			if (httpMethod == null) {
				continue;
			}

			String methodPath = extractMethodPath(method);
			String fullPath = combinePaths(basePath, methodPath);
			String returnType = method.getReturnType().getName();

			RouteInfo route = new RouteInfo(httpMethod, fullPath, clazz.getName(), method.getName(), returnType);

			// Collect parameter info
			collectParameters(method, route);

			bean.routes.add(route);
		}
	}

	private String resolveHttpMethod(Method method) {
		if (method.isAnnotationPresent(summer.web.annotation.Get.class)) {
			return "GET";
		}
		if (method.isAnnotationPresent(summer.web.annotation.Post.class)) {
			return "POST";
		}
		if (method.isAnnotationPresent(summer.web.annotation.Put.class)) {
			return "PUT";
		}
		if (method.isAnnotationPresent(summer.web.annotation.Delete.class)) {
			return "DELETE";
		}
		return null;
	}

	private String extractMethodPath(Method method) {
		if (method.isAnnotationPresent(summer.web.annotation.Get.class)) {
			return method.getAnnotation(summer.web.annotation.Get.class).value();
		}
		if (method.isAnnotationPresent(summer.web.annotation.Post.class)) {
			return method.getAnnotation(summer.web.annotation.Post.class).value();
		}
		if (method.isAnnotationPresent(summer.web.annotation.Put.class)) {
			return method.getAnnotation(summer.web.annotation.Put.class).value();
		}
		if (method.isAnnotationPresent(summer.web.annotation.Delete.class)) {
			return method.getAnnotation(summer.web.annotation.Delete.class).value();
		}
		return "";
	}

	private void collectParameters(Method method, RouteInfo route) {
		for (int i = 0; i < method.getParameterCount(); i++) {
			Class<?> paramType = method.getParameterTypes()[i];
			String paramName = method.getParameters()[i].getName();

			// Skip HttpContext
			if (paramType.getName().equals("summer.web.HttpContext")) {
				continue;
			}

			// Check parameter annotations
			if (method.getParameters()[i].isAnnotationPresent(summer.web.annotation.PathParam.class)) {
				summer.web.annotation.PathParam pathParam = method.getParameters()[i]
						.getAnnotation(summer.web.annotation.PathParam.class);
				String bindingName = pathParam.value().isEmpty() ? paramName : pathParam.value();
				boolean validated = method.getParameters()[i].isAnnotationPresent(jakarta.validation.Valid.class);
				route.params.add(new RouteInfo.ParamInfo(bindingName, paramType.getName(), RouteInfo.ParamBinding.PATH,
						validated));
			} else if (method.getParameters()[i].isAnnotationPresent(summer.web.annotation.QueryParam.class)) {
				summer.web.annotation.QueryParam queryParam = method.getParameters()[i]
						.getAnnotation(summer.web.annotation.QueryParam.class);
				String bindingName = queryParam.value().isEmpty() ? paramName : queryParam.value();
				boolean validated = method.getParameters()[i].isAnnotationPresent(jakarta.validation.Valid.class);
				route.params.add(new RouteInfo.ParamInfo(bindingName, paramType.getName(), RouteInfo.ParamBinding.QUERY,
						validated));
			} else if (summer.web.ScrollRequest.class.isAssignableFrom(paramType)) {
				route.params.add(new RouteInfo.ParamInfo(paramName, paramType.getName(),
						RouteInfo.ParamBinding.PAGEABLE, false));
			} else {
				boolean validated = method.getParameters()[i].isAnnotationPresent(jakarta.validation.Valid.class);
				route.params.add(new RouteInfo.ParamInfo(paramName, paramType.getName(), RouteInfo.ParamBinding.BODY,
						validated));
			}
		}
	}

	private String combinePaths(String base, String method) {
		if (base.isEmpty()) {
			return method;
		}
		if (method.isEmpty()) {
			return base;
		}
		String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		String normalizedMethod = method.startsWith("/") ? method : "/" + method;
		return normalizedBase + normalizedMethod;
	}

	private void extractDefaultValues(Class<?> clazz, ConfigPropertiesBean bean) {
		ClassInfo ci = index.getClassByName(DotName.createSimple(clazz.getName()));
		if (ci == null || !ci.isRecord()) {
			return;
		}

		for (RecordComponentInfo comp : ci.recordComponents()) {
			AnnotationInstance defaultAnn = comp.annotation(DotName.createSimple("summer.core.config.DefaultValue"));
			if (defaultAnn != null) {
				bean.defaultValues.put(comp.name(), defaultAnn.value().asString());
				bean.fieldTypes.put(comp.name(), comp.type().name().toString());
			}
		}
	}
}
