package summer.runtime;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.BeanContainer;
import summer.web.Handler;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.PathUtils;
import summer.web.RouteRegistrar;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

public class RuntimeRouteRegistrar implements RouteRegistrar {

	private static final Logger log = LoggerFactory.getLogger(RuntimeRouteRegistrar.class);

	private static final DotName REST_CONTROLLER = DotName.createSimple(RestController.class);
	private static final Map<DotName, HttpMethod> HTTP_METHODS = Map.of(DotName.createSimple(Get.class), HttpMethod.GET,
			DotName.createSimple(Post.class), HttpMethod.POST, DotName.createSimple(Put.class), HttpMethod.PUT,
			DotName.createSimple(Delete.class), HttpMethod.DELETE);

	private final HttpParameterResolverChain resolverChain;
	private final IndexView index;
	private final Map<Class<?>, Map<String, Method>> methodCache = new HashMap<>();

	public RuntimeRouteRegistrar(HttpParameterResolverChain resolverChain, IndexView index) {
		this.resolverChain = resolverChain;
		this.index = index;
	}

	@Override
	public void registerControllers(HttpRouter.Builder builder, BeanContainer context) {
		// Jandex path: discover controllers from the index
		if (index != null) {
			for (var entry : HTTP_METHODS.entrySet()) {
				Collection<AnnotationInstance> annotations = index.getAnnotations(entry.getKey());
				for (AnnotationInstance ann : annotations) {
					MethodInfo method = ann.target().asMethod();
					ClassInfo controllerClass = method.declaringClass();
					if (!controllerClass.hasAnnotation(REST_CONTROLLER))
						continue;
					register(builder, context, ann, method, controllerClass, entry.getValue());
				}
			}
		}

		// Fallback: scan registered component types for controllers not in the index
		// (e.g. inner-class test controllers)
		for (Class<?> clazz : context.componentTypes()) {
			if (!clazz.isAnnotationPresent(RestController.class))
				continue;
			Object controller = context.getBean(clazz);
			if (controller == null)
				continue;
			for (Method method : clazz.getMethods()) {
				registerFromReflection(builder, controller, clazz, method);
			}
		}
	}

	private void register(HttpRouter.Builder builder, BeanContainer context, AnnotationInstance ann, MethodInfo method,
			ClassInfo controllerClass, HttpMethod httpMethod) {
		String controllerPath = annotationValue(controllerClass.annotation(REST_CONTROLLER));
		String fullPath = PathUtils.combinePaths(controllerPath, annotationValue(ann));
		try {
			Class<?> clazz = Class.forName(controllerClass.name().toString());
			Object controller = context.getBean(clazz);
			Method resolved = resolveMethod(clazz, method);
			Handler handler = HandlerFactory.create(controller, resolved, resolverChain);
			switch (httpMethod) {
				case GET -> builder.get(fullPath, handler);
				case POST -> builder.post(fullPath, handler);
				case PUT -> builder.put(fullPath, handler);
				case DELETE -> builder.delete(fullPath, handler);
			}
		} catch (Exception e) {
			log.warn("[Summer] Failed to register route {} {}: {}", httpMethod, fullPath, e.getMessage());
		}
	}

	private void registerFromReflection(HttpRouter.Builder builder, Object controller, Class<?> clazz, Method method) {
		var restController = clazz.getAnnotation(RestController.class);
		for (var entry : Map.of(Get.class, HttpMethod.GET, Post.class, HttpMethod.POST, Put.class, HttpMethod.PUT,
				Delete.class, HttpMethod.DELETE).entrySet()) {
			java.lang.annotation.Annotation ann = method.getAnnotation(entry.getKey());
			if (ann == null)
				continue;
			String path = PathUtils.combinePaths(restController.value(), annotationValueReflection(ann));
			Handler handler = HandlerFactory.create(controller, method, resolverChain);
			switch (entry.getValue()) {
				case GET -> builder.get(path, handler);
				case POST -> builder.post(path, handler);
				case PUT -> builder.put(path, handler);
				case DELETE -> builder.delete(path, handler);
			}
			return;
		}
	}

	private Method resolveMethod(Class<?> clazz, MethodInfo methodInfo) {
		return methodCache.computeIfAbsent(clazz, c -> {
			Map<String, Method> map = new HashMap<>();
			for (Method m : c.getMethods()) {
				map.putIfAbsent(m.getName() + "|" + m.getParameterCount(), m);
			}
			return map;
		}).get(methodInfo.name() + "|" + methodInfo.parameters().size());
	}

	private static String annotationValue(AnnotationInstance ann) {
		AnnotationValue value = ann.value();
		return value != null ? value.asString() : "";
	}

	private static String annotationValueReflection(java.lang.annotation.Annotation ann) {
		try {
			Method valueMethod = ann.annotationType().getMethod("value");
			return (String) valueMethod.invoke(ann);
		} catch (Exception e) {
			return "";
		}
	}
}